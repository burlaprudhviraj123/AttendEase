package com.example.attendease.repository;

import com.example.attendease.model.AttendanceSession;
import com.example.attendease.model.ClassModel;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

public class StudentRepository {
    private final FirebaseFirestore db;

    public StudentRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    /** Fetch the user's own Firestore document (to resolve rollNo etc.) */
    public Task<DocumentSnapshot> getUserProfile(String uid) {
        return db.collection("users").document(uid).get();
    }

    public Task<QuerySnapshot> getStudentClasses(String rollNo) {
        return db.collection("classes")
                .whereArrayContains("studentIds", rollNo)
                .get();
    }

    /** Query timetable to get all subject entries for this class (distinct subjects). */
    public Task<QuerySnapshot> getSubjectsForClass(String classId) {
        return db.collection("timetable")
                .whereEqualTo("classId", classId)
                .get();
    }

    public Task<QuerySnapshot> getAttendanceSessions(String classId) {
        return db.collection("attendance")
                .whereEqualTo("classId", classId)
                .get();
    }
}
