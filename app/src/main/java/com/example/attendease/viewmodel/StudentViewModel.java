package com.example.attendease.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.attendease.model.AttendanceSession;
import com.example.attendease.model.ClassModel;
import com.example.attendease.repository.StudentRepository;
import com.example.attendease.ui.student.CourseAttendanceAdapter;
import com.example.attendease.R;

import java.util.ArrayList;
import java.util.List;

public class StudentViewModel extends ViewModel {
    private final StudentRepository studentRepository;

    private final MutableLiveData<List<CourseAttendanceAdapter.CourseAttendance>> courseAttendanceList = new MutableLiveData<>();
    private final MutableLiveData<Integer> overallAttendance = new MutableLiveData<>(0);
    private final MutableLiveData<String> attendanceDetails = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // Alert banner: subjects below 75% with "attend X more" text
    private final MutableLiveData<List<AlertItem>> alertSubjects = new MutableLiveData<>();
    // Full session list for History tab / subject drill-down
    private final MutableLiveData<List<AttendanceSession>> allSessions = new MutableLiveData<>();
    // Next Class info
    private final MutableLiveData<NextClassInfo> nextClass = new MutableLiveData<>();

    public static class NextClassInfo {
        public final String subject;
        public final String timeRange;
        public final String location;
        public final String timeRemaining;
        public NextClassInfo(String s, String tr, String loc, String rem) {
            subject = s; timeRange = tr; location = loc; timeRemaining = rem;
        }
    }

    /** Simple model for the student alert banner */
    public static class AlertItem {
        public final String subject;
        public final String faculty;
        public final int attended;
        public final int total;
        public final int percent;
        public final int classesNeeded;

        public AlertItem(String subject, String faculty, int attended, int total, int percent, int classesNeeded) {
            this.subject = subject;
            this.faculty = faculty;
            this.attended = attended;
            this.total = total;
            this.percent = percent;
            this.classesNeeded = classesNeeded;
        }
    }

    public StudentViewModel() {
        this.studentRepository = new StudentRepository();
    }

    public void loadStudentData(String studentUid) {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.setValue(true);
        // First, get the full user profile to resolve rollNo
        studentRepository.getUserProfile(studentUid).addOnCompleteListener(profileTask -> {
            String lookupId = studentUid; // fallback to uid
            if (profileTask.isSuccessful() && profileTask.getResult() != null
                    && profileTask.getResult().exists()) {
                String rollNo = profileTask.getResult().getString("rollNo");
                if (rollNo != null && !rollNo.isEmpty()) {
                    lookupId = rollNo;
                }
            }
            final String finalLookupId = lookupId;
            studentRepository.getStudentClasses(finalLookupId).addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    List<ClassModel> classes = task.getResult().toObjects(ClassModel.class);
                    fetchAttendanceForClasses(finalLookupId, classes);
                } else {
                    isLoading.setValue(false);
                }
            });
        });
    }

    private void fetchAttendanceForClasses(String lookupId, List<ClassModel> classes) {
        List<CourseAttendanceAdapter.CourseAttendance> resultList = new ArrayList<>();
        List<AttendanceSession> sessionBucket = new ArrayList<>();

        if (classes.isEmpty()) {
            courseAttendanceList.setValue(resultList);
            overallAttendance.setValue(0);
            alertSubjects.setValue(new ArrayList<>());
            allSessions.setValue(new ArrayList<>());
            isLoading.setValue(false);
            return;
        }

        final int[] pending = {classes.size()};

        for (ClassModel classModel : classes) {
            // Step 1: query timetable to get subjects for this class
            studentRepository.getSubjectsForClass(classModel.getClassId())
                    .addOnCompleteListener(ttTask -> {
                        java.util.Set<String> subjects = new java.util.LinkedHashSet<>();
                        final java.util.Map<String, String> subjectToFaculty = new java.util.HashMap<>();
                        
                        if (ttTask.isSuccessful() && ttTask.getResult() != null) {
                            java.util.Calendar cal = java.util.Calendar.getInstance();
                            String[] days = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
                            String todayStr = days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1];
                            int currentMins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
                            
                            NextClassInfo upcoming = null;
                            int closestMins = Integer.MAX_VALUE;

                            for (com.google.firebase.firestore.DocumentSnapshot doc
                                    : ttTask.getResult().getDocuments()) {
                                String subj = doc.getString("subject");
                                if (subj != null) {
                                    subjects.add(subj);
                                    String faculty = doc.getString("facultyName");
                                    if (faculty != null) subjectToFaculty.put(subj, faculty);
                                }

                                // Check next class
                                String d = doc.getString("day");
                                if (todayStr.equalsIgnoreCase(d)) {
                                    String start = doc.getString("startTime");
                                    String end = doc.getString("endTime");
                                    if (start != null && start.contains(":")) {
                                        try {
                                            String[] ps = start.split(":");
                                            int startMins = Integer.parseInt(ps[0].trim()) * 60 + Integer.parseInt(ps[1].trim());
                                            if (startMins >= currentMins && startMins < closestMins) {
                                                closestMins = startMins;
                                                int diff = startMins - currentMins;
                                                String remain = diff == 0 ? "NOW" : (diff < 60 ? "IN " + diff + " MINS" : "IN " + (diff/60) + " HRS");
                                                String loc = doc.getString("room") != null ? doc.getString("room") : "";
                                                String tr = start + " - " + end;
                                                upcoming = new NextClassInfo(subj, tr, loc, remain);
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                            if (upcoming != null) {
                                nextClass.postValue(upcoming);
                            } else {
                                nextClass.postValue(new NextClassInfo("No more classes today", "Enjoy the rest of your day!", "Off-Campus", "DONE"));
                            }
                        }

                        // Step 2: fetch attendance sessions for this class
                        studentRepository.getAttendanceSessions(classModel.getClassId())
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful() && task.getResult() != null) {
                                        List<AttendanceSession> sessions =
                                                task.getResult().toObjects(AttendanceSession.class);
                                        sessionBucket.addAll(sessions);

                                        for (String subject : subjects) {
                                            int subjectTotal = 0;
                                            int subjectAttended = 0;

                                            for (AttendanceSession s : sessions) {
                                                if (subject.equals(s.getSubject())) {
                                                    subjectTotal++;
                                                    if (s.getRecords() != null &&
                                                            Boolean.TRUE.equals(
                                                                    s.getRecords().get(lookupId))) {
                                                        subjectAttended++;
                                                    }
                                                }
                                            }

                                            int pct = subjectTotal > 0
                                                    ? (subjectAttended * 100 / subjectTotal) : 0;
                                            String facultyName = subjectToFaculty.getOrDefault(subject, "Department Faculty");
                                            resultList.add(new CourseAttendanceAdapter.CourseAttendance(
                                                    subject, facultyName, pct, R.drawable.ic_school, subjectAttended, subjectTotal));
                                        }
                                    }

                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        courseAttendanceList.setValue(resultList);
                                        calculateOverall(resultList);
                                        buildAlerts(resultList);
                                        sessionBucket.sort((a, b) -> {
                                            if (a.getDate() == null || b.getDate() == null) return 0;
                                            return b.getDate().compareTo(a.getDate());
                                        });
                                        allSessions.setValue(sessionBucket);
                                        isLoading.setValue(false);
                                    }
                                });
                    });
        }
    }

    private void calculateOverall(List<CourseAttendanceAdapter.CourseAttendance> courses) {
        if (courses.isEmpty()) {
            overallAttendance.setValue(0);
            attendanceDetails.setValue("No attendance records yet.");
            return;
        }
        int sum = 0;
        for (CourseAttendanceAdapter.CourseAttendance c : courses) sum += c.getPercentage();
        int avg = sum / courses.size();
        overallAttendance.setValue(avg);
        attendanceDetails.setValue("Average across " + courses.size() + " subject(s): " + avg + "%");
    }

    /**
     * Recovery formula from TODO:
     *   classesNeeded = ceil((0.75*(attended + n) − attended) / 0.25)
     *   Simplified: n = ceil((0.75*total − attended) / 0.25)
     */
    private void buildAlerts(List<CourseAttendanceAdapter.CourseAttendance> courses) {
        List<AlertItem> alerts = new ArrayList<>();
        for (CourseAttendanceAdapter.CourseAttendance c : courses) {
            if (c.getPercentage() < 75) {
                int needed = computeClassesNeeded(c.getAttendedCount(), c.getTotalHeldCount());
                alerts.add(new AlertItem(c.getName(), c.getFaculty(), c.getAttendedCount(), 
                    c.getTotalHeldCount(), c.getPercentage(), needed));
            }
        }
        alertSubjects.setValue(alerts);
    }

    public static int computeClassesNeeded(int attended, int total) {
        if (total > 0 && (double) attended / total >= 0.75) return 0;
        double n = (0.75 * total - attended) / 0.25;
        return (int) Math.ceil(n);
    }

    public int getRemainingClasses(String subject, String classId) {
        // Find how many times this subject appears per week in the timetable
        // For simplicity, we calculate weeks remaining until 16-04-2026
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar end = java.util.Calendar.getInstance();
        end.set(2026, 3, 16); // 16 April 2026

        if (now.after(end)) return 0;

        long diff = end.getTimeInMillis() - now.getTimeInMillis();
        int daysLeft = (int) (diff / (1000 * 60 * 60 * 24));
        int weeksLeft = (int) Math.ceil(daysLeft / 7.0);

        // Map of how many times each subject appears per week
        // We'll estimate this based on the typical 4 sessions/week for theory and 1 for lab
        if (subject.toLowerCase().contains("lab")) return weeksLeft;
        return weeksLeft * 3; // roughly 3-4 theory classes per week
    }

    // LiveData getters
    public LiveData<List<CourseAttendanceAdapter.CourseAttendance>> getCourseAttendanceList() { return courseAttendanceList; }
    public LiveData<Integer> getOverallAttendance() { return overallAttendance; }
    public LiveData<String> getAttendanceDetails() { return attendanceDetails; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<List<AlertItem>> getAlertSubjects() { return alertSubjects; }
    public LiveData<List<AttendanceSession>> getAllSessions() { return allSessions; }
    public LiveData<NextClassInfo> getNextClass() { return nextClass; }
}
