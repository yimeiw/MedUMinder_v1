package com.example.meduminderv1.Statistik;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import android.graphics.Color;
import android.widget.Toast;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Home.ProgressView;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.example.meduminderv1.Repo.StatistikRepo;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.data.PieData;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatistikFragment extends Fragment {
    private StatistikRepo statistikRepo;
    private Button btnWeekly;
    private Button btnMonthly;
    private Button btnYearly;
    private String selectedPeriod = "weekly";
    private TextView tvAdheranceRate;
    private TextView tvTotalTaken;
    private TextView tvTotalIgnored;
    private TextView tvTotalSnooze;
    private ProgressView adherenceRing;
    private FrameLayout dailyChartContainer;
    private FrameLayout responsePieContainer;
    private Button btnDownloadReport;
    private TextView tvAdherenceDescription;
    private List<StatistikRepo.DayStat> currentStats = new ArrayList<>();
    private int currentTotalDikonsumsi = 0;
    private int currentTotalDiabaikan = 0;
    private int currentTotalSnooze = 0;
    private int currentPersentase = 0;
    private BarChart adherenceChart;
    private PieChart responseChart;

    public StatistikFragment() {
        // Required empty public constructor
    }

    // ===== font aplikasi (app_font) untuk grafik & PDF =====
    // Tanpa ini, grafik dan PDF memakai font bawaan HP (Typeface.DEFAULT).
    private Typeface cachedRegular, cachedBold;

    private Typeface fontRegular() {
        if (cachedRegular == null) {
            Typeface f = ResourcesCompat.getFont(requireContext(), R.font.app_font);
            cachedRegular = f != null ? f : Typeface.DEFAULT;
        }
        return cachedRegular;
    }

    private Typeface fontBold() {
        if (cachedBold == null) {
            // app_font punya versi 700 (bold), jadi ini memakai file bold yang asli
            cachedBold = Typeface.create(fontRegular(), Typeface.BOLD);
        }
        return cachedBold;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(
                R.layout.fragment_statistik, container, false
        );

        ImageButton btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v ->
                requireActivity().onBackPressed()
        );

        btnWeekly = view.findViewById(R.id.btnWeekly);
        btnMonthly = view.findViewById(R.id.btnMonthly);
        btnYearly = view.findViewById(R.id.btnYearly);

        btnWeekly.setOnClickListener(v -> {
            selectedPeriod = "weekly";
            Toast.makeText(requireContext(), getString(R.string.filter_mingguan_terpilih), Toast.LENGTH_SHORT).show();
            updatePeriodButton(v);
            loadStats();
        });

        btnMonthly.setOnClickListener(v -> {
            selectedPeriod = "monthly";
            Toast.makeText(requireContext(), getString(R.string.filter_bulanan_terpilih), Toast.LENGTH_SHORT).show();
            updatePeriodButton(v);
            loadStats();
        });

        btnYearly.setOnClickListener(v -> {
            selectedPeriod = "yearly";
            Toast.makeText(requireContext(), getString(R.string.filter_tahunan_terpilih), Toast.LENGTH_SHORT).show();
            updatePeriodButton(v);
            loadStats();
        });

        tvAdheranceRate = view.findViewById(R.id.tvAdherenceRate);
        tvTotalTaken = view.findViewById(R.id.tvTotalTaken);
        tvTotalIgnored = view.findViewById(R.id.tvTotalIgnored);
        tvTotalSnooze = view.findViewById(R.id.tvTotalSnooze);
        adherenceRing = view.findViewById(R.id.adherenceRing);
        dailyChartContainer = view.findViewById(R.id.dailyChartContainer);
        responsePieContainer = view.findViewById(R.id.responsePieContainer);
        btnDownloadReport = view.findViewById(R.id.btnDownloadReport);
        tvAdherenceDescription = view.findViewById(R.id.tvAdherenceDescription);

        btnDownloadReport.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                downloadReport();
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (requireContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
                } else {
                    downloadReport();
                }
            } else {
                downloadReport();
            }
        });

        statistikRepo = new StatistikRepo(requireContext());
        updatePeriodButton(view);
        loadStats();

        return view;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadReport();
            } else {
                Toast.makeText(requireContext(), getString(R.string.izin_penyimpanan_diperlukan), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadStats(){
        String uid = com.example.meduminderv1.Auth.SessionManager.getInstance().getTargetUid();
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.data_pengguna_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }

        statistikRepo.getAdherence(uid, selectedPeriod, new StatistikRepo.StatsCallback() {
            @Override
            public void onResult(List<StatistikRepo.DayStat> weekStats) {
                if(!isAdded()) return;

                int totalSeharusnya = 0;
                int totalDikonsumsi = 0;
                int totalDiabaikan = 0;
                int totalSnooze = 0;

                for(StatistikRepo.DayStat stat : weekStats) {
                    totalSeharusnya += stat.seharusnya;
                    totalDikonsumsi += stat.dikonsumsi;
                    totalDiabaikan += stat.diabaikan;
                    totalSnooze += stat.snooze;
                }

                int persentase = totalSeharusnya == 0 ? 0 : (int) (totalDikonsumsi * 100f / totalSeharusnya);

                currentStats = weekStats;
                currentTotalDikonsumsi = totalDikonsumsi;
                currentTotalDiabaikan = totalDiabaikan;
                currentTotalSnooze = totalSnooze;
                currentPersentase = persentase;

                tvAdheranceRate.setText(persentase + "%");
                adherenceRing.setProgress(persentase);

                if (persentase >= 80) {
                    tvAdherenceDescription.setText(R.string.desc_kepatuhan_tinggi);
                } else if (persentase >= 50) {
                    tvAdherenceDescription.setText(R.string.desc_kepatuhan_okela);
                } else {
                    tvAdherenceDescription.setText(R.string.desc_kepatuhan_rendah);
                }

                tvTotalTaken.setText(getString(R.string.jumlah_obat, totalDikonsumsi));
                tvTotalIgnored.setText(getString(R.string.jumlah_obat, totalDiabaikan));
                tvTotalSnooze.setText(getString(R.string.jumlah_obat, totalSnooze));

                android.util.Log.d("STAT_DEBUG", "Taken=" + totalDikonsumsi + ", Ignored=" + totalDiabaikan + ", Snooze=" + totalSnooze);

                renderAdherenceChart(weekStats);
                renderResponseAnalysis(totalDikonsumsi, totalDiabaikan, totalSnooze);
            }

            @Override
            public void onFailure(Exception e) {

            }
        });
    }

    private void renderAdherenceChart(List<StatistikRepo.DayStat> stats) {
        dailyChartContainer.removeAllViews();
        BarChart chart = new BarChart(requireContext());
        adherenceChart = chart;
        dailyChartContainer.addView(chart, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        for (int i = 0; i < stats.size(); i++) {
            StatistikRepo.DayStat stat = stats.get(i);
            entries.add(new BarEntry(i, stat.persentase));
            labels.add(stat.label);
        }

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.persentaseKepatuhan));

        int itam = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);
        int ijo = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorTertiaryFixed);

        dataSet.setColor(ijo);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTypeface(fontRegular());
        dataSet.setDrawValues(true);

        BarData data = new BarData(dataSet);
        chart.setData(data);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(itam);
        xAxis.setTypeface(fontRegular());

        chart.getAxisLeft().setTextColor(itam);
        chart.getAxisLeft().setTypeface(fontRegular());
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(100f);
        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setFitBars(true);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.invalidate();
    }

    private void renderResponseAnalysis(int totalDikonsumsi, int totalDiabaikan, int totalSnooze) {
        responsePieContainer.removeAllViews();

        PieChart chart = new PieChart(requireContext());
        responseChart = chart;

        responsePieContainer.addView(
                chart,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        if (totalDikonsumsi > 0) {
            entries.add(new PieEntry(totalDikonsumsi, getString(R.string.dikonsumsi)));
            colors.add(requireContext().getColor(R.color.green));
        }

        if (totalSnooze > 0) {
            entries.add(new PieEntry(totalSnooze, getString(R.string.response_snooze_label)));
            colors.add(requireContext().getColor(R.color.gray));
        }

        if (totalDiabaikan > 0) {
            entries.add(new PieEntry(totalDiabaikan, getString(R.string.response_diabaikan_label)));
            colors.add(requireContext().getColor(R.color.merah));
        }

        if (entries.isEmpty()) {
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");

        dataSet.setColors(colors);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTypeface(fontBold());

        PieData data = new PieData(dataSet);

        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.setDrawEntryLabels(false);
        chart.setUsePercentValues(true);
        chart.setCenterText(getString(R.string.response_center_label));
        chart.setCenterTextSize(14f);
        chart.setCenterTextTypeface(fontBold());
        chart.getLegend().setEnabled(false);
        chart.setHoleRadius(55f);
        chart.setTransparentCircleRadius(60f);
        chart.invalidate();
    }

    private void downloadReport() {
        if (currentStats == null || currentStats.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.gagal_mengambil_laporan),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String uid = com.example.meduminderv1.Auth.SessionManager
                .getInstance()
                .getTargetUid();

        if (uid == null || uid.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.data_pengguna_tidak_ditemukan),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("auth_uid", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;

                    String consumerName;

                    if (!querySnapshot.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot document =
                                querySnapshot.getDocuments().get(0);

                        com.example.meduminderv1.Model.User user =
                                document.toObject(com.example.meduminderv1.Model.User.class);

                        if (user != null
                                && user.getName() != null
                                && !user.getName().isEmpty()) {
                            consumerName = user.getName();
                        } else {
                            consumerName = getString(R.string.nama_tidak_diketahui);
                        }
                    } else {
                        consumerName = getString(R.string.nama_tidak_diketahui);
                    }

                    generateStatisticPdf(consumerName);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;

                    Toast.makeText(
                            requireContext(),
                            getString(R.string.gagal_mengambil_laporan),
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    @SuppressLint("StringFormatInvalid")
    private void generateStatisticPdf(String consumerName) {
        PdfDocument document = new PdfDocument();

        int pageWidth = 595;
        int pageHeight = 842;
        float margin = 45;

        android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        // =========================
        // PAGE 1
        // =========================
        PdfDocument.PageInfo pageInfo1 =
                new PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        1
                ).create();

        PdfDocument.Page page1 = document.startPage(pageInfo1);
        Canvas canvas1 = page1.getCanvas();

        float y = 50;

        // Judul
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        paint.setTypeface(fontBold());
        paint.setTextSize(22);

        canvas1.drawText(
                getString(R.string.laporanStatistik),
                pageWidth / 2f,
                y,
                paint
        );

        y += 30;

        // Nama Consumer
        paint.setTypeface(fontRegular());
        paint.setTextSize(15);

        canvas1.drawText(
                consumerName,
                pageWidth / 2f,
                y,
                paint
        );

        y += 25;

        // Periode
        paint.setTextSize(11);

        canvas1.drawText(
                getString(R.string.periode_laporan)
                        + ": "
                        + getReportPeriod(),
                pageWidth / 2f,
                y,
                paint
        );

        y += 40;

        // Tingkat Kepatuhan
        y = drawSectionTitle(
                canvas1,
                paint,
                getString(R.string.tingkatKepatuhan),
                margin,
                y
        );

        // Ring adherence
        Bitmap adherenceBitmap = getViewBitmap(adherenceRing);

        if (adherenceBitmap != null) {
            int ringSize = 130;
            float ringLeft = (pageWidth - ringSize) / 2f;

            canvas1.drawBitmap(
                    adherenceBitmap,
                    null,
                    new android.graphics.RectF(
                            ringLeft,
                            y,
                            ringLeft + ringSize,
                            y + ringSize
                    ),
                    paint
            );

            y += ringSize + 10;
        }

        // Persentase
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        paint.setTypeface(fontBold());
        paint.setTextSize(18);

        canvas1.drawText(
                currentPersentase + "%",
                pageWidth / 2f,
                y,
                paint
        );

        y += 30;

        // Total
        paint.setTextAlign(android.graphics.Paint.Align.LEFT);
        paint.setTypeface(fontRegular());
        paint.setTextSize(12);

        canvas1.drawText(
                // string "totalObatDikonsumsi" tidak punya %1$d, jadi angkanya tidak ikut tampil.
                // Sekarang: label + ": " + jumlah (pakai string jumlah_obat yang ada angkanya)
                getString(R.string.totalObatDikonsumsi) + ": "
                        + getString(R.string.jumlah_obat, currentTotalDikonsumsi),
                margin,
                y,
                paint
        );

        y += 20;

        canvas1.drawText(
                // string "totalObatDiabaikan" tidak punya %1$d, jadi angkanya tidak ikut tampil.
                // Sekarang: label + ": " + jumlah (pakai string jumlah_obat yang ada angkanya)
                getString(R.string.totalObatDiabaikan) + ": "
                        + getString(R.string.jumlah_obat, currentTotalDiabaikan),
                margin,
                y,
                paint
        );

        y += 20;

        canvas1.drawText(
                // string "totalObatSnooze" tidak punya %1$d, jadi angkanya tidak ikut tampil.
                // Sekarang: label + ": " + jumlah (pakai string jumlah_obat yang ada angkanya)
                getString(R.string.totalObatSnooze) + ": "
                        + getString(R.string.jumlah_obat, currentTotalSnooze),
                margin,
                y,
                paint
        );

        y += 40;

        // Adherence Chart
        y = drawSectionTitle(
                canvas1,
                paint,
                getString(R.string.grafikKepatuhan),
                margin,
                y
        );

        Bitmap barBitmap = getViewBitmap(adherenceChart);

        if (barBitmap != null) {
            int chartWidth = pageWidth - 2 * (int) margin;
            int chartHeight = 300;

            canvas1.drawBitmap(
                    barBitmap,
                    null,
                    new android.graphics.RectF(
                            margin,
                            y,
                            margin + chartWidth,
                            y + chartHeight
                    ),
                    paint
            );
        }

        document.finishPage(page1);


        // =========================
        // PAGE 2
        // =========================
        PdfDocument.PageInfo pageInfo2 =
                new PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        2
                ).create();

        PdfDocument.Page page2 = document.startPage(pageInfo2);
        Canvas canvas2 = page2.getCanvas();

        y = 60;

        // Analisis Response
        y = drawSectionTitle(
                canvas2,
                paint,
                getString(R.string.analisisRespon),
                margin,
                y
        );

        // Donut Chart
        Bitmap pieBitmap = getViewBitmap(responseChart);

        if (pieBitmap != null) {
            int pieSize = 250;
            float pieLeft = (pageWidth - pieSize) / 2f;

            canvas2.drawBitmap(
                    pieBitmap,
                    null,
                    new android.graphics.RectF(
                            pieLeft,
                            y,
                            pieLeft + pieSize,
                            y + pieSize
                    ),
                    paint
            );

            y += pieSize + 30;
        }

        // Legend / angka
        paint.setTextAlign(android.graphics.Paint.Align.LEFT);
        paint.setTypeface(fontRegular());
        paint.setTextSize(12);

        String[] legendLabels = {
                getString(R.string.legend_dikonsumsi_format, currentTotalDikonsumsi),
                getString(R.string.legend_snooze_format, currentTotalSnooze),
                getString(R.string.legend_ignored_format, currentTotalDiabaikan)
        };

        int[] legendColors = {
                requireContext().getColor(R.color.green),
                requireContext().getColor(R.color.gray),
                requireContext().getColor(R.color.merah)
        };

        float dotRadius = 5f, dotTextGap = 6f, itemGap = 20f;

        //hitung total lebar biar barisnya bisa ditengahkan
        float totalLegendWidth = 0f;
        float[] labelWidths = new float[legendLabels.length];
        for (int i = 0; i < legendLabels.length; i++){
            labelWidths[i] = paint.measureText(legendLabels[i]);
            totalLegendWidth += (dotRadius * 2) + dotTextGap +  labelWidths[i];
            if (i < legendLabels.length - 1) totalLegendWidth += itemGap;
        }

        float legendX = (pageWidth - totalLegendWidth) / 2f;
        float dotCenterY = y - 4f; //biar titik sejajar vertikal dengan teks

        for (int i = 0; i < legendLabels.length; i++){
            paint.setColor(legendColors[i]);
            canvas2.drawCircle(legendX + dotRadius, dotCenterY, dotRadius, paint);

            paint.setColor(Color.BLACK);
            canvas2.drawText(legendLabels[i], legendX + (dotRadius * 2) + dotTextGap, y, paint);

            legendX += (dotRadius * 2) + dotTextGap + labelWidths[i] + itemGap;
        } y+= 35;

        // Penjelasan
        paint.setTextSize(11);

        y = drawWrappedText(canvas2, paint,
                getString(R.string.penjelasan_dikonsumsi),
                margin, y, pageWidth - 2 * margin);
        y += 12;

        y = drawWrappedText(canvas2, paint,
                getString(R.string.penjelasan_snooze),
                margin, y, pageWidth - 2 * margin);
        y += 12;

        drawWrappedText(canvas2, paint,
                getString(R.string.penjelasan_diabaikan), margin, y,
                pageWidth - 2 * margin);

        document.finishPage(page2);


        String timestamp = new SimpleDateFormat(
                "MMddyyyy", Locale.getDefault()
        ).format(new Date());

        String fileName = "MedUMinder_" + timestamp + "_Statistics.pdf";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            saveViaMediaStore(document, fileName);
        } else {
            saveViaLegacyFile(document, fileName);
        }
    }

    private float drawSectionTitle(
            Canvas canvas,
            android.graphics.Paint paint,
            String title,
            float x,
            float y
    ) {
        paint.setTextAlign(android.graphics.Paint.Align.LEFT);
        paint.setTypeface(fontBold());
        paint.setTextSize(16);

        canvas.drawText(title, x, y, paint);

        return y + 25;
    }

    private float drawWrappedText(
            Canvas canvas,
            android.graphics.Paint paint,
            String text,
            float x,
            float y,
            float maxWidth
    ) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        float lineHeight = paint.getTextSize() + 5;

        for (String word : words) {
            String testLine;

            if (line.length() == 0) {
                testLine = word;
            } else {
                testLine = line + " " + word;
            }

            if (paint.measureText(testLine) > maxWidth) {
                canvas.drawText(
                        line.toString(),
                        x,
                        y,
                        paint
                );

                y += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(testLine);
            }
        }

        if (line.length() > 0) {
            canvas.drawText(
                    line.toString(),
                    x,
                    y,
                    paint
            );

            y += lineHeight;
        }

        return y;
    }

    private Bitmap getViewBitmap(View view) {
        if (view == null) {
            return null;
        }

        int width = view.getWidth();
        int height = view.getHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        return bitmap;
    }

    private String getReportPeriod() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();

        SimpleDateFormat dateFormat =
                new SimpleDateFormat(
                        "dd MMMM yyyy",
                        Locale.getDefault()
                );

        if ("weekly".equals(selectedPeriod)) {
            calendar.set(
                    java.util.Calendar.DAY_OF_WEEK,
                    java.util.Calendar.MONDAY
            );

            Date startDate = calendar.getTime();

            calendar.add(
                    java.util.Calendar.DAY_OF_MONTH,
                    6
            );

            Date endDate = calendar.getTime();

            return dateFormat.format(startDate)
                    + " - "
                    + dateFormat.format(endDate);

        } else if ("monthly".equals(selectedPeriod)) {

            calendar.set(
                    java.util.Calendar.DAY_OF_MONTH,
                    1
            );

            Date startDate = calendar.getTime();

            calendar.set(
                    java.util.Calendar.DAY_OF_MONTH,
                    calendar.getActualMaximum(
                            java.util.Calendar.DAY_OF_MONTH
                    )
            );

            Date endDate = calendar.getTime();

            return dateFormat.format(startDate)
                    + " - "
                    + dateFormat.format(endDate);

        } else {

            calendar.set(
                    java.util.Calendar.DAY_OF_YEAR,
                    1
            );

            Date startDate = calendar.getTime();

            calendar.set(
                    java.util.Calendar.MONTH,
                    java.util.Calendar.DECEMBER
            );

            calendar.set(
                    java.util.Calendar.DAY_OF_MONTH,
                    31
            );

            Date endDate = calendar.getTime();

            return dateFormat.format(startDate)
                    + " - "
                    + dateFormat.format(endDate);
        }
    }

    private void saveViaMediaStore(PdfDocument document, String fileName) {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf");
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);

        android.content.ContentResolver resolver = requireContext().getContentResolver();
        android.net.Uri itemUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

        if (itemUri == null) {
            document.close();
            Toast.makeText(requireContext(), getString(R.string.gagal_membuat_file_laporan), Toast.LENGTH_LONG).show();
            return;
        }

        try (java.io.OutputStream out = resolver.openOutputStream(itemUri)) {
            document.writeTo(out);
            document.close();

            values.clear();
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(itemUri, values, null, null);
            Toast.makeText(requireContext(), getString(R.string.laporan_berhasil_diunduh), Toast.LENGTH_LONG).show();
            saveReportNotification(fileName, itemUri.toString(), null);
            openPdf(itemUri);
        } catch (IOException e) {
            document.close();
            Toast.makeText(requireContext(), getString(R.string.gagal_mengunduh_laporan), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    // Cara LAMA (Android 9 ke bawah): tetap pakai File API kayak sebelumnya
    private void saveViaLegacyFile(PdfDocument document, String fileName) {
        File downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        if (!downloadFolder.exists()) {
            boolean created = downloadFolder.mkdirs();
            if (!created && !downloadFolder.exists()) {
                document.close();
                Toast.makeText(requireContext(), getString(R.string.gagal_membuat_folder_download), Toast.LENGTH_LONG).show();
                return;
            }
        }

        File pdfFile = new File(downloadFolder, fileName);

        try {
            FileOutputStream outputStream = new FileOutputStream(pdfFile);
            document.writeTo(outputStream);
            outputStream.close();
            document.close();
            Toast.makeText(requireContext(), getString(R.string.laporan_berhasil_diunduh), Toast.LENGTH_LONG).show();
            saveReportNotification(fileName, null, pdfFile.getAbsolutePath());
            Uri contentUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", pdfFile);
            openPdf(contentUri);
        } catch (IOException e) {
            document.close();
            Toast.makeText(requireContext(), getString(R.string.gagal_mengunduh_laporan), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void updatePeriodButton(View root) {
        btnWeekly.setBackgroundResource(R.drawable.border);
        btnMonthly.setBackgroundResource(R.drawable.border);
        btnYearly.setBackgroundResource(R.drawable.border);

        int activeColor = com.google.android.material.color.MaterialColors.getColor(
                root, com.google.android.material.R.attr.colorSecondary);

        btnWeekly.setBackgroundTintList(null);
        btnMonthly.setBackgroundTintList(null);
        btnYearly.setBackgroundTintList(null);

        if ("weekly".equals(selectedPeriod)) {
            btnWeekly.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeColor));
        } else if ("monthly".equals(selectedPeriod)) {
            btnMonthly.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeColor));
        } else if ("yearly".equals(selectedPeriod)) {
            btnYearly.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeColor));
        }
    }

    private void openPdf(android.net.Uri uri) {
        if (!isAdded()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(requireContext(), getString(R.string.aplikasi_pembaca_pdf_tidak_ditemukan), Toast.LENGTH_SHORT).show();
        }
    }
    private void saveReportNotification(String fileName, String mediaStoreUri, String legacyFilePath){
        if (!isAdded()) return;
        User user = SessionManager.getInstance().getUser();
        if (user == null) return;
        new NotificationRepo(requireContext()).createReportNotif(user.getAuth_uid(), getString(R.string.laporan_berhasil_diunduh),
                fileName, mediaStoreUri, legacyFilePath, new RepoCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {}

                    @Override
                    public void onFailure(Exception e) {}
                });
    }
}