package com.example.attendease.ui.faculty;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendease.R;

import java.util.ArrayList;
import java.util.List;

public class StudentTileAdapter extends RecyclerView.Adapter<StudentTileAdapter.TileViewHolder> {

    public interface CountChangeListener {
        void onCountChanged(int selectedCount);
    }

    // Tile state: 0=neutral, 1=selected (meaning differs by mode)
    private final String[] studentIds;
    private final int[] tileStates;
    private boolean isAbsenteesMode;
    private final CountChangeListener countListener;

    public StudentTileAdapter(List<String> studentIds, boolean isAbsenteesMode,
                              CountChangeListener countListener) {
        this.studentIds = studentIds.toArray(new String[0]);
        this.tileStates = new int[this.studentIds.length];
        this.isAbsenteesMode = isAbsenteesMode;
        this.countListener = countListener;
    }

    @NonNull
    @Override
    public TileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_tile, parent, false);
        return new TileViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TileViewHolder holder, int position) {
        boolean selected = (tileStates[position] == 1);

        if (selected) {
            if (isAbsenteesMode) {
                // Red = absent
                holder.tileView.setBackgroundResource(R.drawable.bg_tile_absent);
                holder.labelText.setTextColor(holder.itemView.getContext().getColor(R.color.white));
            } else {
                // Green = present
                holder.tileView.setBackgroundResource(R.drawable.bg_tile_present);
                holder.labelText.setTextColor(holder.itemView.getContext().getColor(R.color.white));
            }
        } else {
            holder.tileView.setBackgroundResource(R.drawable.bg_tile_neutral);
            holder.labelText.setTextColor(holder.itemView.getContext().getColor(R.color.on_background));
        }

        // Show student index (1-based) as label — real app would show roll number
        holder.labelText.setText(String.valueOf(position + 1));

        holder.tileView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            // Toggle: neutral → selected; selected → neutral
            tileStates[pos] = (tileStates[pos] == 0) ? 1 : 0;
            notifyItemChanged(pos);
            countListener.onCountChanged(getSelectedCount());
        });
    }

    @Override
    public int getItemCount() { return studentIds.length; }

    public int getSelectedCount() {
        int count = 0;
        for (int s : tileStates) if (s == 1) count++;
        return count;
    }

    public boolean hasSelections() { return getSelectedCount() > 0; }

    public List<String> getSelectedStudentIds() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < tileStates.length; i++) {
            if (tileStates[i] == 1) result.add(studentIds[i]);
        }
        return result;
    }

    /** Clear all selections and switch mode, then refresh */
    public void switchMode(boolean absenteesMode) {
        this.isAbsenteesMode = absenteesMode;
        for (int i = 0; i < tileStates.length; i++) tileStates[i] = 0;
        notifyDataSetChanged();
    }

    static class TileViewHolder extends RecyclerView.ViewHolder {
        View tileView;
        TextView labelText;

        TileViewHolder(@NonNull View itemView) {
            super(itemView);
            tileView = itemView.findViewById(R.id.student_tile_view);
            labelText = itemView.findViewById(R.id.student_tile_label);
        }
    }
}
