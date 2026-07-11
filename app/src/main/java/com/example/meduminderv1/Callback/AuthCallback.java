package com.example.meduminderv1.Callback;

public interface AuthCallback<T> {
    void onSuccess(T result);
    void onFailure(String message);
}
