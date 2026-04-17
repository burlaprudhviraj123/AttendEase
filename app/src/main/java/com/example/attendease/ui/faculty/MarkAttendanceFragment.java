package com.example.attendease.ui.faculty;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.attendease.databinding.FragmentMarkAttendanceBinding;
import com.example.attendease.utils.NetworkUtils;
import com.example.attendease.viewmodel.AuthViewModel;
import com.example.attendease.viewmodel.FacultyViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarkAttendanceFragment extends Fragment {

    private FragmentMarkAttendanceBinding binding;
    private AuthViewModel authViewModel;
    private FacultyViewModel facultyViewModel;
    private StudentTileAdapter tileAdapter;

    // Mode: true = Marking Absentees; false = Marking Presentees
    private boolean isAbsenteesMode = true;

    private String classId, className, subject, startTime, endTime, facultyId;
    private ArrayList<String> studentIds;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMarkAttendanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        facultyViewModel = new ViewModelProvider(requireActivity()).get(FacultyViewModel.class);

        // Receive arguments
        Bundle args = getArguments();
        if (args != null) {
            classId = args.getString("classId", "");
            className = args.getString("className", "");
            subject = args.getString("subject", "");
            startTime = args.getString("startTime", "");
            endTime = args.getString("endTime", "");
            facultyId = args.getString("facultyId", "");
            studentIds = args.getStringArrayList("studentIds");
        }
        if (studentIds == null) studentIds = new ArrayList<>();

        setupUI();
        setupTileGrid();
        setupObservers();
        setupClickListeners();
    }

    private void setupUI() {
        binding.sessionSubjectText.setText(subject);
        binding.sessionInfoText.setText(className + " · " + startTime + " – " + endTime);
        updateCounter();
    }

    private void setupTileGrid() {
        tileAdapter = new StudentTileAdapter(studentIds, isAbsenteesMode, selectedCount -> updateCounter());
        binding.studentGrid.setLayoutManager(new GridLayoutManager(getContext(), 5));
        binding.studentGrid.setAdapter(tileAdapter);
    }

    private void setupObservers() {
        facultyViewModel.getSubmitSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                facultyViewModel.resetSubmitState();
                Map<String, Boolean> records = buildRecords();
                int presentCount = (int) records.values().stream().filter(b -> b).count();
                int absentCount = records.size() - presentCount;
                Toast.makeText(getContext(),
                        "Attendance submitted! Present: " + presentCount + ", Absent: " + absentCount,
                        Toast.LENGTH_LONG).show();
                Navigation.findNavController(requireView()).navigateUp();
            }
        });

        facultyViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                facultyViewModel.resetSubmitState();
            }
        });

        facultyViewModel.getIsLoading().observe(getViewLifecycleOwner(), loading ->
                binding.submitButton.setEnabled(!loading));
    }

    private void setupClickListeners() {
        binding.backButton.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        // Toggle group: 0 = Absentees, 1 = Presentees
        binding.modeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean newMode = (checkedId == binding.btnAbsentees.getId());
            if (newMode == isAbsenteesMode) return;

            if (tileAdapter.hasSelections()) {
            Context context = getContext();
            if (context != null) {
                new AlertDialog.Builder(context)
                        .setTitle("Switch Mode?")
                        .setMessage("Switching mode will clear your current selections.")
                        .setPositiveButton("Switch", (d, w) -> applyModeSwitch(newMode))
                        .setNegativeButton("Cancel", (d, w) -> {
                            // revert toggle
                            binding.modeToggleGroup.check(
                                    isAbsenteesMode ? binding.btnAbsentees.getId()
                                            : binding.btnPresentees.getId());
                        })
                        .show();
            } else {
                applyModeSwitch(newMode);
            }
            } else {
                applyModeSwitch(newMode);
            }
        });

        binding.submitButton.setOnClickListener(v -> submitAttendance());
    }

    private void applyModeSwitch(boolean absenteesMode) {
        isAbsenteesMode = absenteesMode;
        tileAdapter.switchMode(absenteesMode);
        updateCounter();
    }

    private void updateCounter() {
        int selected = tileAdapter != null ? tileAdapter.getSelectedCount() : 0;
        int total = studentIds.size();
        if (isAbsenteesMode) {
            binding.counterText.setText("Marked Absent: " + selected + " / " + total);
        } else {
            binding.counterText.setText("Marked Present: " + selected + " / " + total);
        }
    }

    private void submitAttendance() {
        if (authViewModel.getCurrentUserProfile().getValue() == null) return;
        
        // Connectivity check for reassurance
        Context context = getContext();
        if (context == null) return;
        
        if (!NetworkUtils.isInternetAvailable(context)) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Poor Connection Detected")
                .setMessage("Don't worry! Your attendance records are saved securely on this device and will be automatically synced with the institutional server as soon as you are back online.")
                .setPositiveButton("Understood", (dialog, which) -> {
                    performSubmission();
                    dialog.dismiss();
                })
                .show();
        } else {
            performSubmission();
        }
    }

    private void performSubmission() {
        String fid = authViewModel.getCurrentUserProfile().getValue().getInstitutionalId();
        Map<String, Boolean> records = buildRecords();
        facultyViewModel.submitAttendance(classId, subject, fid, records);
    }

    /**
     * Build the records map according to mode interpretation:
     * • Absentees mode: selected tiles = absent (false), unselected = present (true)
     * • Presentees mode: selected tiles = present (true), unselected = absent (false)
     */
    private Map<String, Boolean> buildRecords() {
        Map<String, Boolean> records = new HashMap<>();
        List<String> selected = tileAdapter.getSelectedStudentIds();

        for (String uid : studentIds) {
            boolean inSelected = selected.contains(uid);
            boolean present = isAbsenteesMode ? !inSelected : inSelected;
            records.put(uid, present);
        }
        return records;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
