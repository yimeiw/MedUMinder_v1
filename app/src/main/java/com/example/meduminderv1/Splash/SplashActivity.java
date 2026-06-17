package com.example.meduminderv1.Splash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Path;
import android.os.Bundle;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.R;

public class SplashActivity extends AppCompatActivity {

    private boolean revealStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

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

            AnimatorSet pillAnim =
                    new AnimatorSet();

            pillAnim.playTogether(
                    movePill,
                    rotatePill
            );

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

            AnimatorSet bounce =
                    new AnimatorSet();

            bounce.playTogether(
                    scaleX,
                    scaleY
            );

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

            AnimatorSet uAnim =
                    new AnimatorSet();

            uAnim.playTogether(
                    fadeU,
                    scaleUX,
                    scaleUY
            );

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
                            -150f
                    );

            AnimatorSet medAnim =
                    new AnimatorSet();

            medAnim.playTogether(
                    medFade,
                    medSlide
            );

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
                            210f
                    );

            AnimatorSet minderAnim =
                    new AnimatorSet();

            minderAnim.playTogether(
                    minderFade,
                    minderSlide
            );

            minderAnim.setDuration(350);

            //pause sedetik di logo
            ValueAnimator pause =
                    ValueAnimator.ofInt(0,1);

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

            AnimatorSet circleAnim =
                    new AnimatorSet();

            circleAnim.playTogether(
                    circleX,
                    circleY,
                    fade
            );

            circleAnim.setDuration(500);

            AnimatorSet finalSet =
                    new AnimatorSet();

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

            finalSet.addListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(
                                Animator animation) {

                            triggerReveal(
                                    circle,
                                    overlay
                            );
                        }
                    });
        });
    }

    private void triggerReveal(
            View circle,
            View overlay) {

        if(revealStarted) return;

        revealStarted = true;

        int cx =
                (int)(circle.getX()
                        + circle.getWidth()/2f);

        int cy =
                (int)(circle.getY()
                        + circle.getHeight()/2f);

        float radius =
                (float)Math.hypot(
                        overlay.getWidth(),
                        overlay.getHeight()
                );

        overlay.setVisibility(View.VISIBLE);

        Animator reveal =
                ViewAnimationUtils.createCircularReveal(
                        overlay,
                        cx,
                        cy,
                        0f,
                        radius
                );

        reveal.setDuration(500);

        reveal.start();

        reveal.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(
                            Animator animation) {

                        startActivity(
                                new Intent(
                                        SplashActivity.this,
                                        LoginActivity.class
                                )
                        );

                        finish();
                    }
                });
    }
}