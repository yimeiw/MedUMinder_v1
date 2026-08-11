package com.example.meduminderv1.Callback;

public interface InvitationCallback {
    void onSuccess(boolean registered);
    void onFailure(String message);
}
