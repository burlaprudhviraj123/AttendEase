package com.example.attendease.repository;

import com.example.attendease.model.AttendanceSession;
import com.example.attendease.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Map;

public class FacultyRepository {
    private final FirebaseFirestore db;

    public FacultyRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    /** Timetable entries for this faculty on the given day (e.g., "MONDAY") */
    public Task<QuerySnapshot> getTimetableForFaculty(String facultyId, String day) {
        return db.collection(Constants.COL_TIMETABLE)
                .whereEqualTo("facultyId", facultyId)
                .whereEqualTo("day", day)
                .get();
    }

    /** All timetable entries for this faculty (all days) */
    public Task<QuerySnapshot> getAllTimetableForFaculty(String facultyId) {
        return db.collection(Constants.COL_TIMETABLE)
                .whereEqualTo("facultyId", facultyId)
                .get();
    }

    /** Fetch a class document by its ID */
    public Task<DocumentSnapshot> getClassById(String classId) {
        return db.collection(Constants.COL_CLASSES).document(classId).get();
    }

    /** All classes where this faculty is the teacher */
    public Task<QuerySnapshot> getClassesForFaculty(String facultyId) {
        return db.collection(Constants.COL_CLASSES)
                .whereEqualTo("teacherId", facultyId)
                .get();
    }

    /** Attendance sessions recorded by this faculty */
    public Task<QuerySnapshot> getSessionsByFaculty(String facultyId) {
        return db.collection(Constants.COL_ATTENDANCE)
                .whereEqualTo("markedBy", facultyId)
                .get();
    }

    /** Attendance sessions for a specific class */
    public Task<QuerySnapshot> getSessionsForClass(String classId) {
        return db.collection(Constants.COL_ATTENDANCE)
                .whereEqualTo("classId", classId)
                .get();
    }

    /** Write a new attendance session document (auto-ID) */
    public Task<DocumentReference> writeAttendanceSession(String classId, String subject,
                                                          String facultyId, Map<String, Boolean> records) {
        AttendanceSession session = new AttendanceSession();
        session.setClassId(classId);
        session.setSubject(subject);
        session.setMarkedBy(facultyId);
        session.setDate(Timestamp.now());
        session.setRecords(records);

        return db.collection(Constants.COL_ATTENDANCE).add(session);
    }

    /** Update (correct) an existing session's records */
    public Task<Void> updateAttendanceRecords(String sessionId, Map<String, Boolean> records) {
        return db.collection(Constants.COL_ATTENDANCE).document(sessionId)
                .update("records", records);
    }
}
