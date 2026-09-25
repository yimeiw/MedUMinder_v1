package com.example.meduminderv1.Auth;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Repo.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
public class SessionManager {
    static SessionManager instance;
    FirebaseAuth auth;
    User currentUser;
    private String activeConsumerUid;
    private SessionManager(){
        auth = FirebaseAuth.getInstance();
    }
    public static synchronized SessionManager getInstance(){
        if (instance == null){
            instance = new SessionManager();
        }
        return instance;
    }
    public User getUser(){
        return currentUser;
    }
    public void saveUser(User user){
        currentUser = user;
    }
    public void clearSession(){
        currentUser = null;
        auth.signOut();
    }
    public void setActiveConsumerUid(String uid){
        this.activeConsumerUid = uid;
    }
    public String getActiveConsumerUid(){
        return activeConsumerUid;
    }
    public String getTargetUid(){
        if (currentUser == null) return null;
        if (currentUser.getCurrentRole() == UserRole.Caregiver){
            return activeConsumerUid; //bisa null kalau caregiver belum pilih consumer;
        } return currentUser.getAuth_uid();
    }
}
