package com.example.attendease.ui.student;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.attendease.R;
import com.example.attendease.databinding.BottomSheetClassesNeededBinding;
import com.example.attendease.viewmodel.StudentViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClassesNeededBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetClassesNeededBinding binding;
    private StudentViewModel studentViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetClassesNeededBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        studentViewModel = new ViewModelProvider(requireActivity()).get(StudentViewModel.class);
        setupObservers();
        setupListeners();
    }

    private void setupObservers() {
        studentViewModel.getCourseAttendanceList().observe(getViewLifecycleOwner(), courses -> {
            if (courses != null && !courses.isEmpty()) {
                List<String> names = new ArrayList<>();
                names.add("Overall Attendance");
                names.addAll(courses.stream().map(c -> c.getName()).collect(Collectors.toList()));
                
                Context context = getContext();
                if (context == null) return;
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(context, 
                        android.R.layout.simple_dropdown_item_1line, names);
                binding.subjectAutoComplete.setAdapter(adapter);
                
                // Default to Overall
                binding.subjectAutoComplete.setText("Overall Attendance", false);
                updateFieldsWithOverall(courses);

                binding.subjectAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                    String selected = (String) parent.getItemAtPosition(position);
                    if ("Overall Attendance".equals(selected)) {
                        updateFieldsWithOverall(courses);
                    } else {
                        CourseAttendanceAdapter.CourseAttendance match = courses.stream()
                            .filter(c -> c.getName().equals(selected)).findFirst().orElse(null);
                        if (match != null) {
                            binding.attendedEditText.setText(String.valueOf(match.getAttendedCount()));
                            binding.totalEditText.setText(String.valueOf(match.getTotalHeldCount()));
                            int remaining = studentViewModel.getRemainingClasses(match.getName(), "current");
                            binding.remainingEditText.setText(String.valueOf(remaining));
                            calculate();
                        }
                    }
                });
            }
        });
    }

    private void updateFieldsWithOverall(List<CourseAttendanceAdapter.CourseAttendance> courses) {
        int totalAttended = 0, totalHeld = 0, totalRem = 0;
        for (CourseAttendanceAdapter.CourseAttendance c : courses) {
            totalAttended += c.getAttendedCount();
            totalHeld += c.getTotalHeldCount();
            totalRem += studentViewModel.getRemainingClasses(c.getName(), "current");
        }
        binding.attendedEditText.setText(String.valueOf(totalAttended));
        binding.totalEditText.setText(String.valueOf(totalHeld));
        binding.remainingEditText.setText(String.valueOf(totalRem));
        calculate();
    }

    private void setupListeners() {
        binding.closeButton.setOnClickListener(v -> dismiss());

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                calculate();
            }
        };

        binding.attendedEditText.addTextChangedListener(watcher);
        binding.totalEditText.addTextChangedListener(watcher);
        binding.remainingEditText.addTextChangedListener(watcher);
    }

    private void calculate() {
        Context context = getContext();
        if (context == null) return;
        try {
            String attendedStr = binding.attendedEditText.getText().toString();
            String totalStr = binding.totalEditText.getText().toString();
            String remainStr = binding.remainingEditText.getText().toString();

            if (attendedStr.isEmpty() || totalStr.isEmpty()) {
                binding.resultText.setText("Select a subject above or enter numbers manually.");
                binding.resultText.setTextColor(context.getColor(R.color.on_background));
                return;
            }

            int attended = Integer.parseInt(attendedStr);
            int total = Integer.parseInt(totalStr);
            int remain = remainStr.isEmpty() ? 0 : Integer.parseInt(remainStr);

            if (attended > total) {
                binding.resultText.setText("Attended cannot be greater than Total.");
                binding.resultText.setTextColor(context.getColor(R.color.error));
                return;
            }

            double currentPct = (total == 0) ? 0 : ((double) attended / total) * 100;
            
            int neededToReach75 = (int) Math.ceil((0.75 * total - attended) / 0.25);

            StringBuilder result = new StringBuilder();
            result.append(String.format("Current split: %d / %d (%.1f%%)\n\n", attended, total, currentPct));

            if (currentPct >= 75) {
                int maxMiss = (int) Math.floor((attended / 0.75) - total);
                
                if (remainStr.isEmpty() || remain == 0) {
                    result.append(String.format("You are above 75%%!\nYou can safely miss %d consecutive classes and still maintain 75%%.", Math.max(0, maxMiss)));
                } else {
                    int requiredToAttend = (int) Math.ceil(0.75 * (total + remain) - attended);
                    int canMiss = remain - Math.max(0, requiredToAttend);
                    if (requiredToAttend <= 0) {
                        result.append(String.format("SAFE! You can miss all %d remaining classes and still stay above 75%%.", remain));
                    } else {
                        result.append(String.format("You can safely miss %d more classes this semester.\n(Must attend %d out of the remaining %d)", canMiss, requiredToAttend, remain));
                    }
                }
                binding.resultText.setTextColor(context.getColor(R.color.success));

            } else {
                result.append(String.format("You are currently below 75%%.\nYou need to ATTEND the next %d classes consecutively to reach 75%%.", Math.max(0, neededToReach75)));
                
                if (remain > 0) {
                    int requiredToAttend = (int) Math.ceil(0.75 * (total + remain) - attended);
                    if (requiredToAttend > remain) {
                        result.append("\n\nWarning: Even if you attend all remaining classes, you won't reach 75%.");
                    } else {
                        result.append(String.format("\n\nYou must attend at least %d of the remaining %d classes.", requiredToAttend, remain));
                    }
                }
                binding.resultText.setTextColor(context.getColor(R.color.error));
            }

            binding.resultText.setText(result.toString());

        } catch (Exception e) {
            binding.resultText.setText("Invalid inputs.");
            binding.resultText.setTextColor(context.getColor(R.color.error));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
