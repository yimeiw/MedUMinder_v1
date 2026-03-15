package com.example.meduminderv1.Splash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.R;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        AnimatorSet finalSet = new AnimatorSet();

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        RelativeLayout main = findViewById(R.id.main);
        ImageView icon = findViewById(R.id.medicine_icon);
        ImageView letterU = findViewById(R.id.letter_u);
        TextView med = findViewById(R.id.med);
        TextView minder = findViewById(R.id.minder);
        View circle = findViewById(R.id.circle);
        View overlay = findViewById(R.id.overlay);

        letterU.setAlpha(0f);
        letterU.setScaleX(0f);
        letterU.setScaleY(0f);

        med.setAlpha(0f);
        minder.setAlpha(0f);
        circle.setAlpha(0f);



        icon.post(() -> {
            float iconX = icon.getX();
            float iconY = icon.getY();
            letterU.setPivotX(letterU.getWidth() / 2f);
            letterU.setPivotY(letterU.getHeight() / 2f);
            letterU.setX(466f);
            letterU.setY(1064f);

            med.setPivotX(med.getWidth() / 2f);
            med.setPivotY(med.getHeight() / 2f);
            med.setY(1017f);

            minder.setPivotX(minder.getWidth() / 2f);
            minder.setPivotY(minder.getHeight() / 2f);
            minder.setY(1017f);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            float screenHeight = dm.heightPixels;
            float finalY = circle.getY();
            float halfStopY = screenHeight - (circle.getHeight() / 2f);

            circle.setAlpha(0f);
            circle.setY(screenHeight);


//            First Animation
            ObjectAnimator moveX1 = ObjectAnimator.ofFloat(icon, "x", -icon.getWidth(), 8f);
            AnimatorSet set1 = new AnimatorSet();
            set1.playTogether(moveX1);
            set1.setDuration(700);

//            Second Animation
            ObjectAnimator moveX2 = ObjectAnimator.ofFloat(icon, "x", 59f);
            ObjectAnimator moveY2 = ObjectAnimator.ofFloat(icon, "y", 263f);
            ObjectAnimator rotate2 = ObjectAnimator.ofFloat(icon, "rotation", 0f, -120f);
            AnimatorSet set2 = new AnimatorSet();
            set2.playTogether(moveX2, moveY2, rotate2);
            set2.setDuration(800);
            set2.setStartDelay(200);

//            Third Animation
            ObjectAnimator moveX3 = ObjectAnimator.ofFloat(icon, "x", 206f);
            ObjectAnimator moveY3 = ObjectAnimator.ofFloat(icon, "y", 395f);
            ObjectAnimator rotate3 = ObjectAnimator.ofFloat(icon, "rotation", -120f, 45f);
            AnimatorSet set3 = new AnimatorSet();
            set3.playTogether(moveX3, moveY3, rotate3);
            set3.setDuration(800);
            set3.setStartDelay(200);

//            Fourth Animation
            ObjectAnimator moveX4 = ObjectAnimator.ofFloat(icon, "x", 347f);
            ObjectAnimator moveY4 = ObjectAnimator.ofFloat(icon, "y", 534f);
            ObjectAnimator rotate4 = ObjectAnimator.ofFloat(icon, "rotation", 45f, -140f);
            AnimatorSet set4 = new AnimatorSet();
            set4.playTogether(moveX4, moveY4, rotate4);
            set4.setDuration(800);
            set4.setStartDelay(200);

//            Fifth Animation
            ObjectAnimator moveX5 = ObjectAnimator.ofFloat(icon, "x", 449f);
            ObjectAnimator moveY5 = ObjectAnimator.ofFloat(icon, "y", 772f);
            ObjectAnimator rotate5 = ObjectAnimator.ofFloat(icon, "rotation", -140f, 90f);
            AnimatorSet set5 = new AnimatorSet();
            set5.playTogether(moveX5, moveY5, rotate5);
            set5.setDuration(800);
            set5.setStartDelay(200);

//            Sixth Animation
            ObjectAnimator fadeInU = ObjectAnimator.ofFloat(letterU, "alpha", 0f, 1f);
            ObjectAnimator scaleX6 = ObjectAnimator.ofFloat(letterU, "scaleX", 0f, 1f);
            ObjectAnimator scaleY6 = ObjectAnimator.ofFloat(letterU, "scaleY", 0f, 1f);

            AnimatorSet set6 = new AnimatorSet();
            set6.playTogether(fadeInU, scaleX6, scaleY6);
            set6.setDuration(800);
            set6.setStartDelay(200);

//            Seventh Animation
            ObjectAnimator fadeInMed = ObjectAnimator.ofFloat(med, "alpha", 0f, 1f);
            ObjectAnimator slideToLeft = ObjectAnimator.ofFloat(med, "translationX", 0f, -15f);
            AnimatorSet set7 = new AnimatorSet();
            set7.playTogether(fadeInMed, slideToLeft);
            set7.setDuration(800);
            set7.setStartDelay(200);

//            Eigth Animation
            ObjectAnimator fadeInMinder = ObjectAnimator.ofFloat(minder, "alpha", 0f, 1f);
            ObjectAnimator slideToRight = ObjectAnimator.ofFloat(minder, "translationX", 0f, 15f);
            AnimatorSet set8 = new AnimatorSet();
            set8.playTogether(fadeInMinder, slideToRight);
            set8.setDuration(800);
            set8.setStartDelay(200);

//          Nineth Animation
            ObjectAnimator fadeInCircle = ObjectAnimator.ofFloat(circle, "alpha", 0f, 1f);
            ObjectAnimator slideToHalf = ObjectAnimator.ofFloat(circle, "y", screenHeight, halfStopY);
            AnimatorSet set9 = new AnimatorSet();
            set9.playTogether(fadeInCircle, slideToHalf);
            set9.setDuration(800);
            set9.setInterpolator(new DecelerateInterpolator());

//            Tenth Animation
            ObjectAnimator slideToFinal = ObjectAnimator.ofFloat(circle, "y", halfStopY, finalY);
            AnimatorSet set10 = new AnimatorSet();
            set10.play(slideToFinal);
            set10.setDuration(800);
            set10.setStartDelay(600);
            set10.setInterpolator(new AccelerateDecelerateInterpolator());

//            Final Set
            finalSet.playSequentially(set1, set2, set3, set4, set5, set6, set7, set8, set9, set10);
            finalSet.start();

            finalSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    triggerReveal(circle, overlay);
                }
            });
        });
        main.setOnClickListener(v -> {
            finalSet.cancel();
            triggerReveal(circle, overlay);
        });
    }

//    Trigger From User to Speed Up Animation
    private void triggerReveal(View circle, View overlay) {
        int cx = (int) (circle.getX() + circle.getWidth() / 2f);
        int cy = (int) (circle.getY() + circle.getHeight() / 2f);
        float finalRadius = (float) Math.hypot(overlay.getWidth(), overlay.getHeight());

        Animator reveal = ViewAnimationUtils.createCircularReveal(
                overlay, cx, cy, 0f, finalRadius
        );
        reveal.setDuration(300);
        reveal.setInterpolator(new AccelerateInterpolator());

        overlay.setVisibility(View.VISIBLE);
        reveal.start();

        reveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        });
    }

}