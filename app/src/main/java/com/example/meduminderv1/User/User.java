package com.example.meduminderv1.User;

public class User {
    private String auth_uid;
    private String name;
    private String email;
    private String phone;
    private String role;
    private boolean caregiver_enabled;
    private String preferred_language;
    private String timezone;

    public User(){
    }

    public User(String auth_uid, String name, String email, String phone, String role, boolean caregiver_enabled, String preferred_language, String timezone){
        this.auth_uid = auth_uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.caregiver_enabled = caregiver_enabled;
        this.preferred_language = preferred_language;
        this.timezone = timezone;
    }

    public String getAuth_uid(){
        return auth_uid;
    }

    public String getName(){
        return name;
    }

    public String getEmail(){
        return email;
    }

    public String getPhone(){
        return phone;
    }

    public String getRole(){
        return role;
    }

    public boolean getCaregiver_enabled(){
        return caregiver_enabled;
    }

    public String getPreferred_language(){
        return preferred_language;
    }

    public String getTimezone(){
        return timezone;
    }
}
