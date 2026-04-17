package com.example.attendease.model;

public enum SessionState {
    UPCOMING,   // device time < startTime
    ACTIVE,     // startTime <= device time <= endTime
    CLOSED      // device time > endTime
}
