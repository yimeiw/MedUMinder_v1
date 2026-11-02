package com.example.meduminderv1.Util;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Spinner untuk satu bagian layar (mis. "Jadwal hari ini") saat data PERTAMA dimuat.
 * - Hanya tampil di muatan pertama; pembaruan live berikutnya tidak memunculkannya lagi (tidak berkedip).
 * - Selama memuat, isi & pesan kosong disembunyikan, jadi tidak pernah tampil bersamaan.
 * - Kalau terlalu lama, berhenti dan memanggil onTimeout (tidak memuat selamanya).
 */
public final class SectionLoader {

    private static final long TIMEOUT_MS = 15_000L;

    private final View spinner;
    private final View[] hideWhileLoading;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loading = false;
    private boolean loadedOnce = false;
    @Nullable private Runnable timeoutAction;

    public SectionLoader(View spinner, View... hideWhileLoading) {
        this.spinner = spinner;
        this.hideWhileLoading = hideWhileLoading;
    }

    /** Mulai memuat; tidak berbuat apa-apa kalau data sudah pernah tampil. */
    public void startFirstLoad(Runnable onTimeout) {
        if (loadedOnce || loading) return;
        loading = true;
        spinner.setVisibility(View.VISIBLE);
        for (View v : hideWhileLoading) if (v != null) v.setVisibility(View.GONE);
        timeoutAction = () -> {
            if (!loading) return;
            stop();
            onTimeout.run();
        };
        handler.postDelayed(timeoutAction, TIMEOUT_MS);
    }

    /** Data (atau kosong) sudah datang: sembunyikan spinner. Aman dipanggil berkali-kali. */
    public void done() {
        loadedOnce = true;
        stop();
    }

    /** Gagal memuat: sembunyikan spinner (pemanggil menampilkan pesan error). */
    public void failed() {
        stop();
    }

    public boolean isLoading() {
        return loading;
    }

    /** true kalau data sudah pernah datang (done() pernah dipanggil). */
    public boolean hasLoaded() {
        return loadedOnce;
    }

    /** Dipanggil saat tampilan dihancurkan. */
    public void release() {
        stop();
    }

    private void stop() {
        loading = false;
        if (timeoutAction != null) handler.removeCallbacks(timeoutAction);
        timeoutAction = null;
        spinner.setVisibility(View.GONE);
    }
}
