package com.example.attendease.ui.faculty;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendease.R;
import com.example.attendease.databinding.FragmentAnalyticsDashboardBinding;
import com.example.attendease.model.AttendanceSession;
import com.example.attendease.repository.FacultyRepository;
import com.example.attendease.viewmodel.AuthViewModel;
import com.example.attendease.viewmodel.FacultyViewModel;
import com.google.android.material.chip.Chip;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnalyticsDashboardFragment extends Fragment {

    private FragmentAnalyticsDashboardBinding binding;
    private AuthViewModel authViewModel;
    private FacultyViewModel facultyViewModel;
    private FacultyRepository repository;
    private String selectedClassId = null;
    private String facultyUid;
    private java.util.Set<String> facultySubjects = new java.util.HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAnalyticsDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        facultyViewModel = new ViewModelProvider(requireActivity()).get(FacultyViewModel.class);
        repository = new FacultyRepository();

        if (authViewModel.getCurrentUserProfile().getValue() != null) {
            facultyUid = authViewModel.getCurrentUserProfile().getValue().getInstitutionalId();
        }

        loadAnalytics();
    }

    private void loadAnalytics() {
        if (facultyUid == null) return;
        binding.loadingIndicator.setVisibility(View.VISIBLE);

        repository.getAllTimetableForFaculty(facultyUid).addOnCompleteListener(task -> {
            if (binding == null) return;
            if (task.isSuccessful() && task.getResult() != null) {
                java.util.Set<String> classIds = new java.util.HashSet<>();
                facultySubjects.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult().getDocuments()) {
                    String cid = doc.getString("classId");
                    String subj = doc.getString("subject");
                    if (cid != null) classIds.add(cid);
                    if (subj != null) facultySubjects.add(subj);
                }

                if (classIds.isEmpty()) {
                    binding.loadingIndicator.setVisibility(View.GONE);
                    return;
                }

                List<com.example.attendease.model.ClassModel> loadedClasses = new ArrayList<>();
                int[] remaining = {classIds.size()};

                for (String classId : classIds) {
                    repository.getClassById(classId).addOnCompleteListener(classTask -> {
                        if (binding == null) return;
                        if (classTask.isSuccessful() && classTask.getResult() != null && classTask.getResult().exists()) {
                            com.example.attendease.model.ClassModel cls = classTask.getResult().toObject(com.example.attendease.model.ClassModel.class);
                            if (cls != null) loadedClasses.add(cls);
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            binding.loadingIndicator.setVisibility(View.GONE);
                            loadedClasses.sort((a, b) -> a.getName().compareTo(b.getName()));
                            populateClassFilters(loadedClasses);
                        }
                    });
                }
            } else {
                binding.loadingIndicator.setVisibility(View.GONE);
            }
        });
    }

    private void populateClassFilters(List<com.example.attendease.model.ClassModel> classes) {
        binding.classFilterChipGroup.removeAllViews();
        for (com.example.attendease.model.ClassModel cls : classes) {
            Chip chip = new Chip(requireContext());
            chip.setText(cls.getName());
            chip.setCheckable(true);
            chip.setChecked(cls.getClassId().equals(selectedClassId));
            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    selectedClassId = cls.getClassId();
                    loadClassAnalytics(cls.getClassId(), cls.getName());
                }
            });
            binding.classFilterChipGroup.addView(chip);
        }

        if (!classes.isEmpty()) {
            selectedClassId = classes.get(0).getClassId();
            ((Chip) binding.classFilterChipGroup.getChildAt(0)).setChecked(true);
            loadClassAnalytics(classes.get(0).getClassId(), classes.get(0).getName());
        }
    }

    private void loadClassAnalytics(String classId, String className) {
        binding.analyticsTitle.setText("Faculty Analytics — " + className);
        binding.subjectBarsContainer.removeAllViews();
        binding.atRiskRecyclerView.setVisibility(View.GONE);

        repository.getSessionsForClass(classId).addOnCompleteListener(task -> {
            if (binding == null) return;
            if (!task.isSuccessful() || task.getResult() == null) return;
            
            // Filter sessions by THIS faculty only (matching institutional ID)
            final String currentFacultyUid = facultyUid;
            List<AttendanceSession> sessions = task.getResult().toObjects(AttendanceSession.class)
                    .stream()
                    .filter(s -> currentFacultyUid != null && currentFacultyUid.equals(s.getMarkedBy()))
                    .collect(Collectors.toList());

            if (sessions.isEmpty()) {
                binding.atRiskLabel.setText("No sessions conducted by you yet.");
                binding.atRiskLabel.setVisibility(View.VISIBLE);
                resetCharts();
                return;
            }

            // Compute per-subject stats (only for subjects THIS faculty taught)
            Map<String, int[]> subjectStats = new HashMap<>(); 
            Map<String, int[]> studentStats = new HashMap<>(); 

            for (AttendanceSession s : sessions) {
                String subj = s.getSubject() != null ? s.getSubject() : "Unknown";
                int[] sa = subjectStats.getOrDefault(subj, new int[]{0, 0});
                if (s.getRecords() != null) {
                    for (Map.Entry<String, Boolean> e : s.getRecords().entrySet()) {
                        sa[0]++; // total opportunities
                        if (Boolean.TRUE.equals(e.getValue())) sa[1]++; // attended
                        
                        // per student
                        int[] ss2 = studentStats.getOrDefault(e.getKey(), new int[]{0, 0});
                        ss2[0]++;
                        if (Boolean.TRUE.equals(e.getValue())) ss2[1]++;
                        studentStats.put(e.getKey(), ss2);
                    }
                }
                subjectStats.put(subj, sa);
            }

            // Display Summary
            binding.totalSessionsValue.setText(String.valueOf(sessions.size()));
            int sumPct = 0, count = 0;
            int low = 0, mid = 0, high = 0;

            for (int[] vals : studentStats.values()) {
                if (vals[0] > 0) { 
                    int pct = (vals[1] * 100 / vals[0]);
                    sumPct += pct; 
                    count++; 
                    if (pct < 75) low++; else if (pct < 85) mid++; else high++;
                }
            }
            binding.avgAttendanceValueDash.setText(count > 0 ? (sumPct / count) + "%" : "—");
            binding.atRiskValue.setText(String.valueOf(low));

            updatePieChart(low, mid, high);

            // Subject detail bars
            for (Map.Entry<String, int[]> e : subjectStats.entrySet()) {
                int[] v = e.getValue();
                int pct = v[0] > 0 ? (v[1] * 100 / v[0]) : 0;
                addSubjectBar(e.getKey(), pct);
            }

            // Tie-break sorting (was ASC by % then Roll, we keep that for both lists)
            List<String> allStudents = new ArrayList<>(studentStats.keySet());
            allStudents.sort((a, b) -> {
                int pctA = studentStats.get(a)[0] > 0 ? (studentStats.get(a)[1] * 100 / studentStats.get(a)[0]) : 0;
                int pctB = studentStats.get(b)[0] > 0 ? (studentStats.get(b)[1] * 100 / studentStats.get(b)[0]) : 0;
                int cmp = Integer.compare(pctA, pctB);
                if (cmp == 0) return a.compareTo(b);
                return cmp;
            });

            // Partition into Safe vs At Risk for App UI
            List<String> appSafe = new ArrayList<>();
            List<String> appRisk = new ArrayList<>();
            for (String uid : allStudents) {
                int[] v = studentStats.get(uid);
                int pct = (v != null && v[0] > 0) ? (v[1] * 100 / v[0]) : 0;
                if (pct < 75) appRisk.add(uid); else appSafe.add(uid);
            }

            // Safe List UI
            if (!appSafe.isEmpty()) {
                binding.safeHeader.setVisibility(View.VISIBLE);
                // Default: Collapsed
                binding.safeStudentsRecyclerView.setVisibility(View.GONE);
                binding.safeArrow.setRotation(0);
                
                binding.safeHeader.setOnClickListener(v -> {
                    boolean show = binding.safeStudentsRecyclerView.getVisibility() == View.GONE;
                    binding.safeStudentsRecyclerView.setVisibility(show ? View.VISIBLE : View.GONE);
                    binding.safeArrow.animate().rotation(show ? 180 : 0).setDuration(200).start();
                });

                binding.safeStudentsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                binding.safeStudentsRecyclerView.setAdapter(new StudentAnalysisAdapter(appSafe, studentStats));
            } else {
                binding.safeHeader.setVisibility(View.GONE);
                binding.safeStudentsRecyclerView.setVisibility(View.GONE);
            }

            // At Risk List UI
            if (!appRisk.isEmpty()) {
                binding.atRiskHeader.setVisibility(View.VISIBLE);
                // Default: Expanded for visibility
                binding.atRiskRecyclerView.setVisibility(View.VISIBLE);
                binding.atRiskArrow.setRotation(180);

                binding.atRiskHeader.setOnClickListener(v -> {
                    boolean show = binding.atRiskRecyclerView.getVisibility() == View.GONE;
                    binding.atRiskRecyclerView.setVisibility(show ? View.VISIBLE : View.GONE);
                    binding.atRiskArrow.animate().rotation(show ? 180 : 0).setDuration(200).start();
                });

                binding.atRiskRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                binding.atRiskRecyclerView.setAdapter(new StudentAnalysisAdapter(appRisk, studentStats));
            } else {
                binding.atRiskHeader.setVisibility(View.GONE);
                binding.atRiskRecyclerView.setVisibility(View.GONE);
            }

            // PDF Export Options
            if (binding.exportClassPdfButton != null) {
                final String finalAvg = count > 0 ? (sumPct / count) + "%" : "—";
                final int finalSize = sessions.size();
                binding.exportClassPdfButton.setOnClickListener(v -> 
                    exportToPdf(className, finalSize, finalAvg, subjectStats, allStudents, studentStats)
                );
            }

            if (binding.exportSubjectPdfButton != null) {
                binding.exportSubjectPdfButton.setOnClickListener(v -> {
                    if (subjectStats.isEmpty()) return;
                    String[] subjects = subjectStats.keySet().toArray(new String[0]);
                    if (subjects.length == 1) {
                         exportSubjectToPdf(className, subjects[0], sessions, allStudents, studentStats);
                    } else {
                        Context context = getContext();
                        if (context == null) return;
                        new android.app.AlertDialog.Builder(context)
                            .setTitle("Select Subject")
                            .setItems(subjects, (d, which) -> 
                                exportSubjectToPdf(className, subjects[which], sessions, allStudents, studentStats))
                            .show();
                    }
                });
            }
        });
    }

    private void updatePieChart(int low, int mid, int high) {
        if (binding.attendancePieChart == null) return;
        Context context = getContext();
        if (context == null) return;

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (high > 0) {
            entries.add(new PieEntry(high, "Safe (≥85%)"));
            colors.add(context.getColor(R.color.success));
        }
        if (mid > 0) {
            entries.add(new PieEntry(mid, "Average (75-84%)"));
            colors.add(context.getColor(R.color.warning));
        }
        if (low > 0) {
            entries.add(new PieEntry(low, "At Risk (<75%)"));
            colors.add(context.getColor(R.color.error));
        }

        if (entries.isEmpty()) {
            binding.attendancePieChart.clear();
            binding.attendancePieChart.setVisibility(View.GONE);
            return;
        }

        binding.attendancePieChart.setVisibility(View.VISIBLE);

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(colors);
        set.setValueTextSize(12f);
        set.setValueTextColor(context.getColor(R.color.white));
        set.setSliceSpace(2f);

        PieData data = new PieData(set);
        data.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        binding.attendancePieChart.setData(data);
        binding.attendancePieChart.getDescription().setEnabled(false);
        binding.attendancePieChart.setCenterText("Class Health");
        binding.attendancePieChart.setCenterTextSize(14f);
        binding.attendancePieChart.setHoleRadius(40f);
        binding.attendancePieChart.setTransparentCircleRadius(45f);
        binding.attendancePieChart.setEntryLabelTextSize(10f);
        binding.attendancePieChart.getLegend().setEnabled(true);
        binding.attendancePieChart.animateY(1000);
        binding.attendancePieChart.invalidate();
    }

    private void resetCharts() {
        binding.subjectBarsContainer.removeAllViews();
        binding.totalSessionsValue.setText("0");
        binding.avgAttendanceValueDash.setText("—");
        binding.atRiskValue.setText("0");
    }

    private void exportToPdf(String className, int totalSessions, String avgAttendance, 
                             Map<String, int[]> subjectStats, List<String> atRisk, Map<String, int[]> studentStats) {
        Context context = getContext();
        if (context == null) return;
        try {
            String fileName = "AttendEase_Report_" + className.replace(" ", "_") + "_" + System.currentTimeMillis() + ".pdf";
            java.io.OutputStream outputStream;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/AttendEase");
                android.net.Uri uri = context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                outputStream = context.getContentResolver().openOutputStream(uri);
            } else {
                java.io.File path = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "AttendEase");
                if (!path.exists()) path.mkdirs();
                java.io.File file = new java.io.File(path, fileName);
                outputStream = new java.io.FileOutputStream(file);
            }

            com.itextpdf.text.Document document = new com.itextpdf.text.Document();
            com.itextpdf.text.pdf.PdfWriter.getInstance(document, outputStream);
            document.open();

            // Title
            com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 18, com.itextpdf.text.Font.BOLD);
            document.add(new com.itextpdf.text.Paragraph("AttendEase Class Report: " + className, titleFont));
            document.add(new com.itextpdf.text.Paragraph("Generated on: " + new java.util.Date().toString() + "\n\n"));

            // Summary
            document.add(new com.itextpdf.text.Paragraph("Total Sessions: " + totalSessions));
            document.add(new com.itextpdf.text.Paragraph("Average Class Attendance: " + avgAttendance + "\n\n"));

            // Subjects Table
            com.itextpdf.text.Font tableHeaderFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 12, com.itextpdf.text.Font.BOLD);
            document.add(new com.itextpdf.text.Paragraph("Subject Performance", tableHeaderFont));
            com.itextpdf.text.pdf.PdfPTable subjectTable = new com.itextpdf.text.pdf.PdfPTable(3);
            subjectTable.setWidthPercentage(100);
            subjectTable.setSpacingBefore(10f);
            subjectTable.addCell("Subject");
            subjectTable.addCell("Sessions");
            subjectTable.addCell("Attendance %");

            for (Map.Entry<String, int[]> e : subjectStats.entrySet()) {
                int[] v = e.getValue();
                int pct = v[0] > 0 ? (v[1] * 100 / v[0]) : 0;
                subjectTable.addCell(e.getKey());
                subjectTable.addCell(String.valueOf(v[0]));
                subjectTable.addCell(pct + "%");
            }
            document.add(subjectTable);
            document.add(new com.itextpdf.text.Paragraph("\n"));

            // Partition students
            List<String> atRiskList = new ArrayList<>();
            List<String> safeList = new ArrayList<>();
            for (String uid : atRisk) { // studentUids passed as 'atRisk' arg (was renamed in previous step)
                int[] v = studentStats.get(uid);
                int pct = (v != null && v[0] > 0) ? (v[1] * 100 / v[0]) : 0;
                if (pct < 75) atRiskList.add(uid); else safeList.add(uid);
            }

            // Safe Students Table
            if (!safeList.isEmpty()) {
                document.add(new com.itextpdf.text.Paragraph("Safe Students (>=75%)", tableHeaderFont));
                com.itextpdf.text.pdf.PdfPTable safeTable = new com.itextpdf.text.pdf.PdfPTable(2);
                safeTable.setWidthPercentage(100);
                safeTable.setSpacingBefore(10f);
                safeTable.addCell("Roll Number / ID");
                safeTable.addCell("Overall %");
                for (String uid : safeList) {
                    int[] v = studentStats.get(uid);
                    int pct = v != null && v[0] > 0 ? (v[1] * 100 / v[0]) : 0;
                    safeTable.addCell(uid);
                    safeTable.addCell(pct + "%");
                }
                document.add(safeTable);
                document.add(new com.itextpdf.text.Paragraph("\n"));
            }

            // At Risk Students Table
            if (!atRiskList.isEmpty()) {
                document.add(new com.itextpdf.text.Paragraph("Students At Risk (<75%)", tableHeaderFont));
                com.itextpdf.text.pdf.PdfPTable riskTable = new com.itextpdf.text.pdf.PdfPTable(2);
                riskTable.setWidthPercentage(100);
                riskTable.setSpacingBefore(10f);
                riskTable.addCell("Roll Number / ID");
                riskTable.addCell("Overall %");
                for (String uid : atRiskList) {
                    int[] v = studentStats.get(uid);
                    int pct = v != null && v[0] > 0 ? (v[1] * 100 / v[0]) : 0;
                    riskTable.addCell(uid);
                    riskTable.addCell(pct + "%");
                }
                document.add(riskTable);
            }

            document.close();
            android.widget.Toast.makeText(context, "Class Report exported successfully!", android.widget.Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(context, "Error exporting Class PDF", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void exportSubjectToPdf(String className, String subject, List<AttendanceSession> allSessions,
                                     List<String> studentUids, Map<String, int[]> classStudentStats) {
        Context context = getContext();
        if (context == null) return;
        try {
            java.util.List<AttendanceSession> subjectSessions = allSessions.stream()
                    .filter(s -> subject.equals(s.getSubject()))
                    .collect(java.util.stream.Collectors.toList());

            // Compute subject-specific stats for all students
            Map<String, int[]> subStats = new HashMap<>();
            for (String uid : studentUids) subStats.put(uid, new int[]{0, 0});

            for (AttendanceSession s : subjectSessions) {
                if (s.getRecords() != null) {
                    for (Map.Entry<String, Boolean> e : s.getRecords().entrySet()) {
                        int[] val = subStats.getOrDefault(e.getKey(), new int[]{0, 0});
                        val[0]++; if (Boolean.TRUE.equals(e.getValue())) val[1]++;
                        subStats.put(e.getKey(), val);
                    }
                }
            }

            String fileName = "AttendEase_Subject_" + subject.replace(" ", "_") + "_" + System.currentTimeMillis() + ".pdf";
            java.io.OutputStream outputStream;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/AttendEase");
                android.net.Uri uri = requireContext().getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                outputStream = requireContext().getContentResolver().openOutputStream(uri);
            } else {
                java.io.File path = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "AttendEase");
                if (!path.exists()) path.mkdirs();
                java.io.File file = new java.io.File(path, fileName);
                outputStream = new java.io.FileOutputStream(file);
            }

            com.itextpdf.text.Document document = new com.itextpdf.text.Document();
            com.itextpdf.text.pdf.PdfWriter.getInstance(document, outputStream);
            document.open();

            com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 18, com.itextpdf.text.Font.BOLD);
            document.add(new com.itextpdf.text.Paragraph("Subject Analysis: " + subject, titleFont));
            document.add(new com.itextpdf.text.Paragraph("Class: " + className));
            document.add(new com.itextpdf.text.Paragraph("Sessions Conducted: " + subjectSessions.size()));
            document.add(new com.itextpdf.text.Paragraph("Report Date: " + new java.util.Date().toString() + "\n\n"));

            // Partition students based on this subject's performance
            List<String> subSafe = new ArrayList<>();
            List<String> subRisk = new ArrayList<>();
            for (String uid : studentUids) {
                int[] v = subStats.get(uid);
                int pct = (v != null && v[0] > 0) ? (v[1] * 100 / v[0]) : 0;
                if (pct < 75) subRisk.add(uid); else subSafe.add(uid);
            }

            com.itextpdf.text.Font headerFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 12, com.itextpdf.text.Font.BOLD);

            // Safe Table
            if (!subSafe.isEmpty()) {
                document.add(new com.itextpdf.text.Paragraph("Safe Students (>=75%)", headerFont));
                com.itextpdf.text.pdf.PdfPTable table = new com.itextpdf.text.pdf.PdfPTable(3);
                table.setWidthPercentage(100);
                table.setSpacingBefore(10f);
                table.addCell("Roll Number / ID");
                table.addCell("Sessions Attended");
                table.addCell("Attendance %");
                for (String uid : subSafe) {
                    int[] v = subStats.get(uid);
                    int pct = (v != null && v[0] > 0) ? (v[1] * 100 / v[0]) : 0;
                    table.addCell(uid);
                    table.addCell((v != null ? v[1] : 0) + " / " + (v != null ? v[0] : 0));
                    table.addCell(pct + "%");
                }
                document.add(table);
                document.add(new com.itextpdf.text.Paragraph("\n"));
            }

            // At Risk Table
            if (!subRisk.isEmpty()) {
                document.add(new com.itextpdf.text.Paragraph("Students At Risk (<75%)", headerFont));
                com.itextpdf.text.pdf.PdfPTable riskTable = new com.itextpdf.text.pdf.PdfPTable(3);
                riskTable.setWidthPercentage(100);
                riskTable.setSpacingBefore(10f);
                riskTable.addCell("Roll Number / ID");
                riskTable.addCell("Sessions Attended");
                riskTable.addCell("Attendance %");
                for (String uid : subRisk) {
                    int[] v = subStats.get(uid);
                    int pct = (v != null && v[0] > 0) ? (v[1] * 100 / v[0]) : 0;
                    riskTable.addCell(uid);
                    riskTable.addCell((v != null ? v[1] : 0) + " / " + (v != null ? v[0] : 0));
                    riskTable.addCell(pct + "%");
                }
                document.add(riskTable);
            }

            document.close();
            android.widget.Toast.makeText(context, "Subject Report exported successfully!", android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(context, "Error exporting Subject PDF", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void addSubjectBar(String subject, int pct) {
        View row = LayoutInflater.from(getContext())
                .inflate(com.example.attendease.R.layout.item_subject_bar, binding.subjectBarsContainer, false);
        ((TextView) row.findViewById(com.example.attendease.R.id.bar_subject_text)).setText(subject);
        ((TextView) row.findViewById(com.example.attendease.R.id.bar_pct_text)).setText(pct + "%");
        ((com.google.android.material.progressindicator.LinearProgressIndicator)
                row.findViewById(com.example.attendease.R.id.bar_progress)).setProgress(pct);
        int colorRes = pct >= 85
                ? com.example.attendease.R.color.success
                : (pct >= 75 ? com.example.attendease.R.color.primary
                : com.example.attendease.R.color.error);
        
        Context context = getContext();
        if (context != null) {
            ((com.google.android.material.progressindicator.LinearProgressIndicator)
                    row.findViewById(com.example.attendease.R.id.bar_progress))
                    .setIndicatorColor(context.getColor(colorRes));
        }
        binding.subjectBarsContainer.addView(row);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // Student Analysis adapter
    static class StudentAnalysisAdapter extends RecyclerView.Adapter<StudentAnalysisAdapter.VH> {
        private final List<String> uids;
        private final Map<String, int[]> stats;
        StudentAnalysisAdapter(List<String> uids, Map<String, int[]> stats) { this.uids = uids; this.stats = stats; }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext())
                    .inflate(com.example.attendease.R.layout.item_at_risk_student, p, false);
            return new VH(view);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            String uid = uids.get(pos);
            int[] v = stats.getOrDefault(uid, new int[]{0, 0});
            int pct = v[0] > 0 ? (v[1] * 100 / v[0]) : 0;
            h.uidText.setText(uid);
            h.pctText.setText(pct + "%");
            int colorRes = pct >= 75 ? com.example.attendease.R.color.primary : com.example.attendease.R.color.error;
            h.pctText.setTextColor(h.itemView.getContext().getColor(colorRes));
        }
        @Override public int getItemCount() { return uids.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView uidText, pctText;
            VH(@NonNull View v) { super(v); uidText = v.findViewById(com.example.attendease.R.id.ar_uid_text); pctText = v.findViewById(com.example.attendease.R.id.ar_pct_text); }
        }
    }
}
