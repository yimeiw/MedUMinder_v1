package com.example.meduminderv1.Auth;

import com.example.meduminderv1.Model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SessionManager {
    static SessionManager instance;
    FirebaseAuth auth;
    User currentUser;
    public SessionManager(){
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
}
