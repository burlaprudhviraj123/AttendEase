package com.example.attendease.ui.student;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.attendease.databinding.BottomSheetClassesNeededBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ClassesNeededCalculatorBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetClassesNeededBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetClassesNeededBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Recalculate whenever any input changes
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) { recalculate(); }
        };
        binding.attendedEditText.addTextChangedListener(watcher);
        binding.totalEditText.addTextChangedListener(watcher);
        binding.remainingEditText.addTextChangedListener(watcher);

        binding.closeButton.setOnClickListener(v -> dismiss());
    }

    private void recalculate() {
        try {
            String aStr = binding.attendedEditText.getText().toString().trim();
            String tStr = binding.totalEditText.getText().toString().trim();
            String rStr = binding.remainingEditText.getText().toString().trim();

            if (aStr.isEmpty() || tStr.isEmpty()) {
                binding.resultText.setText("Enter attended and total classes.");
                return;
            }

            int attended = Integer.parseInt(aStr);
            int total = Integer.parseInt(tStr);
            int remaining = rStr.isEmpty() ? 0 : Integer.parseInt(rStr);

            if (attended > total) {
                binding.resultText.setText("Attended cannot exceed total.");
                return;
            }

            double currentPct = total > 0 ? (attended * 100.0 / total) : 0;
            double projectedPct = (total + remaining) > 0
                    ? ((attended + remaining) * 100.0 / (total + remaining)) : 0;

            // Classes needed to reach 75% without attending any remaining classes
            int needed = computeClassesNeeded(attended, total);

            // Max classes that can be skipped from remaining and still stay >= 75%
            int canSkip = computeCanSkip(attended, total, remaining);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Current attendance: %.1f%%\n\n", currentPct));

            if (currentPct >= 75) {
                sb.append("✅ You are above 75%.\n\n");
                if (remaining > 0) {
                    sb.append(String.format("You can skip up to %d more class(es) and stay above 75%%.\n", canSkip));
                    sb.append(String.format("If you attend all remaining: %.1f%%", projectedPct));
                }
            } else {
                sb.append(String.format("⚠️ You need to attend %d more consecutive class(es) to reach 75%%.\n\n", needed));
                if (remaining > 0) {
                    int mustAttend = remaining - canSkip;
                    sb.append(String.format("From the %d remaining classes: attend at least %d to hit 75%%.",
                            remaining, Math.max(0, mustAttend)));
                }
            }
            binding.resultText.setText(sb.toString());

        } catch (NumberFormatException e) {
            binding.resultText.setText("Please enter valid numbers.");
        }
    }

    /** Classes needed to reach 75%: n = ceil((0.75*total - attended) / 0.25) */
    private static int computeClassesNeeded(int attended, int total) {
        double n = (0.75 * total - attended) / 0.25;
        return (int) Math.ceil(Math.max(0, n));
    }

    /** Max classes that can be skipped from 'remaining' and still keep (attended)/(total+remaining-skipped) >= 75% */
    private static int computeCanSkip(int attended, int total, int remaining) {
        // attended / (total + remaining - skipped) >= 0.75
        // total + remaining - skipped <= attended / 0.75
        // skipped >= total + remaining - attended/0.75
        double maxTotal = attended / 0.75;
        int skip = (int) (maxTotal - total);
        return Math.max(0, Math.min(skip, remaining));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
