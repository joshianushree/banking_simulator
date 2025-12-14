package com.bankingsim.model;

import java.time.LocalDateTime;

/**
 * Represents a user in the system (Admin or Customer).
 * Tracks login/logout, lock status, and activity.
 */
public class User {

    private int userId;
    private String username;
    private String password;
    private String email;
    private String phone;
    private String role; // ADMIN or CUSTOMER
    private String accountNumber;
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;
    private String status; // ACTIVE / INACTIVE
    private LocalDateTime createdAt;

    // 🔹 NEW FIELDS (match DB schema)
    private Integer failedAttempts;
    private Boolean isLocked;
    private LocalDateTime lockTime;
    private int deletionReq;   // 0 or 1
    private int isDeleted;     // 0 or 1


    // Constructors
    public User() {}

    public User(String username, String password, String email, String phone, String role, String accountNumber) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.accountNumber = accountNumber;
    }

    // Getters & Setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public LocalDateTime getLoginTime() { return loginTime; }
    public void setLoginTime(LocalDateTime loginTime) { this.loginTime = loginTime; }

    public LocalDateTime getLogoutTime() { return logoutTime; }
    public void setLogoutTime(LocalDateTime logoutTime) { this.logoutTime = logoutTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Integer failedAttempts) { this.failedAttempts = failedAttempts; }

    public Boolean getLocked() { return isLocked; }
    public void setLocked(Boolean locked) { isLocked = locked; }

    public LocalDateTime getLockTime() { return lockTime; }
    public void setLockTime(LocalDateTime lockTime) { this.lockTime = lockTime; }

    public int getDeletionReq() {
        return deletionReq;
    }

    public void setDeletionReq(int deletionReq) {
        this.deletionReq = deletionReq;
    }

    public int getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(int isDeleted) {
        this.isDeleted = isDeleted;
    }

}
