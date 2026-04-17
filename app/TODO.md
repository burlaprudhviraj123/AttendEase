## AttendEase — TODO (non-hallucinating, doc-backed)

This checklist is derived from:
- `AttendEase_PRD.md` (requirements, data model, roadmap, NFRs, scope)
- `AttendEase_Design_Document.md` (system architecture + constraints)
- `AttendEase_UI_Design_Document.md` (UI system + screen behaviors)
- `AttendEase_StitchAI_Prompts.md` (screen prompt workflow)

---

## Epic: UI deliverables (StitchAI prompts)
- [ ] In StitchAI, paste the **Global Style Prompt** before any screen prompt.
- [ ] Generate screens at **390×844** resolution (iPhone 14 size) for consistency.
- [ ] Generate all screens from the prompt set (keep the same design system across screens):
  - [ ] Login
  - [ ] Set Password (first-time login)
  - [ ] Faculty Home
  - [ ] Mark Attendance
  - [ ] Mode Switch Confirmation Dialog
  - [ ] My Classes (Faculty)
  - [ ] Faculty Analytics Dashboard
  - [ ] Student Home
  - [ ] Student Attendance Detail
  - [ ] Session History (Student)
  - [ ] Classes Needed Calculator (Student)
  - [ ] Profile
  - [ ] Forgot Password (show default + success state side-by-side)
- [ ] After all screens, generate a **component sheet** (buttons, cards, chips, badges, inputs).

---

## Epic: Foundation & authentication (Week 1)
- [ ] Create Android project (Java, **min SDK 24**).
- [ ] Add Firebase dependencies and initialize Firebase (Auth + Firestore; optionally FCM later).
- [ ] Implement **single entry Login screen** (email input + Continue + Google Sign-In option; no signup screen).
- [ ] Implement **email validation before any Firebase call**:
  - [ ] Must end with `@anits.edu.in`
  - [ ] Student pattern: `name.YY.dept@anits.edu.in`
  - [ ] Faculty pattern: `name.dept@anits.edu.in`
  - [ ] Reject unknown patterns with an error message
- [ ] Implement role detection + parsing (name/department/batch where applicable) from email.
- [ ] Implement new vs returning user routing using `fetchSignInMethodsForEmail`:
  - [ ] New user → Set Password
  - [ ] Returning user → password entry (login)
- [ ] Implement first-time Set Password flow:
  - [ ] Show parsed name + role badge + department (+ batch year for students)
  - [ ] Call `createUserWithEmailAndPassword`
  - [ ] Auto-create Firestore user profile (no extra profile form)
- [ ] Implement Google Sign-In:
  - [ ] Restrict account picker to hosted domain `anits.edu.in` (`setHostedDomain`)
  - [ ] Run the same email-pattern role detection on returned email before proceeding

---

## Epic: Forgot password
- [x] Add Forgot Password entry point from Login.
- [x] Implement reset flow using `sendPasswordResetEmail`.
- [x] Replace form with a **success state** after Send Reset Link.

---

## Epic: App architecture rules (MVVM)
- [ ] Enforce MVVM layering:
  - [ ] Activities/Fragments only observe state + handle navigation
  - [ ] ViewModels hold UI state + business rules (time lock, formulas, selection logic)
  - [ ] Repositories contain **all** Firebase Auth/Firestore calls (no Firebase calls from UI layer)
- [ ] Use **ViewBinding** throughout (no `findViewById`).
- [ ] Centralize Firestore collection name strings in a single constants file.
- [ ] Use LiveData (or equivalent observable) from ViewModels to Views.
- [ ] Ensure ViewModels survive rotation without refetch loops (avoid repeated Firebase calls on orientation change).

---

## Epic: Firestore data model (exact collections/fields)
- [ ] Define `/users/{userId}` documents with:
  - [ ] `name`, `email`, `role` ("student"/"faculty"), `department`, `batch` (students only)
- [ ] Define `/classes/{classId}` documents with:
  - [ ] `name`, `teacherId`, `studentIds` (array), `subjects` (array)
- [ ] Define `/timetable/{entryId}` documents with:
  - [ ] `facultyId`, `classId` (reference), `subject`, `day`, `startTime`, `endTime`
- [ ] Define `/attendance/{sessionId}` documents with:
  - [ ] `classId`, `subject`, `date` (timestamp), `markedBy` (faculty UID)
  - [ ] `records` map: `{ studentUid: true/false }` (present/absent)
- [ ] Use **one independent document per attendance session** (no shared doc updates between faculty).

---

## Epic: Firestore security rules
- [ ] Restrict reads so a user cannot read another user’s personal data.
- [ ] Restrict attendance writes so **only faculty role** accounts can write to `/attendance`.
- [ ] Restrict students so they can read only their own attendance data (per-record access).
- [ ] Ensure role enforcement is implemented at the database rules layer, not just UI.

---

## Epic: Offline support & reliability
- [ ] Enable Firebase offline persistence.
- [ ] Queue attendance writes offline and sync automatically on reconnect (silent recovery).
- [ ] Show a clear offline indicator but do not block marking attendance.
- [ ] Validate “no data loss” under intermittent connectivity scenarios.

---

## Epic: Timetable-driven faculty flow
- [ ] Implement timetable-driven session discovery (faculty never manually selects class/subject):
  - [ ] Determine current day/time on device
  - [ ] Query timetable for faculty UID and relevant day
  - [ ] Surface sessions on Faculty Home
- [ ] Implement session card states derived from current time:
  - [ ] Upcoming (before `startTime`)
  - [ ] Active (between `startTime` and `endTime`)
  - [ ] Closed (after `endTime`)
- [ ] Ensure session UI communicates state (highlight Active, lock Upcoming, gray Closed).

---

## Epic: Attendance integrity (time lock)
- [ ] Enforce rule: faculty cannot submit before class starts.
- [ ] Lock/disable Submit until device time reaches `startTime` (enforce in ViewModel).
- [ ] Display countdown label indicating when marking opens (until start time).

---

## Epic: Mark attendance (universal toggle + grid logic)
- [ ] Auto-populate session header from timetable (class, subject, time slot); faculty cannot edit.
- [ ] Implement segmented toggle:
  - [ ] Marking Absentees
  - [ ] Marking Presentees
- [ ] Initialize all student tiles as neutral/unselected.
- [ ] Tile tap rules:
  - [ ] Absentees mode: tap → red; tap again → neutral
  - [ ] Presentees mode: tap → green; tap again → neutral
- [ ] Submission interpretation:
  - [ ] Absentees mode: neutral tiles are treated as Present
  - [ ] Presentees mode: neutral tiles are treated as Absent
- [ ] Sticky footer:
  - [ ] Live counter reflecting count relevant to current mode
  - [ ] Submit action
- [ ] Mode switch confirmation dialog:
  - [ ] If selections exist and toggle switches → confirm “Switching mode will clear selections”
  - [ ] Yes clears selections; No cancels toggle change
- [ ] Post-submit confirmation (screen or toast) with final present/absent counts.

---

## Epic: Attendance write + retrieval semantics
- [ ] On Submit, write a new `/attendance/{sessionId}` document with `markedBy`, `date`, `classId`, `subject`, and `records` map.
- [ ] Ensure write pattern supports high concurrency (many faculty writing new docs in parallel).
- [ ] Implement faculty-scoped reads (e.g., attendance where `markedBy == currentFacultyUid`).
- [ ] Implement student-scoped reads (attendance sessions containing current student in `records`).

---

## Epic: Faculty screens (Home, My Classes, session correction entry)
- [ ] Faculty Home:
  - [ ] Greeting header showing parsed name + department
  - [ ] Summary cards: Classes Handled, Sessions This Month, Average Attendance
  - [ ] Today’s sessions list with Upcoming/Active/Closed states
  - [ ] Bottom navigation: Mark | Classes | Dashboard
- [ ] My Classes:
  - [ ] List classes assigned to the faculty from timetable
  - [ ] Class card shows class name, student count, subject chips
  - [ ] Drill into class → chronological past sessions
  - [ ] Session row shows subject, date, attendance % badge
  - [ ] Session details view shows full present/absent list
  - [ ] Provide a path to “correct mistakes” in past session records

---

## Epic: Student screens (Home, Attendance detail, History)
- [ ] Student Home:
  - [ ] Overall attendance circular progress ring with thresholds:
    - [ ] Green above 85%
    - [ ] Amber 75–85%
    - [ ] Red below 75%
  - [ ] Classes Attended / Classes Missed info chips
  - [ ] Subject cards (progress + % colored by threshold)
  - [ ] Alert banner when any subject <75% with “classes needed to recover” text
- [ ] Implement recovery calculation used by alert banner:
  - [ ] `classesNeeded = ceil((0.75 × (attended + classesNeeded) - attended))`
  - [ ] Compute in ViewModel and display plain-language alert
- [ ] My Attendance (subject list + drilldown):
  - [ ] Per subject: total sessions held/attended/missed/%.
  - [ ] Tap subject → session log (chronological) with green dot present / red dot absent.
- [ ] Session History:
  - [ ] Chronological list across subjects
  - [ ] Group by date labels: Today, Yesterday, specific dates
  - [ ] Filter bottom sheet: by subject and/or Present/Absent
- [ ] Bottom navigation: Home | Attendance | History

---

## Epic: Classes needed calculator (Student bottom sheet)
- [ ] Add bottom sheet entry point from Student Home.
- [ ] Input: total remaining classes in semester (single number).
- [ ] Output: per subject results:
  - [ ] “You can miss X more classes” or “Attend next X classes”
  - [ ] Pills colored green (safe) or red (at-risk)

---

## Epic: Profile (both roles)
- [ ] Show avatar + user info.
- [ ] Change password flow (Firebase Auth).
- [ ] Logout.

---

## Epic: Analytics dashboard & PDF export (Faculty)
- [ ] Subject filter chip row that filters all dashboard views.
- [ ] Bar chart: subject-wise attendance %, colored by threshold (green/amber/red).
- [ ] Line chart: attendance trend over last 10 sessions (selected subject).
- [ ] Students at Risk list:
  - [ ] Students below 75% with name, roll number, subject, % in red badge
- [ ] Export to PDF button:
  - [ ] Generate formatted report using **iTextPDF**
  - [ ] Ensure report generation time target: under 5 seconds
  - [ ] Save/download to device

---

## Epic: Performance & non-functional requirements
- [ ] Ensure all Firebase operations are async/off main thread (no UI blocking).
- [ ] Targets:
  - [ ] Attendance submission < 2 seconds (standard 4G)
  - [ ] Dashboard load < 3 seconds for up to 100 past sessions
  - [ ] Avoid ANR during Firestore operations
- [ ] Usability targets:
  - [ ] Faculty can mark a 60-student class in under 60 seconds
  - [ ] Student can check overall attendance in under 2 taps
  - [ ] One-hand usability on mobile screens

---

## Epic: Testing, stress, and demo milestones
- [ ] Unit tests (ViewModel logic):
  - [ ] Attendance percentage calculations
  - [ ] Recovery formula (alert banner)
  - [ ] Calculator logic
  - [ ] Mark-attendance toggle interpretation rules
- [ ] Stress/concurrency validation:
  - [ ] Verify 160+ simultaneous faculty submissions without errors
  - [ ] Validate performance targets under load
- [ ] Multi-device testing (API 24+).
- [ ] Prepare README + demo script + screen recording for presentation.

---

## Epic: Optional enhancements (only if timeline permits)
### Dark mode (P2)
- [ ] Implement system-aware dark mode theming (same layout, adjusted contrast).

### Push notifications (P3)
- [ ] Use FCM to notify when a student drops below 75% in any subject.

---
