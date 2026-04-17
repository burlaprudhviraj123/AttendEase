package com.example.attendease.ui.student;

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
import com.example.attendease.databinding.FragmentAttendanceBinding;
import com.example.attendease.model.AttendanceSession;
import com.example.attendease.model.User;
import com.example.attendease.viewmodel.AuthViewModel;
import com.example.attendease.viewmodel.StudentViewModel;
import com.google.android.material.tabs.TabLayout;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AttendanceFragment extends Fragment {

    private FragmentAttendanceBinding binding;
    private AuthViewModel authViewModel;
    private StudentViewModel studentViewModel;
    private String currentStudentId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAttendanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        studentViewModel = new ViewModelProvider(requireActivity()).get(StudentViewModel.class);

        if (authViewModel.getCurrentUserProfile().getValue() != null) {
            User user = authViewModel.getCurrentUserProfile().getValue();
            currentStudentId = (user.getRollNo() != null && !user.getRollNo().isEmpty()) 
                    ? user.getRollNo() 
                    : user.getUid();
        }

        setupObservers();
        setupSubjectList();
        
        // Trigger load if data hasn't been loaded yet by the Home fragment
        List<CourseAttendanceAdapter.CourseAttendance> existing = studentViewModel.getCourseAttendanceList().getValue();
        if ((existing == null || existing.isEmpty()) && currentStudentId != null) {
            // We use the raw UID for loading student data (as it's the document key in Firestore)
            studentViewModel.loadStudentData(authViewModel.getCurrentUserProfile().getValue().getUid());
        }

        if (binding.calculatorButton != null) {
            binding.calculatorButton.setOnClickListener(v -> 
                new ClassesNeededBottomSheet().show(getChildFragmentManager(), "calc"));
        }
    }

    private void setupObservers() {
        // Subject-level attendance
        studentViewModel.getCourseAttendanceList().observe(getViewLifecycleOwner(), courses -> {
            if (courses != null) {
                updateChart(courses);
                SubjectListAdapter adapter = new SubjectListAdapter(courses, subject ->
                        showSessionLog(subject));
                binding.subjectRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                binding.subjectRecyclerView.setAdapter(adapter);
            }
        });
    }

    private void updateChart(List<CourseAttendanceAdapter.CourseAttendance> courses) {
        if (binding.attendanceBarChart == null || courses.isEmpty()) return;

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        Context context = getContext();
        if (context == null) return;
        
        int labelColor = context.getColor(R.color.on_surface);
        int gridColor = context.getColor(R.color.outline_variant);

        for (int i = 0; i < courses.size(); i++) {
            CourseAttendanceAdapter.CourseAttendance c = courses.get(i);
            entries.add(new BarEntry(i, c.getPercentage()));
            labels.add(getShortName(c.getName()));
            
            int color = c.getPercentage() >= 85
                    ? context.getColor(R.color.success)
                    : (c.getPercentage() >= 75
                    ? context.getColor(R.color.primary)
                    : context.getColor(R.color.error));
            colors.add(color);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Attendance %");
        dataSet.setColors(colors);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(labelColor);

        BarData data = new BarData(dataSet);
        binding.attendanceBarChart.setData(data);

        // Styling
        binding.attendanceBarChart.getDescription().setEnabled(false);
        binding.attendanceBarChart.getLegend().setEnabled(false);
        binding.attendanceBarChart.setDrawGridBackground(false);
        binding.attendanceBarChart.setFitBars(true);
        binding.attendanceBarChart.getPaint(BarChart.PAINT_INFO).setColor(labelColor);

        // X Axis
        XAxis xAxis = binding.attendanceBarChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(labels.size());
        xAxis.setLabelRotationAngle(-45); // Set diagonal rotation for readability
        xAxis.setTextColor(labelColor);
        xAxis.setAxisLineColor(gridColor);

        // Y Axis
        YAxis leftAxis = binding.attendanceBarChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(100f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(gridColor);
        leftAxis.setTextColor(labelColor);
        leftAxis.setAxisLineColor(gridColor);

        binding.attendanceBarChart.getAxisRight().setEnabled(false);

        binding.attendanceBarChart.animateY(1000);
        binding.attendanceBarChart.invalidate();
    }

    private String getShortName(String full) {
        if (full == null) return "???";
        String s = full.toLowerCase();
        String res = "";
        
        if (s.contains("numerical ability")) res = "NA";
        else if (s.contains("professional communication")) res = "PC";
        else if (s.contains("probability") || s.contains("p&s")) res = "PS";
        else if (s.contains("computer organization") || s.contains("mini")) res = "COMPI";
        else if (s.contains("full stack")) res = "FS";
        else if (s.contains("database") || s.contains("dbms")) res = "DBMS";
        else if (s.contains("formal language") || s.contains("flat")) res = "FLAT";
        else if (s.contains("analysis of algorithms") || s.contains("daa")) res = "DAA";
        else if (s.contains("mobile application") || s.contains("mad")) res = "MAD";
        else if (s.contains("entrepreneurship") || s.contains("edipr")) res = "EDIPR";
        else if (s.contains("training")) res = "TRAIN";
        else {
            // Default to first letters if unknown
            String[] parts = full.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) if (!p.isEmpty()) sb.append(p.charAt(0));
            res = sb.toString().toUpperCase();
        }

        if (s.contains("lab")) {
            res += "L";
        }
        return res;
    }

    private void setupSubjectList() {
        binding.subjectRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void showSessionLog(String subject) {
        List<AttendanceSession> all = studentViewModel.getAllSessions().getValue();
        if (all == null || currentStudentId == null) return;

        List<SessionLogItem> log = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        for (AttendanceSession s : all) {
            if (subject.equals(s.getSubject())) {
                boolean present = s.getRecords() != null &&
                        Boolean.TRUE.equals(s.getRecords().get(currentStudentId));
                String date = s.getDate() != null ? sdf.format(s.getDate().toDate()) : "—";
                log.add(new SessionLogItem(date, present));
            }
        }

        binding.sessionLogTitle.setVisibility(View.VISIBLE);
        binding.sessionLogTitle.setText(subject + " — Session Log");
        binding.sessionLogRecyclerView.setVisibility(View.VISIBLE);
        binding.sessionLogRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.sessionLogRecyclerView.setAdapter(new SessionLogAdapter(log));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public static class SessionLogItem {
        public final String date;
        public final boolean present;
        public SessionLogItem(String date, boolean present) {
            this.date = date;
            this.present = present;
        }
    }

    static class SubjectListAdapter extends RecyclerView.Adapter<SubjectListAdapter.VH> {
        interface OnSubjectClick { void onClick(String subject); }
        private final List<CourseAttendanceAdapter.CourseAttendance> courses;
        private final OnSubjectClick listener;
        SubjectListAdapter(List<CourseAttendanceAdapter.CourseAttendance> c, OnSubjectClick l) {
            this.courses = c; this.listener = l;
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext())
                    .inflate(com.example.attendease.R.layout.item_subject_row, p, false);
            return new VH(view);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            CourseAttendanceAdapter.CourseAttendance c = courses.get(pos);
            h.subjectText.setText(c.getName());
            h.facultyText.setText(c.getFaculty());
            h.pctText.setText(c.getPercentage() + "%");
            h.progressBar.setProgress(c.getPercentage());
            int color = c.getPercentage() >= 85
                    ? com.example.attendease.R.color.success
                    : (c.getPercentage() >= 75
                    ? com.example.attendease.R.color.primary
                    : com.example.attendease.R.color.error);
            h.pctText.setTextColor(h.itemView.getContext().getColor(color));
            h.progressBar.setIndicatorColor(h.itemView.getContext().getColor(color));
            h.itemView.setOnClickListener(v -> listener.onClick(c.getName()));
        }
        @Override public int getItemCount() { return courses.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView subjectText, facultyText, pctText;
            com.google.android.material.progressindicator.LinearProgressIndicator progressBar;
            VH(@NonNull View v) {
                super(v);
                subjectText = v.findViewById(com.example.attendease.R.id.subject_name_text);
                facultyText = v.findViewById(com.example.attendease.R.id.faculty_name_text);
                pctText = v.findViewById(com.example.attendease.R.id.subject_pct_text);
                progressBar = v.findViewById(com.example.attendease.R.id.subject_progress_bar);
            }
        }
    }

    static class SessionLogAdapter extends RecyclerView.Adapter<SessionLogAdapter.VH> {
        private final List<SessionLogItem> items;
        SessionLogAdapter(List<SessionLogItem> items) { this.items = items; }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext())
                    .inflate(com.example.attendease.R.layout.item_session_log_row, p, false);
            return new VH(view);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SessionLogItem item = items.get(pos);
            h.dateText.setText(item.date);
            h.statusDot.setBackgroundResource(item.present
                    ? com.example.attendease.R.drawable.bg_dot_present
                    : com.example.attendease.R.drawable.bg_dot_absent);
            h.statusText.setText(item.present ? "Present" : "Absent");
            h.statusText.setTextColor(h.itemView.getContext().getColor(
                    item.present ? com.example.attendease.R.color.success
                            : com.example.attendease.R.color.error));
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView dateText, statusText; View statusDot;
            VH(@NonNull View v) {
                super(v);
                dateText = v.findViewById(com.example.attendease.R.id.sl_date_text);
                statusText = v.findViewById(com.example.attendease.R.id.sl_status_text);
                statusDot = v.findViewById(com.example.attendease.R.id.sl_dot);
            }
        }
    }
}
