package com.example.meduminderv1.Splash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;

public class SplashActivity extends AppCompatActivity {

    private boolean revealStarted = false;
    boolean isSkip = false;
    AnimatorSet currentAnimator;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //menerapkan last mode user
        SharedPreferences prefs = getSharedPreferences("themes", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_mode", false);
        int targetMode = isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != targetMode){
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }

        authManager = AuthManager.getInstance(getApplicationContext());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View rootView = findViewById(android.R.id.content);
        rootView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN){
                skipSplash();
                return true;
            }
            return false;
        } );

        ImageView pill = findViewById(R.id.medicine_icon);
        ImageView letterU = findViewById(R.id.letter_u);

        TextView med = findViewById(R.id.med);
        TextView minder = findViewById(R.id.minder);

        View circle = findViewById(R.id.circle);
        View overlay = findViewById(R.id.overlay);

        letterU.post(() -> {

            float targetX = letterU.getX() + (letterU.getWidth() / 2f) - (pill.getWidth() / 2f);

            float targetY = letterU.getY() + (letterU.getHeight() / 2f) - (pill.getHeight() / 2f) - 10f;

            pill.setX(-pill.getWidth());
            pill.setY(200);

            med.setTranslationX(0);
            minder.setTranslationX(0);

            Path path = new Path();

            path.moveTo(-pill.getWidth(), 150);

            path.quadTo(120, 250, 220, 420);

            path.quadTo(340, 580, targetX, targetY);

            ObjectAnimator movePill = ObjectAnimator.ofFloat(pill, View.X, View.Y, path);

            movePill.setDuration(1600);
            movePill.setInterpolator(
                    new LinearInterpolator()
            );

            ObjectAnimator rotatePill =
                    ObjectAnimator.ofFloat(
                            pill,
                            "rotation",
                            0f,
                            810f
                    );

            rotatePill.setDuration(1600);

            AnimatorSet pillAnim = new AnimatorSet();

            pillAnim.playTogether(movePill, rotatePill);

            ObjectAnimator scaleX =
                    ObjectAnimator.ofFloat(
                            pill,
                            "scaleX",
                            1f,
                            1.15f,
                            1f
                    );

            ObjectAnimator scaleY =
                    ObjectAnimator.ofFloat(
                            pill,
                            "scaleY",
                            1f,
                            1.5f,
                            1f
                    );

            AnimatorSet bounce = new AnimatorSet();

            bounce.playTogether(scaleX, scaleY);

            bounce.setDuration(180);

            // menampilkan U

            ObjectAnimator fadeU =
                    ObjectAnimator.ofFloat(
                            letterU,
                            "alpha",
                            0f,
                            1f
                    );

            ObjectAnimator scaleUX =
                    ObjectAnimator.ofFloat(
                            letterU,
                            "scaleX",
                            0f,
                            1f
                    );

            ObjectAnimator scaleUY =
                    ObjectAnimator.ofFloat(
                            letterU,
                            "scaleY",
                            0f,
                            1f
                    );

            AnimatorSet uAnim = new AnimatorSet();

            uAnim.playTogether(fadeU, scaleUX, scaleUY);

            uAnim.setDuration(250);

            // MED keluar dari U

            ObjectAnimator medFade =
                    ObjectAnimator.ofFloat(
                            med,
                            "alpha",
                            0f,
                            1f
                    );

            ObjectAnimator medSlide =
                    ObjectAnimator.ofFloat(
                            med,
                            "translationX",
                            0f,
                            -20f
                    );

            AnimatorSet medAnim = new AnimatorSet();

            medAnim.playTogether(medFade, medSlide);

            medAnim.setDuration(350);

            // MINDER keluar dari U

            ObjectAnimator minderFade =
                    ObjectAnimator.ofFloat(
                            minder,
                            "alpha",
                            0f,
                            1f
                    );

            ObjectAnimator minderSlide =
                    ObjectAnimator.ofFloat(
                            minder,
                            "translationX",
                            0f,
                            285f
                    );

            AnimatorSet minderAnim = new AnimatorSet();

            minderAnim.playTogether(minderFade, minderSlide);

            minderAnim.setDuration(350);

            //pause sedetik di logo
            ValueAnimator pause = ValueAnimator.ofInt(0, 1);

            pause.setDuration(1000);

            // Circle muncul

            circle.setScaleX(0f);
            circle.setScaleY(0f);
            circle.setAlpha(0f);

            ObjectAnimator circleX =
                    ObjectAnimator.ofFloat(
                            circle,
                            "scaleX",
                            0f,
                            1f
                    );

            ObjectAnimator circleY =
                    ObjectAnimator.ofFloat(
                            circle,
                            "scaleY",
                            0f,
                            1f
                    );

            ObjectAnimator fade =
                    ObjectAnimator.ofFloat(
                            circle,
                            "alpha",
                            0f,
                            1f
                    );

            AnimatorSet circleAnim = new AnimatorSet();

            circleAnim.playTogether(
                    circleX,
                    circleY,
                    fade
            );

            circleAnim.setDuration(500);

            currentAnimator = new AnimatorSet();
            AnimatorSet finalSet = currentAnimator;

            finalSet.playSequentially(
                    pillAnim,
                    bounce,
                    uAnim,
                    medAnim,
                    minderAnim,
                    pause,
                    circleAnim
            );

            finalSet.start();

            finalSet.addListener(new AnimatorListenerAdapter() {
                boolean cancelled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (cancelled) return;
                    if (isSkip) return;
                    if (isFinishing() || isDestroyed()) return;
                    triggerReveal(circle, overlay);
                }
            });
        });
    }

    private void skipSplash() {
        if (isSkip) return;
        isSkip = true;

        //stop animasi yg lg jalan
        if (currentAnimator != null){
            currentAnimator.cancel();
        }

        //lgsng ke login
        openNextScreen();
    }

    private void triggerReveal(View circle, View overlay) {

        if(revealStarted || isSkip) return;
        if(isFinishing() || isDestroyed()) return;
        if (circle == null || overlay == null) return;
        if(!circle.isAttachedToWindow()) return;
        if(!overlay.isAttachedToWindow()) return;

        revealStarted = true;

        int cx = (int)(circle.getX() + circle.getWidth()/2f);
        int cy = (int)(circle.getY() + circle.getHeight()/2f);

        float radius = (float)Math.hypot(overlay.getWidth(), overlay.getHeight());

        overlay.setVisibility(View.VISIBLE);

        Animator reveal = ViewAnimationUtils.createCircularReveal(overlay, cx, cy, 0f, radius);

        reveal.setDuration(500);

        reveal.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (isSkip) return;
                        if (isFinishing() || isDestroyed()) return;
                        openNextScreen();
                    }
        });
        reveal.start();
    }

    private void openNextScreen() {
        authManager.restoreSession(new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onFailure(String message) {
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (currentAnimator != null){
            currentAnimator.cancel();
            currentAnimator = null;
        }
        super.onDestroy();
    }

}