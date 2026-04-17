package com.example.attendease.model;

import java.util.List;

/**
 * UI-facing model that merges a TimetableEntry with its resolved ClassModel
 * and a computed SessionState (UPCOMING / ACTIVE / CLOSED).
 */
public class TimetableSession {
    private final TimetableEntry entry;
    private final String className;
    private final int studentCount;
    private final List<String> studentIds;
    private SessionState state;
    private long secondsUntilStart; // 0 when not UPCOMING

    public TimetableSession(TimetableEntry entry, String className,
                            int studentCount, List<String> studentIds,
                            SessionState state, long secondsUntilStart) {
        this.entry = entry;
        this.className = className;
        this.studentCount = studentCount;
        this.studentIds = studentIds;
        this.state = state;
        this.secondsUntilStart = secondsUntilStart;
    }

    public TimetableEntry getEntry() { return entry; }
    public String getClassName() { return className; }
    public int getStudentCount() { return studentCount; }
    public List<String> getStudentIds() { return studentIds; }
    public SessionState getState() { return state; }
    public void setState(SessionState state) { this.state = state; }
    public long getSecondsUntilStart() { return secondsUntilStart; }
    public void setSecondsUntilStart(long s) { this.secondsUntilStart = s; }

    /** Convenience getters forwarded from entry */
    public String getSubject() { return entry.getSubject(); }
    public String getStartTime() { return entry.getStartTime(); }
    public String getEndTime() { return entry.getEndTime(); }
    public String getClassId() { return entry.getClassId(); }
    public String getFacultyId() { return entry.getFacultyId(); }
}
