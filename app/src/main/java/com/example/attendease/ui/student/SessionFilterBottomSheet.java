package com.example.attendease.ui.student;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendease.databinding.BottomSheetSessionFilterBinding;
import com.example.attendease.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.List;

public class SessionFilterBottomSheet extends BottomSheetDialogFragment {

    public interface OnFilterApply {
        void onApply(String subject, Boolean present); // null = no filter
    }

    private final List<String> subjects;
    private final String currentSubject;
    private final Boolean currentPresence;
    private final OnFilterApply callback;
    private BottomSheetSessionFilterBinding binding;
    private String selectedSubject;
    private Boolean selectedPresence;

    public SessionFilterBottomSheet(List<String> subjects, String currentSubject,
                                    Boolean currentPresence, OnFilterApply callback) {
        this.subjects = subjects;
        this.currentSubject = currentSubject;
        this.currentPresence = currentPresence;
        this.callback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSessionFilterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        selectedSubject = currentSubject;
        selectedPresence = currentPresence;

        // Subject chips
        for (String s : subjects) {
            Chip chip = new Chip(requireContext());
            chip.setText(s);
            chip.setCheckable(true);
            chip.setChecked(s.equals(currentSubject) || ("All Subjects".equals(s) && currentSubject == null));
            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) selectedSubject = "All Subjects".equals(s) ? null : s;
            });
            binding.subjectChipGroup.addView(chip);
        }

        // Present / Absent chips
        binding.chipPresent.setChecked(Boolean.TRUE.equals(currentPresence));
        binding.chipAbsent.setChecked(Boolean.FALSE.equals(currentPresence));
        binding.chipPresent.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) { selectedPresence = true; binding.chipAbsent.setChecked(false); }
        });
        binding.chipAbsent.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) { selectedPresence = false; binding.chipPresent.setChecked(false); }
        });
        binding.chipAllStatus.setOnClickListener(v -> {
            selectedPresence = null;
            binding.chipPresent.setChecked(false);
            binding.chipAbsent.setChecked(false);
        });

        binding.applyFilterButton.setOnClickListener(v -> {
            callback.onApply(selectedSubject, selectedPresence);
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
