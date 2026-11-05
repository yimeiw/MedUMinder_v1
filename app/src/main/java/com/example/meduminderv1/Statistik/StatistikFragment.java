package com.example.meduminderv1.Statistik;

import com.example.meduminderv1.Caregiver.ConsumerPickerHelper;
import com.example.meduminderv1.Util.LoadingOverlay;

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
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.github.mikephil.charting.charts.Chart;
import com.google.android.material.button.MaterialButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import android.graphics.Color;
import com.example.meduminderv1.Util.Toast;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Home.ProgressView;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.example.meduminderv1.Repo.StatistikRepo;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.data.PieData;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    private MaterialButton btnWeekly;
    private MaterialButton btnMonthly;
    private MaterialButton btnYearly;
    private String selectedPeriod = "weekly";
    private TextView tvAdheranceRate;
    private TextView tvTotalTaken;
    private TextView tvTotalIgnored;
    private TextView tvTotalSnooze;
    private ProgressView adherenceRing;
    private FrameLayout dailyChartContainer;
    private FrameLayout responsePieContainer;
    private Button btnDownloadReport;
    private boolean isDownloading = false;
    private TextView tvAdherenceDescription;
    private List<StatistikRepo.DayStat> currentStats = new ArrayList<>();
    private int currentTotalDikonsumsi = 0;
    private int currentTotalDiabaikan = 0;
    private int currentTotalSnooze = 0;
    private int currentPersentase = 0;
    private int weekOffset = 0;
    private TextView tvChartAverage, tvWeekFilter, tvLegendMedicine, tvLegendAppointment;
    private java.util.Map<String, Integer> reportTakenMedicines;
    private long reportAnchorMillis = 0L;
    private BarChart adherenceChart;
    private PieChart responseChart;
    ConsumerPickerHelper consumerPickerHelper;
    String targetUid;
    int loadToken = 0;

    public StatistikFragment() {
    }

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
            Typeface f = ResourcesCompat.getFont(requireContext(), R.font.inter_18pt_bold);
            cachedBold = f != null ? f : Typeface.create(fontRegular(), Typeface.BOLD);
        }
        return cachedBold;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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
        tvChartAverage = view.findViewById(R.id.tvChartAverage);
        tvWeekFilter = view.findViewById(R.id.tvWeekFilter);
        tvLegendMedicine = view.findViewById(R.id.tvLegendMedicine);
        tvLegendAppointment = view.findViewById(R.id.tvLegendAppointment);
        tvWeekFilter.setOnClickListener(v -> showWeekPicker());

        statistikRepo = new StatistikRepo(requireContext());
        updatePeriodButton(view);

        View pickerRoot = view.findViewById(R.id.consumerPicker);
        consumerPickerHelper = new ConsumerPickerHelper(pickerRoot, requireContext(), uid -> {
            targetUid = uid;
            boolean hasConsumer = uid != null;
            if (!hasConsumer){
                pickerRoot.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
                showNoConsumerState();
            } else {
                btnDownloadReport.setEnabled(true);
                loadStats();
            }
        }); consumerPickerHelper.setup();

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
        final String uid = targetUid;
        if (uid == null || uid.isEmpty()){
            showNoConsumerState();
            return;
        } final int token = ++loadToken;
        boolean weekly = "weekly".equals(selectedPeriod);
        tvWeekFilter.setVisibility(weekly ? View.VISIBLE : View.GONE);
        tvWeekFilter.setText(weekLabel(weekOffset));

        statistikRepo.getAdherenceWithAppointments(uid, selectedPeriod,
                weekly ? weekAnchorMillis() : System.currentTimeMillis(), new StatistikRepo.StatsCallback() {
            @Override
            public void onResult(List<StatistikRepo.DayStat> weekStats) {
                if(!isAdded() || token != loadToken) return;

                int totalDikonsumsi = 0;
                int totalDiabaikan = 0;
                int totalSnooze = 0;

                for(StatistikRepo.DayStat stat : weekStats) {
                    totalDikonsumsi += stat.dikonsumsi;
                    totalDiabaikan += stat.diabaikan;
                    totalSnooze += stat.snooze;
                }

                int persentase = combinedPercent(weekStats);

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

                Log.d("STAT_DEBUG", "Taken=" + totalDikonsumsi + ", Ignored=" + totalDiabaikan + ", Snooze=" + totalSnooze);

                renderAdherenceChart(weekStats);
                updateChartSummary(weekStats, persentase);
                renderResponseAnalysis(totalDikonsumsi, totalDiabaikan, totalSnooze);
            }

            @Override
            public void onFailure(Exception e) {

            }
        });
    }

    private void showNoConsumerState(){
        loadToken++;
        currentStats = new ArrayList<>();
        currentTotalDikonsumsi = currentTotalDiabaikan = currentTotalSnooze = currentPersentase = 0;

        tvAdheranceRate.setText("0%");
        adherenceRing.setProgress(0);
        tvAdherenceDescription.setText(R.string.noConsumptionData);
        tvTotalTaken.setText(getString(R.string.jumlah_obat, 0));
        tvTotalIgnored.setText(getString(R.string.jumlah_obat, 0));
        tvTotalSnooze.setText(getString(R.string.jumlah_obat, 0));
        dailyChartContainer.removeAllViews();
        responsePieContainer.removeAllViews();
        updateChartSummary(new ArrayList<>(), 0);
        btnDownloadReport.setEnabled(false);
    }

    private void renderAdherenceChart(List<StatistikRepo.DayStat> stats) {
        dailyChartContainer.removeAllViews();
        BarChart chart = new BarChart(requireContext());
        styleNoData(chart);
        adherenceChart = chart;
        dailyChartContainer.addView(chart, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        boolean isMonthly = "monthly".equals(selectedPeriod);
        List<String> labels = shortChartLabels(stats);

        ArrayList<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < stats.size(); i++) {
            entries.add(new BarEntry(isMonthly ? i + 1 : i, stackOf(stats.get(i))));
        }

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.persentaseKepatuhan));

        int itam = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);
        int garis = MaterialColors.getColor(requireView(), R.attr.appOutline);

        dataSet.setColors(chartColors());
        dataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        dataSet.setHighlightEnabled(false);
        dataSet.setDrawValues(false);

        BarData data = new BarData(dataSet);
        data.setBarWidth(isMonthly ? 0.7f : 0.6f);
        chart.setData(data);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setAxisLineColor(garis);
        xAxis.setTextColor(itam);
        xAxis.setTextSize(10f);
        xAxis.setTypeface(fontRegular());
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setAvoidFirstLastClipping(false);
        if (isMonthly) {
            xAxis.setAxisMinimum(0.5f);
            xAxis.setAxisMaximum(stats.size() + 0.5f);
            xAxis.setLabelCount(7);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.valueOf(Math.round(value));
                }
            });
        } else {
            xAxis.setAxisMinimum(-0.5f);
            xAxis.setAxisMaximum(stats.size() - 0.5f);
            xAxis.setLabelCount(stats.size());
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        }

        YAxis right = chart.getAxisRight();
        right.setTextColor(itam);
        right.setTextSize(10f);
        right.setTypeface(fontRegular());
        right.setAxisMinimum(0f);
        right.setAxisMaximum(100f);
        right.setLabelCount(5, true);
        right.setGridColor(garis);
        right.enableGridDashedLine(6f, 6f, 0f);
        right.setDrawAxisLine(false);
        right.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return Math.round(value) + "%";
            }
        });
        if (hasDue(stats)) {
            LimitLine average = new LimitLine(combinedPercent(stats), getString(R.string.rata_rata_singkat));
            average.setLineColor(itam);
            average.setLineWidth(1.2f);
            average.enableDashedLine(10f, 8f, 0f);
            average.setTextColor(itam);
            average.setTextSize(10f);
            average.setTypeface(fontRegular());
            average.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
            right.addLimitLine(average);
        }

        chart.getAxisLeft().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setExtraBottomOffset(22f);
        chart.setExtraRightOffset(16f);
        chart.setFitBars(true);
        chart.setTouchEnabled(false);
        chart.setScaleEnabled(false);
        chart.invalidate();
        addAxisTitles(itam);
    }

    private float[] stackOf(StatistikRepo.DayStat stat) {
        int due = stat.due();
        if (due == 0) return new float[]{0f, 0f};
        return new float[]{stat.dikonsumsi * 100f / due, stat.apptAttended * 100f / due};
    }

    private int[] chartColors() {
        return new int[]{requireContext().getColor(R.color.green), requireContext().getColor(R.color.biru)};
    }

    private boolean hasDue(List<StatistikRepo.DayStat> stats) {
        for (StatistikRepo.DayStat stat : stats) if (stat.due() > 0) return true;
        return false;
    }

    private int combinedPercent(List<StatistikRepo.DayStat> stats) {
        int due = 0, done = 0;
        for (StatistikRepo.DayStat stat : stats) {
            due += stat.due();
            done += stat.done();
        }
        return due == 0 ? 0 : (int) (done * 100f / due);
    }

    private int[] legendTotals(List<StatistikRepo.DayStat> stats) {
        int medDue = 0, medDone = 0, apptDue = 0, apptDone = 0;
        for (StatistikRepo.DayStat stat : stats) {
            medDue += stat.seharusnya;
            medDone += stat.dikonsumsi;
            apptDue += stat.apptDue;
            apptDone += stat.apptAttended;
        }
        return new int[]{medDone, medDue, apptDone, apptDue};
    }

    private void updateChartSummary(List<StatistikRepo.DayStat> stats, int percent) {
        int[] totals = legendTotals(stats);
        tvChartAverage.setText(percent + "%");
        tvLegendMedicine.setText(totals[0] + "/" + totals[1]);
        tvLegendAppointment.setText(totals[2] + "/" + totals[3]);
    }

    private long weekAnchorMillis() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -7 * weekOffset);
        return calendar.getTimeInMillis();
    }

    private String weekLabel(int offset) {
        if (offset == 0) return getString(R.string.minggu_ini);
        if (offset == 1) return getString(R.string.minggu_lalu);
        return getString(R.string.x_minggu_lalu, offset);
    }

    private String weekRange(int offset) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        SimpleDateFormat format = new SimpleDateFormat("dd MMM", locale);
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setFirstDayOfWeek(java.util.Calendar.MONDAY);
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -7 * offset);
        calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY);
        String start = format.format(calendar.getTime());
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 6);
        return start + " - " + format.format(calendar.getTime());
    }

    private void showWeekPicker() {
        String[] labels = new String[4];
        for (int i = 0; i < labels.length; i++) labels[i] = weekLabel(i) + " (" + weekRange(i) + ")";
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pilih_minggu_laporan)
                .setItems(labels, (d, which) -> {
                    weekOffset = which;
                    loadStats();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MaterialColors.getColor(requireContext(),
                    com.google.android.material.R.attr.colorTertiaryFixedDim, Color.BLACK));
        }
    }

    /** Judul sumbu: MPAndroidChart tidak punya, jadi ditumpuk di atas grafik. */
    private void addAxisTitles(int color) {
        TextView xTitle = axisTitle(xAxisTitle(), color);
        dailyChartContainer.addView(xTitle, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL));

        TextView yTitle = axisTitle(getString(R.string.sumbu_y_kepatuhan), color);
        yTitle.setRotation(90f);
        dailyChartContainer.addView(yTitle, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL));
        yTitle.post(() -> yTitle.setTranslationX((yTitle.getWidth() - yTitle.getHeight()) / 2f));
    }

    private TextView axisTitle(String text, int color) {
        TextView title = new TextView(requireContext());
        title.setText(text);
        title.setTextColor(color);
        title.setTextSize(10f);
        title.setTypeface(fontRegular());
        return title;
    }

    private String xAxisTitle() {
        if ("weekly".equals(selectedPeriod)) return getString(R.string.sumbu_x_hari);
        return getString("monthly".equals(selectedPeriod) ? R.string.sumbu_x_tanggal : R.string.sumbu_x_bulan);
    }

    private List<String> shortChartLabels(List<StatistikRepo.DayStat> stats) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        java.text.DateFormatSymbols symbols = java.text.DateFormatSymbols.getInstance(locale);
        List<String> labels = new ArrayList<>();
        if ("weekly".equals(selectedPeriod) && stats.size() == 7) {
            String[] days = symbols.getShortWeekdays();
            int[] order = {java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY, java.util.Calendar.WEDNESDAY,
                    java.util.Calendar.THURSDAY, java.util.Calendar.FRIDAY, java.util.Calendar.SATURDAY,
                    java.util.Calendar.SUNDAY};
            for (int d : order) labels.add(days[d]);
        } else if ("yearly".equals(selectedPeriod) && stats.size() == 12) {
            String[] months = symbols.getShortMonths();
            for (int m = 0; m < 12; m++) labels.add(months[m]);
        } else {
            for (StatistikRepo.DayStat stat : stats) labels.add(stat.label);
        }
        return labels;
    }

    private void renderResponseAnalysis(int totalDikonsumsi, int totalDiabaikan, int totalSnooze) {
        responsePieContainer.removeAllViews();

        PieChart chart = new PieChart(requireContext());
        responseChart = chart;
        styleNoData(chart);

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
            chart.invalidate();
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
        String uid = targetUid;

        if (uid == null || uid.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.data_pengguna_tidak_ditemukan),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        boolean weekly = "weekly".equals(selectedPeriod);
        if (weekly && !canDownload(currentStats)) return;

        if (isDownloading) return;
        isDownloading = true;
        btnDownloadReport.setEnabled(false);
        LoadingOverlay.show(StatistikFragment.this);
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("auth_uid", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) { finishDownload(); return; }

                    String consumerName = getString(R.string.nama_tidak_diketahui);
                    long registeredAt = System.currentTimeMillis();

                    if (!querySnapshot.isEmpty()) {
                        com.example.meduminderv1.Model.User user =
                                querySnapshot.getDocuments().get(0).toObject(com.example.meduminderv1.Model.User.class);

                        if (user != null && user.getName() != null && !user.getName().isEmpty()) {
                            consumerName = user.getName();
                        }
                        if (user != null && user.getCreated_at() != null) {
                            registeredAt = user.getCreated_at().toDate().getTime();
                        }
                    }

                    if (weekly) {
                        generateWithMedicines(uid, consumerName, "weekly", weekAnchorMillis(), currentStats);
                    } else {
                        LoadingOverlay.hide(StatistikFragment.this);
                        finishDownload();
                        showReportPeriodPicker(uid, consumerName, registeredAt);
                    }
                })
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide(StatistikFragment.this);
                    finishDownload();
                    if (!isAdded()) return;

                    Toast.makeText(
                            requireContext(),
                            getString(R.string.gagal_mengambil_laporan),
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private boolean canDownload(List<StatistikRepo.DayStat> stats) {
        if (stats == null || stats.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.gagal_mengambil_laporan), Toast.LENGTH_SHORT).show();
            return false;
        }
        for (StatistikRepo.DayStat stat : stats) {
            if (stat.due() > 0 || stat.dikonsumsi > 0 || stat.diabaikan > 0 || stat.snooze > 0) return true;
        }
        Toast.makeText(requireContext(), getString(R.string.tidak_ada_data_untuk_diunduh), Toast.LENGTH_SHORT).show();
        return false;
    }

    /** Bulanan / tahunan: pilih periode dari bulan/tahun akun terdaftar sampai sekarang (terbaru di atas). */
    private void showReportPeriodPicker(String uid, String consumerName, long registeredAt) {
        if (!isAdded()) return;
        final String period = selectedPeriod;
        boolean monthly = "monthly".equals(period);
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        SimpleDateFormat labelFormat = new SimpleDateFormat(monthly ? "MMMM yyyy" : "yyyy", locale);

        java.util.Calendar first = java.util.Calendar.getInstance();
        first.setTimeInMillis(registeredAt);
        java.util.Calendar cursor = java.util.Calendar.getInstance();
        cursor.set(java.util.Calendar.DAY_OF_MONTH, 1);

        List<Long> anchors = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        while (monthly
                ? cursor.get(java.util.Calendar.YEAR) * 12 + cursor.get(java.util.Calendar.MONTH)
                        >= first.get(java.util.Calendar.YEAR) * 12 + first.get(java.util.Calendar.MONTH)
                : cursor.get(java.util.Calendar.YEAR) >= first.get(java.util.Calendar.YEAR)) {
            anchors.add(cursor.getTimeInMillis());
            labels.add(labelFormat.format(cursor.getTime()));
            cursor.add(monthly ? java.util.Calendar.MONTH : java.util.Calendar.YEAR, -1);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(monthly ? R.string.pilih_bulan_laporan : R.string.pilih_tahun_laporan)
                .setItems(labels.toArray(new String[0]),
                        (d, which) -> downloadForPeriod(uid, consumerName, period, anchors.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MaterialColors.getColor(requireContext(),
                    com.google.android.material.R.attr.colorTertiaryFixedDim, Color.BLACK));
        }
    }

    private void downloadForPeriod(String uid, String consumerName, String period, long anchorMillis) {
        if (isDownloading) return;
        isDownloading = true;
        btnDownloadReport.setEnabled(false);
        LoadingOverlay.show(StatistikFragment.this);
        statistikRepo.getAdherenceWithAppointments(uid, period, anchorMillis, new StatistikRepo.StatsCallback() {
            @Override
            public void onResult(List<StatistikRepo.DayStat> stats) {
                if (isAdded() && canDownload(stats)) {
                    generateWithMedicines(uid, consumerName, period, anchorMillis, stats);
                    return;
                }
                LoadingOverlay.hide(StatistikFragment.this);
                finishDownload();
            }

            @Override
            public void onFailure(Exception e) {
                LoadingOverlay.hide(StatistikFragment.this);
                finishDownload();
                if (!isAdded()) return;
                Toast.makeText(requireContext(), getString(R.string.gagal_mengambil_laporan), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** PDF digambar dari field current*: dipinjam sebentar untuk periode pilihan, tampilan layar tidak berubah. */
    private void generateWithMedicines(String uid, String consumerName, String period, long anchorMillis,
                                       List<StatistikRepo.DayStat> stats) {
        statistikRepo.getTakenMedicines(uid, period, anchorMillis, taken -> {
            if (isAdded()) {
                reportTakenMedicines = taken;
                generateReportFor(consumerName, period, anchorMillis, stats);
                reportTakenMedicines = null;
            }
            LoadingOverlay.hide(StatistikFragment.this);
            finishDownload();
        });
    }

    private void generateReportFor(String consumerName, String period, long anchorMillis, List<StatistikRepo.DayStat> stats) {
        List<StatistikRepo.DayStat> savedStats = currentStats;
        int savedTaken = currentTotalDikonsumsi, savedIgnored = currentTotalDiabaikan,
                savedSnooze = currentTotalSnooze, savedPercent = currentPersentase;
        String savedPeriod = selectedPeriod;

        int taken = 0, ignored = 0, snooze = 0;
        for (StatistikRepo.DayStat stat : stats) {
            taken += stat.dikonsumsi;
            ignored += stat.diabaikan;
            snooze += stat.snooze;
        }
        currentStats = stats;
        currentTotalDikonsumsi = taken;
        currentTotalDiabaikan = ignored;
        currentTotalSnooze = snooze;
        currentPersentase = combinedPercent(stats);
        selectedPeriod = period;
        reportAnchorMillis = anchorMillis;
        try {
            generateStatisticPdf(consumerName);
        } finally {
            currentStats = savedStats;
            currentTotalDikonsumsi = savedTaken;
            currentTotalDiabaikan = savedIgnored;
            currentTotalSnooze = savedSnooze;
            currentPersentase = savedPercent;
            selectedPeriod = savedPeriod;
            reportAnchorMillis = 0L;
        }
    }

    private void finishDownload() {
        isDownloading = false;
        if (btnDownloadReport != null) btnDownloadReport.setEnabled(targetUid != null);
    }

    @SuppressLint("StringFormatInvalid")
    private void generateStatisticPdf(String consumerName) {
        PdfDocument document = new PdfDocument();

        int pageWidth = 595;
        int pageHeight = 842;
        float margin = 45;

        android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        PdfDocument.PageInfo pageInfo1 =
                new PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        1
                ).create();

        PdfDocument.Page page1 = document.startPage(pageInfo1);
        Canvas canvas1 = page1.getCanvas();

        float y = 50;

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

        paint.setTypeface(fontRegular());
        paint.setTextSize(15);

        canvas1.drawText(
                consumerName,
                pageWidth / 2f,
                y,
                paint
        );

        y += 25;

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

        y = drawSectionTitle(
                canvas1,
                paint,
                getString(R.string.tingkatKepatuhan),
                margin,
                y
        );

        {
            int ringSize = 130;
            float ringLeft = (pageWidth - ringSize) / 2f;
            drawPdfRing(canvas1, ringLeft, y, ringSize, currentPersentase);
            y += ringSize + 10;
        }

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

        paint.setTextAlign(android.graphics.Paint.Align.LEFT);
        paint.setTypeface(fontRegular());
        paint.setTextSize(12);

        canvas1.drawText(
                getString(R.string.totalObatDikonsumsi) + ": "
                        + getString(R.string.jumlah_obat, currentTotalDikonsumsi),
                margin,
                y,
                paint
        );

        y += 20;

        canvas1.drawText(
                getString(R.string.totalObatDiabaikan) + ": "
                        + getString(R.string.jumlah_obat, currentTotalDiabaikan),
                margin,
                y,
                paint
        );

        y += 20;

        canvas1.drawText(
                getString(R.string.totalObatSnooze) + ": "
                        + getString(R.string.jumlah_obat, currentTotalSnooze),
                margin,
                y,
                paint
        );

        y += 40;

        y = drawSectionTitle(
                canvas1,
                paint,
                getString(R.string.grafikKepatuhan),
                margin,
                y
        );

        {
            int chartWidth = pageWidth - 2 * (int) margin;
            int chartHeight = 300;
            drawPdfBarChart(canvas1, margin, y, chartWidth, chartHeight);
        }

        document.finishPage(page1);


        PdfDocument.PageInfo pageInfo2 =
                new PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        2
                ).create();

        PdfDocument.Page page2 = document.startPage(pageInfo2);
        Canvas canvas2 = page2.getCanvas();

        y = 60;

        y = drawSectionTitle(
                canvas2,
                paint,
                getString(R.string.analisisRespon),
                margin,
                y
        );

        {
            int pieSize = 250;
            float pieLeft = (pageWidth - pieSize) / 2f;
            drawPdfDonut(canvas2, pieLeft, y, pieSize);
            y += pieSize + 30;
        }

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

        float totalLegendWidth = 0f;
        float[] labelWidths = new float[legendLabels.length];
        for (int i = 0; i < legendLabels.length; i++){
            labelWidths[i] = paint.measureText(legendLabels[i]);
            totalLegendWidth += (dotRadius * 2) + dotTextGap +  labelWidths[i];
            if (i < legendLabels.length - 1) totalLegendWidth += itemGap;
        }

        float legendX = (pageWidth - totalLegendWidth) / 2f;
        float dotCenterY = y - 4f;

        for (int i = 0; i < legendLabels.length; i++){
            paint.setColor(legendColors[i]);
            canvas2.drawCircle(legendX + dotRadius, dotCenterY, dotRadius, paint);

            paint.setColor(Color.BLACK);
            canvas2.drawText(legendLabels[i], legendX + (dotRadius * 2) + dotTextGap, y, paint);

            legendX += (dotRadius * 2) + dotTextGap + labelWidths[i] + itemGap;
        } y+= 35;

        paint.setTextSize(11);

        y = drawWrappedText(canvas2, paint,
                getString(R.string.penjelasan_dikonsumsi),
                margin, y, pageWidth - 2 * margin);
        y += 12;

        y = drawWrappedText(canvas2, paint,
                getString(R.string.penjelasan_snooze),
                margin, y, pageWidth - 2 * margin);
        y += 12;

        y = drawWrappedText(canvas2, paint,
                getString(R.string.penjelasan_diabaikan), margin, y,
                pageWidth - 2 * margin);

        PdfDocument.Page lastPage = page2;
        if (reportTakenMedicines != null) {
            Canvas listCanvas = canvas2;
            y += 30;
            if (y > pageHeight - 90) {
                document.finishPage(lastPage);
                lastPage = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create());
                listCanvas = lastPage.getCanvas();
                y = 60;
            }
            paint.setColor(Color.BLACK);
            y = drawSectionTitle(listCanvas, paint, getString(R.string.obat_diminum_title), margin, y);
            paint.setTypeface(fontRegular());
            paint.setTextSize(12);
            if (reportTakenMedicines.isEmpty()) {
                listCanvas.drawText(getString(R.string.tidak_ada_obat_diminum), margin, y, paint);
            }
            int pageNumber = 3;
            for (java.util.Map.Entry<String, Integer> medicine : reportTakenMedicines.entrySet()) {
                if (y > pageHeight - 50) {
                    document.finishPage(lastPage);
                    pageNumber++;
                    lastPage = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                    listCanvas = lastPage.getCanvas();
                    y = 60;
                }
                String name = medicine.getKey();
                float maxName = pageWidth - 2 * margin - 50;
                while (name.length() > 1 && paint.measureText(name) > maxName) {
                    name = name.substring(0, name.length() - 1);
                }
                paint.setTextAlign(android.graphics.Paint.Align.LEFT);
                listCanvas.drawText(name, margin, y, paint);
                paint.setTextAlign(android.graphics.Paint.Align.RIGHT);
                listCanvas.drawText(medicine.getValue() + "x", pageWidth - margin, y, paint);
                y += 20;
            }
            paint.setTextAlign(android.graphics.Paint.Align.LEFT);
        }

        document.finishPage(lastPage);


        String pattern = "monthly".equals(selectedPeriod) ? "yyyyMM"
                : "yearly".equals(selectedPeriod) ? "yyyy" : "MMddyyyy";
        String timestamp = new SimpleDateFormat(pattern, Locale.ROOT)
                .format(new Date(reportAnchorMillis > 0 ? reportAnchorMillis : System.currentTimeMillis()));

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

    private static final int PDF_TEXT = Color.parseColor("#0B1E33");
    private static final int PDF_MUTED = Color.parseColor("#5B6576");
    private static final int PDF_GRID = Color.parseColor("#E3E7EE");
    private static final int PDF_TRACK = Color.parseColor("#E0E0E0");

    /** Ring kepatuhan: sama seperti di layar (lingkaran abu + progres hijau + persen di tengah). */
    private void drawPdfRing(Canvas c, float left, float top, float size, int percent) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float stroke = size * 0.16f;
        float pad = stroke / 2f + 2;
        android.graphics.RectF oval = new android.graphics.RectF(left + pad, top + pad, left + size - pad, top + size - pad);

        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setColor(PDF_TRACK);
        c.drawArc(oval, 0, 360, false, p);

        p.setColor(requireContext().getColor(R.color.green));
        p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        float sweep = Math.min(360f * Math.max(0, percent) / 100f, 359.99f);
        if (sweep > 0) c.drawArc(oval, -90, sweep, false, p);

        p.setStyle(android.graphics.Paint.Style.FILL);
        p.setColor(PDF_TEXT);
        p.setTypeface(fontBold());
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        p.setTextSize(size * 0.2f);
        float textY = top + size / 2f - (p.descent() + p.ascent()) / 2f;
        c.drawText(percent + "%", left + size / 2f, textY, p);
    }

    /** Grafik batang kepatuhan 0-100%: sumbu 0/25/50/75/100, label X pendek, nilai di atas batang. */
    private void drawPdfBarChart(Canvas c, float x, float y, float w, float h) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        List<StatistikRepo.DayStat> stats = currentStats != null ? currentStats : new ArrayList<>();
        List<String> labels = shortChartLabels(stats);
        boolean isMonthly = "monthly".equals(selectedPeriod);
        int n = stats.size();

        float axisW = 48f;
        float labelH = 34f;
        float plotL = x, plotR = x + w - axisW, plotT = y + 14, plotB = y + h - labelH;
        float plotH = plotB - plotT;

        p.setStrokeWidth(0.8f);
        p.setTypeface(fontRegular());
        p.setTextSize(9f);
        p.setTextAlign(android.graphics.Paint.Align.LEFT);
        for (int pct = 0; pct <= 100; pct += 25) {
            float gy = plotB - plotH * pct / 100f;
            p.setColor(pct == 0 ? PDF_MUTED : PDF_GRID);
            c.drawLine(plotL, gy, plotR, gy, p);
            p.setColor(PDF_MUTED);
            c.drawText(pct + "%", plotR + 6, gy + 3, p);
        }

        p.setColor(PDF_MUTED);
        p.setTextSize(10f);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        c.drawText(xAxisTitle(), (plotL + plotR) / 2f, plotB + 29, p);
        c.save();
        c.rotate(90f, x + w - 8, (plotT + plotB) / 2f);
        c.drawText(getString(R.string.sumbu_y_kepatuhan), x + w - 8, (plotT + plotB) / 2f, p);
        c.restore();

        int[] totals = legendTotals(stats);
        int[] colors = chartColors();
        String[] legend = {
                getString(R.string.obat_default) + " " + totals[0] + "/" + totals[1],
                getString(R.string.appointment) + " " + totals[2] + "/" + totals[3]
        };
        float box = 9f, gap = 6f, itemGap = 18f, legendW = itemGap * (legend.length - 1);
        p.setTextAlign(android.graphics.Paint.Align.LEFT);
        for (String item : legend) legendW += box + gap + p.measureText(item);
        float legendX = x + (w - legendW) / 2f;
        float legendY = y + h + 16;
        for (int i = 0; i < legend.length; i++) {
            p.setColor(colors[i]);
            c.drawRect(legendX, legendY - box + 1, legendX + box, legendY + 1, p);
            p.setColor(PDF_TEXT);
            c.drawText(legend[i], legendX + box + gap, legendY, p);
            legendX += box + gap + p.measureText(legend[i]) + itemGap;
        }
        if (n == 0) return;

        float slot = (plotR - plotL) / n;
        float barW = slot * (isMonthly ? 0.7f : 0.55f);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);

        for (int i = 0; i < n; i++) {
            float cx = plotL + slot * i + slot / 2f;
            float bottom = plotB;
            float[] stack = stackOf(stats.get(i));
            for (int k = 0; k < stack.length; k++) {
                if (stack[k] <= 0) continue;
                float top = bottom - plotH * stack[k] / 100f;
                p.setColor(colors[k]);
                c.drawRect(cx - barW / 2f, top, cx + barW / 2f, bottom, p);
                bottom = top;
            }

            int day = i + 1;
            if (!isMonthly || day == 1 || day % 5 == 0) {
                p.setTypeface(fontRegular());
                p.setTextSize(9f);
                p.setColor(PDF_MUTED);
                c.drawText(isMonthly ? String.valueOf(day) : labels.get(i), cx, plotB + 14, p);
            }
        }

        if (hasDue(stats)) {
            float avgY = plotB - plotH * combinedPercent(stats) / 100f;
            p.setColor(PDF_TEXT);
            p.setStrokeWidth(1f);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{5f, 4f}, 0f));
            c.drawLine(plotL, avgY, plotR, avgY, p);
            p.setPathEffect(null);
            p.setTextSize(9f);
            p.setTextAlign(android.graphics.Paint.Align.RIGHT);
            c.drawText(getString(R.string.rata_rata_singkat), plotR, avgY - 3, p);
        }
    }

    private void drawPdfDonut(Canvas c, float left, float top, float size) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        int[] values = {currentTotalDikonsumsi, currentTotalSnooze, currentTotalDiabaikan};
        int[] colors = {
                requireContext().getColor(R.color.green),
                requireContext().getColor(R.color.gray),
                requireContext().getColor(R.color.merah)
        };
        int total = values[0] + values[1] + values[2];

        float cx = left + size / 2f, cy = top + size / 2f;
        float outerR = size / 2f;
        float innerR = outerR * 0.55f;
        float ringR = (outerR + innerR) / 2f;
        float stroke = outerR - innerR;
        android.graphics.RectF oval = new android.graphics.RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR);

        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        if (total == 0) {
            p.setColor(PDF_TRACK);
            c.drawArc(oval, 0, 360, false, p);
        } else {
            float start = -90f;
            for (int i = 0; i < values.length; i++) {
                if (values[i] <= 0) continue;
                float sweep = 360f * values[i] / total;
                p.setColor(colors[i]);
                c.drawArc(oval, start, sweep, false, p);

                if (sweep >= 18f) {
                    double mid = Math.toRadians(start + sweep / 2f);
                    float tx = cx + (float) (ringR * Math.cos(mid));
                    float ty = cy + (float) (ringR * Math.sin(mid));
                    android.graphics.Paint t = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    t.setColor(Color.WHITE);
                    t.setTypeface(fontBold());
                    t.setTextSize(12f);
                    t.setTextAlign(android.graphics.Paint.Align.CENTER);
                    c.drawText(Math.round(values[i] * 100f / total) + "%", tx, ty - (t.descent() + t.ascent()) / 2f, t);
                }
                start += sweep;
            }
        }

        p.setStyle(android.graphics.Paint.Style.FILL);
        p.setColor(PDF_TEXT);
        p.setTypeface(fontBold());
        p.setTextSize(14f);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        c.drawText(getString(R.string.response_center_label), cx, cy - (p.descent() + p.ascent()) / 2f, p);
    }

    private String getReportPeriod() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        if (reportAnchorMillis > 0) calendar.setTimeInMillis(reportAnchorMillis);
        calendar.setFirstDayOfWeek(java.util.Calendar.MONDAY);

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

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
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
        btnWeekly.setChecked("weekly".equals(selectedPeriod));
        btnMonthly.setChecked("monthly".equals(selectedPeriod));
        btnYearly.setChecked("yearly".equals(selectedPeriod));
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
        new NotificationRepo(requireContext()).createReportNotif(user.getAuth_uid(), user.getCurrentRole(), "laporan_berhasil_diunduh",
                fileName, mediaStoreUri, legacyFilePath, new RepoCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {}

                    @Override
                    public void onFailure(Exception e) {}
                });
    }
    private void styleNoData(Chart<?> chart) {
        chart.setNoDataText(getString(R.string.noConsumptionData));
        chart.setNoDataTextColor(MaterialColors.getColor(requireView(),
                com.google.android.material.R.attr.colorOnSurface));
        chart.getPaint(Chart.PAINT_INFO).setTypeface(fontRegular());
    }
}