package com.example.meduminderv1.Auth;

import com.example.meduminderv1.Model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SessionManager {
    static SessionManager instance;
    FirebaseAuth auth;
    User currentUser;
    private SessionManager(){
        auth = FirebaseAuth.getInstance();
    }
    public static synchronized SessionManager getInstance(){
        if (instance == null){
            instance = new SessionManager();
        }
        return instance;
    }
    public boolean isLoggedIn(){
        return auth.getCurrentUser() != null;
    }
    public User getUser(){
        return currentUser;
    }

    public String getUid(){
        FirebaseUser user = auth.getCurrentUser();
        if (user == null){
            return null;
        }
        return user.getUid();
    }
    public void saveUser(User user){
        currentUser = user;
    }

    public void clearSession(){
        currentUser = null;
        auth.signOut();
    }

    public void updateName(String name){
        if (currentUser != null){
            currentUser.setName(name);
        }
    }
    public void updatePhone(String phone){
        if (currentUser != null){
            currentUser.setPhone(phone);
        }
    }
    public void updateGoogleLinked(boolean linked){
        if (currentUser != null){
            currentUser.setGoogleLinked(linked);
        }
    }
    public void updatePhoneVerified(boolean verified){
        if (currentUser != null){
            currentUser.setPhoneVerified(verified);
        }
    }
}
