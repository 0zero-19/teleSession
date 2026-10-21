package com.telegram.sessionmanager.account;

import org.json.JSONException;
import org.json.JSONObject;

public class AccountModel {
    private String id;
    private long userId;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String username;
    private boolean isAuthorized;
    private long lastActiveDate;
    private long createdAt;

    public AccountModel(String id) {
        this.id = id;
        this.userId = 0;
        this.phoneNumber = "";
        this.firstName = "";
        this.lastName = "";
        this.username = "";
        this.isAuthorized = false;
        this.lastActiveDate = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
    }

    public AccountModel(String id, long userId, String phoneNumber, String firstName,
                        String lastName, String username, boolean isAuthorized,
                        long lastActiveDate, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.phoneNumber = phoneNumber != null ? phoneNumber : "";
        this.firstName = firstName != null ? firstName : "";
        this.lastName = lastName != null ? lastName : "";
        this.username = username != null ? username : "";
        this.isAuthorized = isAuthorized;
        this.lastActiveDate = lastActiveDate;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber != null ? phoneNumber : "";
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName != null ? firstName : "";
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName != null ? lastName : "";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username : "";
    }

    public boolean isAuthorized() {
        return isAuthorized;
    }

    public void setAuthorized(boolean authorized) {
        isAuthorized = authorized;
    }

    public long getLastActiveDate() {
        return lastActiveDate;
    }

    public void setLastActiveDate(long lastActiveDate) {
        this.lastActiveDate = lastActiveDate;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isEmpty()) {
            sb.append(firstName);
        }
        if (lastName != null && !lastName.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(lastName);
        }
        if (sb.length() > 0) {
            return sb.toString();
        }
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        return "Account " + id;
    }

    public String getInitials() {
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isEmpty()) {
            sb.append(firstName.charAt(0));
        }
        if (lastName != null && !lastName.isEmpty()) {
            sb.append(lastName.charAt(0));
        }
        if (sb.length() == 0 && phoneNumber != null && phoneNumber.length() > 1) {
            sb.append(phoneNumber.charAt(phoneNumber.startsWith("+") ? 1 : 0));
        }
        if (sb.length() == 0) {
            sb.append("TG");
        }
        return sb.toString().toUpperCase();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("userId", userId);
        json.put("phoneNumber", phoneNumber);
        json.put("firstName", firstName);
        json.put("lastName", lastName);
        json.put("username", username);
        json.put("isAuthorized", isAuthorized);
        json.put("lastActiveDate", lastActiveDate);
        json.put("createdAt", createdAt);
        return json;
    }

    public static AccountModel fromJson(JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "acc_default");
        long userId = json.optLong("userId", 0);
        String phoneNumber = json.optString("phoneNumber", "");
        String firstName = json.optString("firstName", "");
        String lastName = json.optString("lastName", "");
        String username = json.optString("username", "");
        boolean isAuthorized = json.optBoolean("isAuthorized", false);
        long lastActiveDate = json.optLong("lastActiveDate", System.currentTimeMillis());
        long createdAt = json.optLong("createdAt", System.currentTimeMillis());

        return new AccountModel(id, userId, phoneNumber, firstName, lastName, username,
                isAuthorized, lastActiveDate, createdAt);
    }
}
