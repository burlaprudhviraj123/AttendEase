package com.example.attendease.ui.student;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendease.R;
import com.example.attendease.databinding.ItemCourseAttendanceBinding;

import java.util.List;

public class CourseAttendanceAdapter extends RecyclerView.Adapter<CourseAttendanceAdapter.ViewHolder> {

    private final List<CourseAttendance> courses;

    public CourseAttendanceAdapter(List<CourseAttendance> courses) {
        this.courses = courses;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCourseAttendanceBinding binding = ItemCourseAttendanceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CourseAttendance course = courses.get(position);
        holder.binding.courseName.setText(course.getName());
        holder.binding.facultyName.setText(course.getFaculty());
        holder.binding.percentageText.setText(course.getPercentage() + "%");
        holder.binding.courseProgress.setProgress(course.getPercentage());
        
        // Color coding based on percentage
        int colorRes = R.color.success;
        if (course.getPercentage() < 75) {
            colorRes = R.color.error;
        } else if (course.getPercentage() < 85) {
            colorRes = R.color.warning;
        }
        
        holder.binding.percentageText.setTextColor(holder.itemView.getContext().getColor(colorRes));
        holder.binding.courseProgress.setIndicatorColor(holder.itemView.getContext().getColor(colorRes));
        holder.binding.courseIcon.setImageResource(course.getIconRes());
    }

    @Override
    public int getItemCount() {
        return courses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemCourseAttendanceBinding binding;

        public ViewHolder(ItemCourseAttendanceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class CourseAttendance {
        private String name;
        private String faculty;
        private int percentage;
        private int iconRes;
        private int attendedCount;
        private int totalHeldCount;

        public CourseAttendance(String name, String faculty, int percentage, int iconRes) {
            this(name, faculty, percentage, iconRes, 0, 0);
        }

        public CourseAttendance(String name, String faculty, int percentage, int iconRes, int attended, int total) {
            this.name = name;
            this.faculty = faculty;
            this.percentage = percentage;
            this.iconRes = iconRes;
            this.attendedCount = attended;
            this.totalHeldCount = total;
        }

        public String getName() { return name; }
        public String getFaculty() { return faculty; }
        public int getPercentage() { return percentage; }
        public int getIconRes() { return iconRes; }
        public int getAttendedCount() { return attendedCount; }
        public int getTotalHeldCount() { return totalHeldCount; }
    }
}
