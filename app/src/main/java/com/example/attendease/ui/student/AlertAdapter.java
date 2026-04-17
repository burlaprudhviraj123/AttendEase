package com.example.attendease.ui.student;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendease.databinding.ItemAlertBinding;
import com.example.attendease.viewmodel.StudentViewModel;

import java.util.List;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.VH> {

    private final List<StudentViewModel.AlertItem> alerts;

    public AlertAdapter(List<StudentViewModel.AlertItem> alerts) {
        this.alerts = alerts;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAlertBinding binding = ItemAlertBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        StudentViewModel.AlertItem alert = alerts.get(position);
        holder.binding.alertTitle.setText("Low Attendance: " + alert.subject);
        
        String instructor = (alert.faculty != null && !alert.faculty.equals("—")) 
                ? alert.faculty : "your course instructor";
                
        String message = String.format("Your attendance has fallen to %d%%. You need to attend %d more classes to reach 75%%. Consider meeting with %s.", 
                alert.percent, alert.classesNeeded, instructor);
        
        holder.binding.alertMessage.setText(message);
        holder.binding.alertTime.setText("JUST NOW");
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ItemAlertBinding binding;
        VH(ItemAlertBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
