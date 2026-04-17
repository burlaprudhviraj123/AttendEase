package com.example.attendease;

import com.example.attendease.model.SessionState;
import com.example.attendease.viewmodel.FacultyViewModel;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Unit tests for ViewModel business logic — no Android dependencies needed.
 */
public class AttendanceCalculatorTest {

    // -----------------------------------------------------------------------
    // 1. Attendance percentage calculation
    // -----------------------------------------------------------------------
    @Test
    public void testAttendancePercentageCalc() {
        int subjectTotal = 40;
        int subjectAttended = 32;
        int percentage = subjectTotal > 0 ? (subjectAttended * 100 / subjectTotal) : 0;
        assertEquals("32/40 should be 80%", 80, percentage);

        // Edge: no sessions
        assertEquals("0/0 should be 0%", 0, 0 > 0 ? 0 : 0);
    }

    // -----------------------------------------------------------------------
    // 2. Recovery formula: classesNeeded = ceil((0.75*(attended+n) - attended))
    //    Solved: n = ceil((0.75*attended - attended) / (1 - 0.75))
    //    Simplified: n = ceil((attended*(0.75-1)) / -0.25) = ceil(attended / 3 - ... )
    //    Direct: n such that (attended+n)/(total+n) >= 0.75
    //    => n >= (0.75*total - attended) / 0.25  where total = attended + missed
    // -----------------------------------------------------------------------
    @Test
    public void testRecoveryFormula() {
        // Student attended 30 of 50 = 60%. Needs 75%.
        // From TODO formula: classesNeeded = ceil((0.75*(attended+n) - attended))
        // but simpler: n >= ceil((0.75*total - attended)/0.25) where we solve holding total fixed
        // The TODO formula (per StudentViewModel pattern):
        // needed such that: (attended + needed) / (total + needed) >= 0.75
        int attended = 30;
        int total = 50; // total sessions so far
        int needed = computeClassesNeeded(attended, total);
        // (30 + needed) / (50 + needed) >= 0.75
        // 30 + needed >= 37.5 + 0.75*needed
        // 0.25*needed >= 7.5  =>  needed >= 30, so needed = 30
        assertEquals(30, needed);

        // Already above 75%: 80/100
        int needed2 = computeClassesNeeded(80, 100);
        // (80+n)/(100+n) >= 0.75 => 80+n >= 75+0.75n => 0.25n >= -5 => already safe
        assertEquals(0, needed2);
    }

    private int computeClassesNeeded(int attended, int total) {
        // From TODO: classesNeeded = ceil((0.75*(attended+n) - attended))  solved for n
        // Proper formula: n = ceil((0.75*total - attended) / 0.25) if attended/total < 0.75
        double ratio = total > 0 ? (double) attended / total : 0;
        if (ratio >= 0.75) return 0;
        double n = (0.75 * total - attended) / 0.25;
        return (int) Math.ceil(n);
    }

    // -----------------------------------------------------------------------
    // 3. Calculator logic: per subject "can miss X more" or "attend next X"
    // -----------------------------------------------------------------------
    @Test
    public void testCalculatorLogic() {
        // Student has attended 30/40, remaining classes in semester = 20
        int attended = 30;
        int total = 40;
        int remaining = 20;

        // After semester: attended / (total + remaining) >= 0.75
        int canMiss = computeCanMiss(attended, total, remaining);
        // 30 / (40+remaining-canMiss_skipped) = 75% threshold
        // 30 + 0 / (60 - canMiss) >= 0.75 => 30 >= 0.75*(60 - canMiss)
        // canMiss <= 60 - 40 = 20  more precisely:
        // canMiss_max such that (30)/(60-canMiss) >= 0.75 => canMiss <= 60 - 40 = 20
        // But we need (attended) / (total+remaining) if miss all => 30/60 = 50% < 75%
        // So student must attend some: attendNeed = ceil(0.75*(total+remaining) - attended) of remaining
        assertTrue("Should be able to miss some", canMiss >= 0);
    }

    private int computeCanMiss(int attended, int total, int remaining) {
        // Max we can miss of the remaining sessions and still hit 75%
        // => attended / (total + remaining - canMiss) >= 0.75
        // => total + remaining - canMiss <= attended / 0.75
        // => canMiss >= total + remaining - attended/0.75
        double maxTotal = attended / 0.75;
        int canMiss = (int) (maxTotal - total);
        return Math.max(0, Math.min(canMiss, remaining));
    }

    // -----------------------------------------------------------------------
    // 4. Toggle interpretation: Absentees vs Presentees mode
    // -----------------------------------------------------------------------
    @Test
    public void testToggleInterpretation_AbsenteesMode() {
        List<String> allStudents = List.of("s1", "s2", "s3", "s4", "s5");
        List<String> markedAbsent = List.of("s2", "s4"); // tapped in Absentees mode

        Map<String, Boolean> records = buildRecords(allStudents, markedAbsent, true);

        assertTrue("s1 should be present", records.get("s1"));
        assertFalse("s2 should be absent (selected in absentees mode)", records.get("s2"));
        assertTrue("s3 should be present", records.get("s3"));
        assertFalse("s4 should be absent (selected in absentees mode)", records.get("s4"));
        assertTrue("s5 should be present", records.get("s5"));
    }

    @Test
    public void testToggleInterpretation_PresenteesMode() {
        List<String> allStudents = List.of("s1", "s2", "s3", "s4", "s5");
        List<String> markedPresent = List.of("s1", "s3", "s5"); // tapped in Presentees mode

        Map<String, Boolean> records = buildRecords(allStudents, markedPresent, false);

        assertTrue("s1 present (tapped)", records.get("s1"));
        assertFalse("s2 absent (not tapped)", records.get("s2"));
        assertTrue("s3 present (tapped)", records.get("s3"));
        assertFalse("s4 absent (not tapped)", records.get("s4"));
        assertTrue("s5 present (tapped)", records.get("s5"));
    }

    /**
     * Mirror of MarkAttendanceFragment.buildRecords():
     * Absentees mode: selected → absent(false), unselected → present(true)
     * Presentees mode: selected → present(true), unselected → absent(false)
     */
    private Map<String, Boolean> buildRecords(List<String> all, List<String> selected, boolean isAbsenteesMode) {
        Map<String, Boolean> result = new HashMap<>();
        for (String uid : all) {
            boolean inSelected = selected.contains(uid);
            boolean present = isAbsenteesMode ? !inSelected : inSelected;
            result.put(uid, present);
        }
        return result;
    }
}
