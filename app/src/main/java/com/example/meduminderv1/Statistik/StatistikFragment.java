package com.example.meduminderv1.Statistik;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;

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

import com.example.meduminderv1.Home.ProgressView;
import com.example.meduminderv1.R;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatistikFragment extends Fragment {

    private String mParam1;
    private String mParam2;

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
    private View statistikContent;
    private TextView tvAdherenceDescription;

    public StatistikFragment() {
        // Required empty public constructor
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
        ViewGroup scrollContent = (ViewGroup) view.findViewById(R.id.scrollView);
        statistikContent = scrollContent.getChildAt(0);
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

        statistikRepo = new StatistikRepo();
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
        dailyChartContainer.addView(chart, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        for (int i = 0; i < stats.size(); i++) {
            StatistikRepo.DayStat stat = stats.get(i);
            entries.add(new BarEntry(i, stat.persentase));
            labels.add(stat.label);
        }

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.persentaseKepatuhan));

        int pink = requireContext().getColor(R.color.pink);

        dataSet.setColor(pink);
        dataSet.setValueTextSize(10f);
        dataSet.setDrawValues(true);

        BarData data = new BarData(dataSet);
        chart.setData(data);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

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
            colors.add(requireContext().getColor(R.color.pink));
        }

        if (entries.isEmpty()) {
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");

        dataSet.setColors(colors);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);

        PieData data = new PieData(dataSet);

        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.setDrawEntryLabels(false);
        chart.setUsePercentValues(true);
        chart.setCenterText(getString(R.string.response_center_label));
        chart.setCenterTextSize(14f);
        chart.getLegend().setEnabled(false);
        chart.setHoleRadius(55f);
        chart.setTransparentCircleRadius(60f);
        chart.invalidate();
    }

    private void downloadReport() {
        if (statistikContent == null) {
            Toast.makeText(requireContext(), getString(R.string.gagal_mengambil_laporan), Toast.LENGTH_SHORT).show();
            return;
        }

        int contentWidth = statistikContent.getWidth();
        if (contentWidth <= 0) contentWidth = statistikContent.getMeasuredWidth();
        if (contentWidth <= 0) {
            Toast.makeText(requireContext(), getString(R.string.gagal_mengambil_ukuran_laporan), Toast.LENGTH_SHORT).show();
            return;
        }

        statistikContent.measure(
                View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        int width = statistikContent.getMeasuredWidth();
        int height = statistikContent.getMeasuredHeight();
        statistikContent.layout(0, 0, width, height);

        if (width <= 0 || height <= 0) {
            Toast.makeText(requireContext(), getString(R.string.laporan_ukuran_tidak_valid), Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument document = new PdfDocument();
        int pageWidth = 595;
        int pageHeight = 842;
        float scale = (float) pageWidth / width;
        int scaledHeight = (int) (height * scale);
        int pageCount = Math.max(1, (int) Math.ceil((double) scaledHeight / pageHeight));

        for (int pageNumber = 0; pageNumber < pageCount; pageNumber++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            canvas.save();
            canvas.scale(scale, scale);
            canvas.translate(0, -(pageNumber * pageHeight) / scale);
            statistikContent.draw(canvas);
            canvas.restore();
            document.finishPage(page);
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = getString(R.string.nama_file_laporan_statistik) + "_" + timestamp + ".pdf";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            saveViaMediaStore(document, fileName);
        } else {
            saveViaLegacyFile(document, fileName);
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
}