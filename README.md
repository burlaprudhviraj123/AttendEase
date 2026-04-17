# AttendEase - Intelligent Attendance Management System

AttendEase is a context-aware Android application designed to streamline academic attendance tracking. Unlike generic attendance trackers, AttendEase is built to be **institution-aware**, integrating directly with real-time college data to provide a seamless experience for both students and faculty.

---

## 🚀 What Makes AttendEase Different?

Most attendance apps are simple counters that require manual entry of subjects and schedules. AttendEase redefines this by focusing on **context and automation**:

*   **Institution-Level Integration**: Automated synchronization with real-time college timetables. Students don't "add subjects"—the app already knows their schedule based on their department and section.
*   **Dynamic Data Parsing**: Uses a robust CSV-parsing engine to handle complex department-specific schedules, faculty mappings, and room assignments.
*   **Smart "My Classes" Logic**: A dynamic dashboard that calculates "What's Next?" in real-time, providing instant visibility into current and upcoming sessions.
*   **Contextual Attendance Tracking**: Records aren't just numbers; they are linked to specific faculty members, time slots, and academic dates, enabling rich analytics.

---

## ✨ Key Features

*   **📍 Real-Time Dashboard**: High-visibility "My Classes" view for students to track their daily academic commitments.
*   **📊 Analytics Suite**: Visualized attendance trends, "at-risk" student identification for faculty, and overall percentage tracking.
*   **📂 Seamless Synchronization**: Automated tools for importing faculty, student, and timetable data from CSV into Firestore.
*   **🌙 Modern UI/UX**: Clean, responsive interface with Dark Mode support and intuitive navigation.
*   **📲 Offline Accessibility**: Critical timetable data is cached locally, ensuring students can check their schedule even without an active internet connection.

---

## 🛠️ Tech Stack

*   **Platform**: Android (Native Java & XML)
*   **Database**: Firebase Firestore (Real-time NoSQL)
*   **Authentication**: Firebase Auth (Secure login/reset flow)
*   **Automation**: Python (Custom data ingestion scripts)
*   **Version Control**: Git & GitHub

---

## 🛡️ Security & Privacy

*   **Secure Authentication**: Role-based access control (Student vs. Faculty) via Firebase.
*   **Data Integrity**: Automated validation during CSV imports to prevent orphaned or duplicate records.
*   **Environment Safety**: Sensitive configurations like `serviceAccountKey.json` and `google-services.json` are excluded from version control to maintain project security.

---

## 📸 Screenshots

*(Add your screenshots here to make the README pop!)*

---

## 🏁 Getting Started

1. Clone the repository: `git clone https://github.com/burlaprudhviraj123/AttendEase.git`
2. Open the project in **Android Studio**.
3. Add your `google-services.json` to the `app/` directory.
4. Sync Gradle and Run.

---

Developed with ❤️ for streamlined academic management.
