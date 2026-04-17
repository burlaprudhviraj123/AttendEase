package com.example.attendease.viewmodel;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.attendease.model.AttendanceSession;
import com.example.attendease.model.ClassModel;
import com.example.attendease.model.SessionState;
import com.example.attendease.model.TimetableEntry;
import com.example.attendease.model.TimetableSession;
import com.example.attendease.repository.FacultyRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FacultyViewModel extends ViewModel {

    private final FacultyRepository facultyRepository = new FacultyRepository();

    // Today's sessions enriched with class info + state
    private final MutableLiveData<List<TimetableSession>> todaysSessions = new MutableLiveData<>();
    // Summary stats
    private final MutableLiveData<Integer> totalClasses = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> sessionsThisMonth = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> avgAttendance = new MutableLiveData<>(0);
    // Submit state
    private final MutableLiveData<Boolean> submitSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    // Countdown (seconds remaining until active session's start)
    private final MutableLiveData<Long> countdownSeconds = new MutableLiveData<>(0L);

    private CountDownTimer countDownTimer;

    // -----------------------------------------------------------------------
    // Load today's timetable for a faculty member
    // -----------------------------------------------------------------------
    public void loadTodaysSessions(String facultyId) {
        String today = getTodayDayName();
        isLoading.setValue(true);
        facultyRepository.getTimetableForFaculty(facultyId, today).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                List<TimetableEntry> entries = task.getResult().toObjects(TimetableEntry.class);
                resolveClassesForEntries(facultyId, entries);
            } else {
                isLoading.setValue(false);
            }
        });
    }

    private void resolveClassesForEntries(String facultyId, List<TimetableEntry> entries) {
        if (entries.isEmpty()) {
            todaysSessions.setValue(new ArrayList<>());
            loadSummaryStats(facultyId);
            isLoading.setValue(false);
            return;
        }

        List<TimetableSession> result = new ArrayList<>();
        final int[] pending = {entries.size()};

        for (TimetableEntry entry : entries) {
            facultyRepository.getClassById(entry.getClassId()).addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    ClassModel cls = task.getResult().toObject(ClassModel.class);
                    if (cls != null) {
                        SessionState state = computeState(entry.getStartTime(), entry.getEndTime());
                        long secs = (state == SessionState.UPCOMING) ? secondsUntilTime(entry.getStartTime()) : 0;
                        result.add(new TimetableSession(entry, cls.getName(),
                                cls.getStudentIds() != null ? cls.getStudentIds().size() : 0,
                                cls.getStudentIds(), state, secs));
                    }
                }
                pending[0]--;
                if (pending[0] == 0) {
                    todaysSessions.setValue(result);
                    loadSummaryStats(facultyId);
                    isLoading.setValue(false);
                    startCountdownIfNeeded();
                }
            });
        }
    }

    // -----------------------------------------------------------------------
    // Load summary stats (sessions this month, avg attendance, class count)
    // -----------------------------------------------------------------------
    private void loadSummaryStats(String facultyId) {
        facultyRepository.getAllTimetableForFaculty(facultyId).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                java.util.Set<String> classIds = new java.util.HashSet<>();
                for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult().getDocuments()) {
                    String cid = doc.getString("classId");
                    if (cid != null) classIds.add(cid);
                }
                totalClasses.setValue(classIds.size());
            }
        });

        facultyRepository.getSessionsByFaculty(facultyId).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                List<AttendanceSession> sessions = task.getResult().toObjects(AttendanceSession.class);

                // Sessions this month
                Calendar now = Calendar.getInstance();
                int month = now.get(Calendar.MONTH);
                int year = now.get(Calendar.YEAR);
                int monthCount = 0;
                int totalPresent = 0;
                int totalRecords = 0;

                for (AttendanceSession s : sessions) {
                    if (s.getDate() != null) {
                        Calendar sc = Calendar.getInstance();
                        sc.setTimeInMillis(s.getDate().toDate().getTime());
                        if (sc.get(Calendar.MONTH) == month && sc.get(Calendar.YEAR) == year) {
                            monthCount++;
                        }
                    }
                    if (s.getRecords() != null) {
                        for (Boolean present : s.getRecords().values()) {
                            totalRecords++;
                            if (Boolean.TRUE.equals(present)) totalPresent++;
                        }
                    }
                }
                sessionsThisMonth.setValue(monthCount);
                avgAttendance.setValue(totalRecords > 0 ? (totalPresent * 100 / totalRecords) : 0);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Submit attendance
    // -----------------------------------------------------------------------
    public void submitAttendance(String classId, String subject, String facultyId,
                                 Map<String, Boolean> records) {
        isLoading.setValue(true);
        facultyRepository.writeAttendanceSession(classId, subject, facultyId, records)
                .addOnCompleteListener(task -> {
                    isLoading.setValue(false);
                    if (task.isSuccessful()) {
                        submitSuccess.setValue(true);
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage() : "Submission failed";
                        errorMessage.setValue(msg);
                    }
                });
    }

    public void resetSubmitState() {
        submitSuccess.setValue(null);
        errorMessage.setValue(null);
    }

    // -----------------------------------------------------------------------
    // Countdown timer for UPCOMING sessions
    // -----------------------------------------------------------------------
    private void startCountdownIfNeeded() {
        List<TimetableSession> sessions = todaysSessions.getValue();
        if (sessions == null) return;

        for (TimetableSession s : sessions) {
            if (s.getState() == SessionState.UPCOMING) {
                long secs = secondsUntilTime(s.getStartTime());
                if (secs > 0) {
                    scheduleCountdown(secs);
                    return;
                }
            }
        }
    }

    private void scheduleCountdown(long totalSeconds) {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(totalSeconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long remaining = millisUntilFinished / 1000;
                countdownSeconds.setValue(remaining);
                // refresh states when countdown nears zero
                if (remaining == 0) refreshSessionStates();
            }

            @Override
            public void onFinish() {
                countdownSeconds.setValue(0L);
                refreshSessionStates();
            }
        }.start();
    }

    private void refreshSessionStates() {
        List<TimetableSession> sessions = todaysSessions.getValue();
        if (sessions == null) return;
        for (TimetableSession s : sessions) {
            SessionState newState = computeState(s.getStartTime(), s.getEndTime());
            s.setState(newState);
            s.setSecondsUntilStart(newState == SessionState.UPCOMING ? secondsUntilTime(s.getStartTime()) : 0);
        }
        todaysSessions.setValue(sessions);
    }

    // -----------------------------------------------------------------------
    // Time helpers  (format "HH:mm" 24h)
    // -----------------------------------------------------------------------
    private String getTodayDayName() {
        return new SimpleDateFormat("EEEE", Locale.ENGLISH)
                .format(Calendar.getInstance().getTime()).toUpperCase();
    }

    static SessionState computeState(String startTime, String endTime) {
        try {
            int[] start = parseTime(startTime);
            int[] end = parseTime(endTime);
            Calendar now = Calendar.getInstance();
            int h = now.get(Calendar.HOUR_OF_DAY);
            int m = now.get(Calendar.MINUTE);

            int nowMin = h * 60 + m;
            int startMin = start[0] * 60 + start[1];
            int endMin = end[0] * 60 + end[1];

            if (nowMin < startMin) return SessionState.UPCOMING;
            if (nowMin <= endMin) return SessionState.ACTIVE;
            return SessionState.CLOSED;
        } catch (Exception e) {
            return SessionState.CLOSED;
        }
    }

    static long secondsUntilTime(String timeStr) {
        try {
            int[] t = parseTime(timeStr);
            Calendar now = Calendar.getInstance();
            Calendar target = Calendar.getInstance();
            target.set(Calendar.HOUR_OF_DAY, t[0]);
            target.set(Calendar.MINUTE, t[1]);
            target.set(Calendar.SECOND, 0);
            long diff = target.getTimeInMillis() - now.getTimeInMillis();
            return diff > 0 ? diff / 1000 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int[] parseTime(String timeStr) {
        // Supports "HH:mm" or "h:mm AM/PM"
        timeStr = timeStr.trim();
        if (timeStr.toUpperCase().contains("AM") || timeStr.toUpperCase().contains("PM")) {
            boolean pm = timeStr.toUpperCase().contains("PM");
            timeStr = timeStr.replaceAll("(?i)am|pm", "").trim();
            String[] parts = timeStr.split(":");
            int h = Integer.parseInt(parts[0].trim());
            int min = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            if (pm && h != 12) h += 12;
            if (!pm && h == 12) h = 0;
            return new int[]{h, min};
        }
        String[] parts = timeStr.split(":");
        return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
    }

    // -----------------------------------------------------------------------
    // LiveData getters
    // -----------------------------------------------------------------------
    public LiveData<List<TimetableSession>> getTodaysSessions() { return todaysSessions; }
    public LiveData<Integer> getTotalClasses() { return totalClasses; }
    public LiveData<Integer> getSessionsThisMonth() { return sessionsThisMonth; }
    public LiveData<Integer> getAvgAttendance() { return avgAttendance; }
    public LiveData<Boolean> getSubmitSuccess() { return submitSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Long> getCountdownSeconds() { return countdownSeconds; }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
