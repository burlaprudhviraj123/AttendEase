package com.example.attendease.ui.faculty;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendease.R;
import com.example.attendease.model.SessionState;
import com.example.attendease.model.TimetableSession;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class SessionCardAdapter extends RecyclerView.Adapter<SessionCardAdapter.ViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClicked(TimetableSession session);
    }

    private List<TimetableSession> sessions;
    private final OnSessionClickListener listener;

    public SessionCardAdapter(List<TimetableSession> sessions, OnSessionClickListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }

    public void updateSessions(List<TimetableSession> newSessions) {
        this.sessions = newSessions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimetableSession session = sessions.get(position);
        holder.subjectText.setText(session.getSubject());
        holder.classTimeText.setText(session.getClassName() + " · " +
                session.getStartTime() + " – " + session.getEndTime());
        holder.studentCountText.setText(session.getStudentCount() + " students");

        // Style + label by state
        SessionState state = session.getState();
        switch (state) {
            case ACTIVE:
                holder.stateLabel.setText("ACTIVE");
                holder.stateLabel.setBackgroundResource(R.drawable.bg_state_active);
                holder.stateLabel.setTextColor(holder.itemView.getContext().getColor(R.color.success));
                holder.card.setAlpha(1f);
                holder.card.setClickable(true);
                holder.card.setOnClickListener(v -> listener.onSessionClicked(session));
                break;
            case UPCOMING:
                long secs = session.getSecondsUntilStart();
                String countdown = formatCountdown(secs);
                holder.stateLabel.setText("IN " + countdown);
                holder.stateLabel.setBackgroundResource(R.drawable.bg_state_upcoming);
                holder.stateLabel.setTextColor(holder.itemView.getContext().getColor(R.color.primary));
                holder.card.setAlpha(1f);
                holder.card.setClickable(false);
                holder.card.setOnClickListener(null);
                break;
            case CLOSED:
                holder.stateLabel.setText("CLOSED");
                holder.stateLabel.setBackgroundResource(R.drawable.bg_state_closed);
                holder.stateLabel.setTextColor(holder.itemView.getContext().getColor(R.color.outline));
                holder.card.setAlpha(0.6f);
                holder.card.setClickable(false);
                holder.card.setOnClickListener(null);
                break;
        }
    }

    private String formatCountdown(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return totalSeconds + "s";
    }

    @Override
    public int getItemCount() { return sessions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView subjectText, classTimeText, studentCountText, stateLabel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            subjectText = itemView.findViewById(R.id.session_subject_text);
            classTimeText = itemView.findViewById(R.id.session_class_time_text);
            studentCountText = itemView.findViewById(R.id.session_student_count_text);
            stateLabel = itemView.findViewById(R.id.session_state_label);
        }
    }
}
