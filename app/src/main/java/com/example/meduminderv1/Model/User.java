package com.example.meduminderv1.Model;

import com.google.firebase.Timestamp;

public class User {
    private String auth_uid;
    private String name;
    private String email;
    private String current_role;

    private AuthProviderType authProvider;
    private boolean caregiver_enabled;
    private String preferred_language;
    private String timezone;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;
    public String getAuth_uid() {
        return auth_uid;
    }
    public void setAuth_uid(String auth_uid) {
        this.auth_uid = auth_uid;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    public String getCurrent_role() {
        return current_role;
    }

    public void setCurrent_role(String current_role) {
        this.current_role = current_role;
    }

    public AuthProviderType getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProviderType authProvider) {
        this.authProvider = authProvider;
    }

    public boolean isCaregiver_enabled() {
        return caregiver_enabled;
    }

    public void setCaregiver_enabled(boolean caregiver_enabled) {
        this.caregiver_enabled = caregiver_enabled;
    }

    public String getPreferred_language() {
        return preferred_language;
    }

    public void setPreferred_language(String preferred_language) {
        this.preferred_language = preferred_language;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Timestamp getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Timestamp deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setGoogleLinked(boolean linked) {
    }
}
