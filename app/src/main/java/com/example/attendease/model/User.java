package com.example.attendease.model;

import com.example.attendease.utils.Constants;

public class User {
    private String uid;
    private String name;
    private String email;
    private String role;
    private String department;
    private String batch; // Only for students
    private String rollNo;
    private String section;
    private boolean passwordSet;
    private String institutionalId; // Official ID (e.g., sanusha.cse or 22L31A0501)

    public User() {
        // Required for Firestore
    }

    public User(String uid, String name, String email, String role, String department) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
        this.department = department;
    }

    public User(String uid, String name, String email, String role, String department, String batch, String rollNo, String section) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
        this.department = department;
        this.batch = batch;
        this.rollNo = rollNo;
        this.section = section;
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getInstitutionalId() { return institutionalId; }
    public void setInstitutionalId(String institutionalId) { this.institutionalId = institutionalId; }

    public boolean isPasswordSet() { return passwordSet; }
    public void setPasswordSet(boolean passwordSet) { this.passwordSet = passwordSet; }
    
    public boolean isStudent() {
        return Constants.ROLE_STUDENT.equals(role);
    }
}
