package com.example.meduminderv1.Repo;

import android.util.Log;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    FirebaseFirestore db;
    static UserRepository instance;

    private UserRepository(){
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized UserRepository getInstance(){
        if (instance == null){
            instance = new UserRepository();
        } return instance;
    }

    public void saveUser(User user, RepoCallback<Void> callback){
        db.collection("users").document(user.getAuth_uid()).set(user)
                .addOnSuccessListener(unused -> {
                    callback.onSuccess(null);
                }).addOnFailureListener(callback::onFailure);
    }

    public void getUserbyUid(String uid, RepoCallback<User> callback){
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()){
                        callback.onSuccess(snapshot.toObject(User.class));
                    } else {
                        callback.onFailure(new Exception("User tidak ditemukan."));
                    }
                }).addOnFailureListener(callback::onFailure);
    }

    public void getUserbyEmail(String email, RepoCallback<User> callback){
        Log.d("USER_REPO", "Email = " + email);
        db.collection("users").whereEqualTo("email", email).limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d("USER_REPO", "Result size = " + queryDocumentSnapshots.size());
                    if (queryDocumentSnapshots.isEmpty()){
                        callback.onFailure(new Exception("Email tidak ditemukan"));
                        return;
                     } User user = queryDocumentSnapshots.getDocuments().get(0).toObject(User.class);
                    callback.onSuccess(user);
                }).addOnFailureListener(callback::onFailure);
    }
    public void updateUser(User user, RepoCallback<Void> callback){
        Map<String, Object> update = new HashMap<>();
        update.put("name", user.getName());
        update.put("email", user.getEmail());
        update.put("updatedAt", user.getUpdatedAt());

        db.collection("users").document(user.getAuth_uid()).update(update)
                .addOnSuccessListener(unused -> {
                    callback.onSuccess(null);
                }).addOnFailureListener(callback::onFailure);
    }

    public void deleteUser(String uid, RepoCallback<Void> callback){
        db.collection("users").document(uid).delete()
                .addOnSuccessListener(unused -> {
                    callback.onSuccess(null);
                }).addOnFailureListener(callback::onFailure);
    }
}
