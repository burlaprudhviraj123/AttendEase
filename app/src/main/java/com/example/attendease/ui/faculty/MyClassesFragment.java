package com.example.attendease.ui.faculty;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendease.R;
import com.example.attendease.databinding.FragmentMyClassesBinding;
import com.example.attendease.model.AttendanceSession;
import com.example.attendease.model.ClassModel;
import com.example.attendease.repository.FacultyRepository;
import com.example.attendease.viewmodel.AuthViewModel;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MyClassesFragment extends Fragment {

    private FragmentMyClassesBinding binding;
    private AuthViewModel authViewModel;
    private FacultyRepository facultyRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMyClassesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        facultyRepository = new FacultyRepository();

        loadClasses();
    }

    private void loadClasses() {
        if (authViewModel.getCurrentUserProfile().getValue() == null) return;
        String uid = authViewModel.getCurrentUserProfile().getValue().getInstitutionalId();

        binding.loadingIndicator.setVisibility(View.VISIBLE);
        facultyRepository.getAllTimetableForFaculty(uid).addOnCompleteListener(task -> {
            if (binding == null) return;
            if (task.isSuccessful() && task.getResult() != null) {
                java.util.Set<String> classIds = new java.util.HashSet<>();
                java.util.Map<String, java.util.Set<String>> classSubjects = new java.util.HashMap<>();

                for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult().getDocuments()) {
                    String cid = doc.getString("classId");
                    String subj = doc.getString("subject");
                    if (cid != null && subj != null) {
                        classIds.add(cid);
                        classSubjects.computeIfAbsent(cid, k -> new java.util.HashSet<>()).add(subj);
                    }
                }

                if (classIds.isEmpty()) {
                    binding.loadingIndicator.setVisibility(View.GONE);
                    populateClasses(new ArrayList<>());
                    return;
                }

                List<ClassModel> loadedClasses = new ArrayList<>();
                int[] remaining = {classIds.size()};

                for (String classId : classIds) {
                    facultyRepository.getClassById(classId).addOnCompleteListener(classTask -> {
                        if (binding == null) return;
                        if (classTask.isSuccessful() && classTask.getResult() != null && classTask.getResult().exists()) {
                            ClassModel cls = classTask.getResult().toObject(ClassModel.class);
                            if (cls != null) {
                                cls.setSubjects(new ArrayList<>(classSubjects.get(classId)));
                                loadedClasses.add(cls);
                            }
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            binding.loadingIndicator.setVisibility(View.GONE);
                            // Sort classes alphabetically by name
                            loadedClasses.sort((a, b) -> a.getName().compareTo(b.getName()));
                            populateClasses(loadedClasses);
                        }
                    });
                }
            } else {
                binding.loadingIndicator.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Failed to load classes", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateClasses(List<ClassModel> classes) {
        binding.classListContainer.removeAllViews();
        if (classes.isEmpty()) {
            binding.emptyText.setVisibility(View.VISIBLE);
            return;
        }
        binding.emptyText.setVisibility(View.GONE);

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        for (ClassModel cls : classes) {
            // Inflate a class card view
            View cardView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_class_info_card, binding.classListContainer, false);

            TextView nameText = cardView.findViewById(R.id.class_name_text);
            TextView subjectsText = cardView.findViewById(R.id.class_subjects_text);
            TextView studentCountText = cardView.findViewById(R.id.class_student_count_text);
            RecyclerView sessionsRecycler = cardView.findViewById(R.id.past_sessions_recycler);

            nameText.setText(cls.getName());
            studentCountText.setText((cls.getStudentIds() != null ? cls.getStudentIds().size() : 0) + " students");
            if (cls.getSubjects() != null) {
                subjectsText.setText(String.join("  •  ", cls.getSubjects()));
            }

            // Expand/Collapse logic for past sessions
            View expandButton = cardView.findViewById(R.id.expand_button);
            View expandIcon = cardView.findViewById(R.id.expand_icon);
            expandButton.setOnClickListener(v -> {
                if (sessionsRecycler.getVisibility() == View.VISIBLE) {
                    sessionsRecycler.setVisibility(View.GONE);
                    expandIcon.animate().rotation(0).setDuration(200).start();
                } else {
                    sessionsRecycler.setVisibility(View.VISIBLE);
                    expandIcon.animate().rotation(180).setDuration(200).start();
                }
            });

            // Load past sessions for this class
            sessionsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
            facultyRepository.getSessionsForClass(cls.getClassId()).addOnCompleteListener(task -> {
                if (binding == null) return;
                if (task.isSuccessful() && task.getResult() != null) {
                    List<AttendanceSession> sessions = task.getResult().toObjects(AttendanceSession.class);
                    // Filter: Only subjects this faculty teaches AND marked by this faculty
                    String facultyUid = authViewModel.getCurrentUserProfile().getValue().getInstitutionalId();
                    List<String> mySubjects = cls.getSubjects();
                    
                    sessions = sessions.stream()
                        .filter(s -> mySubjects != null && mySubjects.contains(s.getSubject()))
                        .filter(s -> facultyUid != null && facultyUid.equals(s.getMarkedBy()))
                        .sorted((a, b) -> {
                            if (a.getDate() == null || b.getDate() == null) return 0;
                            return b.getDate().compareTo(a.getDate());
                        })
                        .collect(java.util.stream.Collectors.toList());

                    PastSessionAdapter adapter = new PastSessionAdapter(sessions, sdf);
                    sessionsRecycler.setAdapter(adapter);
                }
            });

            binding.classListContainer.addView(cardView);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ---- inner adapter for past sessions ----
    static class PastSessionAdapter extends RecyclerView.Adapter<PastSessionAdapter.VH> {
        private final List<AttendanceSession> sessions;
        private final SimpleDateFormat sdf;

        PastSessionAdapter(List<AttendanceSession> sessions, SimpleDateFormat sdf) {
            this.sessions = sessions;
            this.sdf = sdf;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_past_session, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AttendanceSession s = sessions.get(position);
            holder.subjectText.setText(s.getSubject());
            if (s.getDate() != null) {
                holder.dateText.setText(sdf.format(s.getDate().toDate()));
            }
            // Compute attendance %
            if (s.getRecords() != null && !s.getRecords().isEmpty()) {
                long present = s.getRecords().values().stream().filter(b -> Boolean.TRUE.equals(b)).count();
                int pct = (int)(present * 100L / s.getRecords().size());
                holder.pctBadge.setText(pct + "%");
                holder.pctBadge.setTextColor(
                        holder.itemView.getContext().getColor(
                                pct >= 75 ? R.color.success : R.color.error));
            }
        }

        @Override
        public int getItemCount() { return sessions.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView subjectText, dateText, pctBadge;
            VH(@NonNull View v) {
                super(v);
                subjectText = v.findViewById(R.id.ps_subject_text);
                dateText = v.findViewById(R.id.ps_date_text);
                pctBadge = v.findViewById(R.id.ps_pct_badge);
            }
        }
    }
}
