package com.example.attendease.model;

import com.google.firebase.Timestamp;
import java.util.Map;

public class AttendanceSession {
    private String sessionId;
    private String classId;
    private String subject;
    private Timestamp date;
    private String markedBy; // faculty UID
    private Map<String, Boolean> records; // studentUid -> isPresent

    public AttendanceSession() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public Timestamp getDate() { return date; }
    public void setDate(Timestamp date) { this.date = date; }

    public String getMarkedBy() { return markedBy; }
    public void setMarkedBy(String markedBy) { this.markedBy = markedBy; }

    public Map<String, Boolean> getRecords() { return records; }
    public void setRecords(Map<String, Boolean> records) { this.records = records; }
}
