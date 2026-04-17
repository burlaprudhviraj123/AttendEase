package com.example.attendease.ui.student;

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

import com.example.attendease.databinding.FragmentSessionHistoryBinding;
import com.example.attendease.model.AttendanceSession;
import com.example.attendease.viewmodel.AuthViewModel;
import com.example.attendease.viewmodel.StudentViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionHistoryFragment extends Fragment {

    private FragmentSessionHistoryBinding binding;
    private AuthViewModel authViewModel;
    private StudentViewModel studentViewModel;
    private HistoryAdapter historyAdapter;
    private List<AttendanceSession> fullList = new ArrayList<>();
    private String filterSubject = null;     // null = all subjects
    private Boolean filterPresent = null;    // null = all statuses
    private String studentUid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSessionHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        studentViewModel = new ViewModelProvider(requireActivity()).get(StudentViewModel.class);

        if (authViewModel.getCurrentUserProfile().getValue() != null) {
            studentUid = authViewModel.getCurrentUserProfile().getValue().getInstitutionalId();
        }

        setupObservers();

        // Trigger load if data hasn't been loaded yet by the Home fragment
        if (studentViewModel.getAllSessions().getValue() == null ||
            studentViewModel.getAllSessions().getValue().isEmpty()) {
            if (studentUid != null) {
                studentViewModel.loadStudentData(studentUid);
            }
        }

        setupRecyclerView();
        setupFilters();
    }

    private void setupRecyclerView() {
        historyAdapter = new HistoryAdapter(new ArrayList<>());
        binding.historyRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.historyRecyclerView.setAdapter(historyAdapter);
    }

    private void setupFilters() {
        // Subject filter chips are populated once we have data
        binding.filterButton.setOnClickListener(v -> showFilterSheet());
    }

    private void setupObservers() {
        studentViewModel.getAllSessions().observe(getViewLifecycleOwner(), sessions -> {
            if (sessions != null) {
                fullList = sessions;
                applyFilters();
            }
        });
    }

    private void applyFilters() {
        List<AttendanceSession> filtered = new ArrayList<>();
        for (AttendanceSession s : fullList) {
            boolean matchSubject = filterSubject == null || filterSubject.equals(s.getSubject());
            boolean isPresent = s.getRecords() != null
                    && Boolean.TRUE.equals(s.getRecords().get(studentUid));
            boolean matchPresent = filterPresent == null || filterPresent == isPresent;
            if (matchSubject && matchPresent) filtered.add(s);
        }
        historyAdapter.updateItems(filtered, studentUid);
        updateFilterLabel();
    }

    private void updateFilterLabel() {
        List<String> parts = new ArrayList<>();
        if (filterSubject != null) parts.add(filterSubject);
        if (filterPresent != null) parts.add(filterPresent ? "Present" : "Absent");
        binding.activeFiltersText.setText(parts.isEmpty() ? "All sessions" : String.join(" · ", parts));
        binding.clearFiltersButton.setVisibility(parts.isEmpty() ? View.GONE : View.VISIBLE);
        binding.clearFiltersButton.setOnClickListener(v -> {
            filterSubject = null;
            filterPresent = null;
            applyFilters();
        });
    }

    private void showFilterSheet() {
        // Collect unique subjects
        List<String> subjects = new ArrayList<>();
        subjects.add("All Subjects");
        for (AttendanceSession s : fullList) {
            if (s.getSubject() != null && !subjects.contains(s.getSubject())) {
                subjects.add(s.getSubject());
            }
        }

        SessionFilterBottomSheet sheet = new SessionFilterBottomSheet(
                subjects, filterSubject, filterPresent,
                (selectedSubject, selectedPresence) -> {
                    filterSubject = "All Subjects".equals(selectedSubject) ? null : selectedSubject;
                    filterPresent = selectedPresence;
                    applyFilters();
                });
        sheet.show(getChildFragmentManager(), "filter");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ----------------------------------------------------------------
    // History adapter — groups by Today / Yesterday / date label
    // ----------------------------------------------------------------
    static class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        static class Row {
            int type;
            String label;          // for headers
            AttendanceSession s;   // for session rows
            boolean present;

            static Row header(String lbl) { Row r = new Row(); r.type = TYPE_HEADER; r.label = lbl; return r; }
            static Row session(AttendanceSession s, boolean p) { Row r = new Row(); r.type = TYPE_ITEM; r.s = s; r.present = p; return r; }
        }

        private List<Row> rows = new ArrayList<>();

        HistoryAdapter(List<AttendanceSession> initial) {}

        void updateItems(List<AttendanceSession> sessions, String studentUid) {
            rows.clear();
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DATE, -1);

            String lastLabel = null;
            for (AttendanceSession s : sessions) {
                String label;
                if (s.getDate() == null) {
                    label = "Earlier";
                } else {
                    Calendar sc = Calendar.getInstance();
                    sc.setTimeInMillis(s.getDate().toDate().getTime());
                    if (isSameDay(sc, today)) label = "Today";
                    else if (isSameDay(sc, yesterday)) label = "Yesterday";
                    else label = sdf.format(s.getDate().toDate());
                }
                if (!label.equals(lastLabel)) {
                    rows.add(Row.header(label));
                    lastLabel = label;
                }
                boolean present = s.getRecords() != null && Boolean.TRUE.equals(s.getRecords().get(studentUid));
                rows.add(Row.session(s, present));
            }
            notifyDataSetChanged();
        }

        private boolean isSameDay(Calendar a, Calendar b) {
            return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                    && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        }

        @Override public int getItemViewType(int pos) { return rows.get(pos).type; }
        @Override public int getItemCount() { return rows.size(); }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater li = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                View v = li.inflate(com.example.attendease.R.layout.item_history_header, parent, false);
                return new HeaderVH(v);
            } else {
                View v = li.inflate(com.example.attendease.R.layout.item_history_session, parent, false);
                return new SessionVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            Row row = rows.get(pos);
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).labelText.setText(row.label);
            } else {
                SessionVH h = (SessionVH) holder;
                h.subjectText.setText(row.s.getSubject());
                h.dot.setBackgroundResource(row.present
                        ? com.example.attendease.R.drawable.bg_dot_present
                        : com.example.attendease.R.drawable.bg_dot_absent);
                h.statusText.setText(row.present ? "Present" : "Absent");
                h.statusText.setTextColor(h.itemView.getContext().getColor(
                        row.present ? com.example.attendease.R.color.success
                                : com.example.attendease.R.color.error));
            }
        }

        static class HeaderVH extends RecyclerView.ViewHolder {
            TextView labelText;
            HeaderVH(@NonNull View v) { super(v); labelText = v.findViewById(com.example.attendease.R.id.history_header_text); }
        }
        static class SessionVH extends RecyclerView.ViewHolder {
            TextView subjectText, statusText; View dot;
            SessionVH(@NonNull View v) {
                super(v);
                subjectText = v.findViewById(com.example.attendease.R.id.history_subject_text);
                statusText = v.findViewById(com.example.attendease.R.id.history_status_text);
                dot = v.findViewById(com.example.attendease.R.id.history_dot);
            }
        }
    }
}
