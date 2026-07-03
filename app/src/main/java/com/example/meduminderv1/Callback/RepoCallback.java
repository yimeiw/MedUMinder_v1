package com.example.meduminderv1.Callback;

public interface RepoCallback<T> {
    void onSuccess(T result);
    void onFailure(Exception e);
}
