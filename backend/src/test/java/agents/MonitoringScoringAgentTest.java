package agents;

import game.GameSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonitoringScoringAgentTest {

    private MonitoringScoringAgent agent;

    @BeforeEach
    void setUp() {
        agent = new MonitoringScoringAgent();
        agent.startSession(); // initialises XP=500, clears counters
    }

    // --- computeXP via processOutcome (no help, no retry) ---

    @Test
    void super_effective_no_help_awards_150_xp() {
        int xp = agent.processOutcome("SUPER_EFFECTIVE", false, false);
        assertEquals(150, xp);
    }

    @Test
    void normal_no_help_awards_100_xp() {
        int xp = agent.processOutcome("NORMAL", false, false);
        assertEquals(100, xp);
    }

    @Test
    void weak_no_help_awards_0_xp() {
        int xp = agent.processOutcome("WEAK", false, false);
        assertEquals(0, xp);
    }

    @Test
    void super_effective_with_help_awards_25_xp() {
        int xp = agent.processOutcome("SUPER_EFFECTIVE", true, false);
        assertEquals(25, xp);
    }

    @Test
    void super_effective_on_retry_awards_25_xp() {
        int xp = agent.processOutcome("SUPER_EFFECTIVE", false, true);
        assertEquals(25, xp);
    }

    // --- XP accumulation across waves ---

    @Test
    void xp_accumulates_correctly_across_waves() {
        // start at 500, +150, +100 → 750
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("NORMAL", false, false);
        GameSummary summary = agent.endSession();
        assertEquals(750, summary.totalXP);
    }

    @Test
    void waves_completed_increments_per_outcome() {
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("NORMAL", false, false);
        GameSummary summary = agent.endSession();
        assertEquals(2, summary.wavesCompleted);
    }

    // --- deductXP ---

    @Test
    void deduct_xp_applies_correct_fraction() {
        // 500 * 0.5 = 250 deducted → 250 remaining
        int remaining = agent.deductXP(0.5);
        assertEquals(250, remaining);
    }

    @Test
    void deduct_xp_minimum_is_1() {
        // 500 * 0.001 = 0.5 → floor = 0 → max(1, 0) = 1 deducted → 499 remaining
        int remaining = agent.deductXP(0.001);
        assertEquals(499, remaining);
    }

    @Test
    void deduct_xp_never_goes_below_zero() {
        agent.deductXP(1.0); // deducts all 500 → 0
        int remaining = agent.deductXP(1.0); // would go negative without the max(0,...)
        assertEquals(0, remaining);
    }

    // --- performanceRating via endSession ---

    @Test
    void rating_is_recruit_at_300_xp() {
        agent.deductXP(0.4); // 500 - 200 = 300
        GameSummary summary = agent.endSession();
        assertEquals("RECRUIT", summary.performanceRating);
    }

    @Test
    void rating_is_analyst_at_301_xp() {
        // start 500, deduct 199 → 301
        // deductXP(fraction): deduction = max(1, floor(500 * fraction))
        // want deduction = 199 → fraction = 199/500 = 0.398
        agent.deductXP(0.398);
        GameSummary summary = agent.endSession();
        assertEquals("ANALYST", summary.performanceRating);
    }

    @Test
    void rating_is_specialist_at_602_xp() {
        // start 500, earn +150 (SUPER_EFFECTIVE) → 650, then deduct to 602
        agent.processOutcome("SUPER_EFFECTIVE", false, false); // 650
        agent.deductXP(0.075); // floor(650 * 0.075) = floor(48.75) = 48 → 602
        GameSummary summary = agent.endSession();
        assertEquals("SPECIALIST", summary.performanceRating);
    }

    @Test
    void rating_is_defender_above_901_xp() {
        // start 500, earn +150 +150 +150 +150 = 600 → total 1100
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        GameSummary summary = agent.endSession();
        assertEquals("DEFENDER", summary.performanceRating);
    }

    // --- endSession summary ---

    @Test
    void end_session_summary_contains_closing_message() {
        GameSummary summary = agent.endSession();
        assertNotNull(summary.closingMessage);
        assertFalse(summary.closingMessage.isEmpty());
    }

    @Test
    void end_session_accuracy_is_zero_with_no_waves() {
        GameSummary summary = agent.endSession();
        assertEquals(0.0, summary.accuracyRate);
    }

    // --- helpUsedCount tracking ---

    @Test
    void record_help_used_increments_help_count() {
        agent.recordHelpUsed();
        agent.recordHelpUsed();
        GameSummary summary = agent.endSession();
        assertEquals(2, summary.helpUsedCount);
    }

    // --- accuracy rate ---

    @Test
    void accuracy_is_1_when_all_waves_are_first_attempt() {
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("NORMAL", false, false);
        GameSummary summary = agent.endSession();
        assertEquals(1.0, summary.accuracyRate, 0.0001);
    }

    @Test
    void accuracy_is_correct_with_mixed_retries() {
        // wave 1: first attempt (correctFirstAttempts=1, wavesCompleted=1)
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        // wave 2: retry (correctFirstAttempts stays 1, wavesCompleted=2)
        agent.processOutcome("SUPER_EFFECTIVE", false, true);
        GameSummary summary = agent.endSession();
        assertEquals(0.5, summary.accuracyRate, 0.0001);
    }

    // --- performanceRating upper-boundary tests ---

    @Test
    void rating_is_analyst_at_exactly_600_xp() {
        // start=500 + NORMAL(+100) = 600
        agent.processOutcome("NORMAL", false, false);
        GameSummary summary = agent.endSession();
        assertEquals(600, summary.totalXP);
        assertEquals("ANALYST", summary.performanceRating);
    }

    @Test
    void rating_is_specialist_at_601_xp() {
        // start=500, +SUPER_EFFECTIVE(+150)=650, deduct 49 → 601
        // deduction = max(1, floor(650 * 0.0754)) = max(1, floor(49.01)) = 49
        agent.processOutcome("SUPER_EFFECTIVE", false, false); // 650
        agent.deductXP(0.0754);                                 // 650-49=601
        GameSummary summary = agent.endSession();
        assertEquals(601, summary.totalXP);
        assertEquals("SPECIALIST", summary.performanceRating);
    }

    @Test
    void rating_is_specialist_at_exactly_900_xp() {
        // start=500, 2x SUPER_EFFECTIVE(+300), 1x NORMAL(+100) = 900
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("NORMAL", false, false);
        GameSummary summary = agent.endSession();
        assertEquals(900, summary.totalXP);
        assertEquals("SPECIALIST", summary.performanceRating);
    }

    @Test
    void rating_is_defender_at_901_xp() {
        // start=500, 3x SUPER_EFFECTIVE(+450)=950, deduct 49 → 901
        // deduction = max(1, floor(950 * 0.0516)) = max(1, floor(49.02)) = 49
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false); // 950
        agent.deductXP(0.0516);                                 // 950-49=901
        GameSummary summary = agent.endSession();
        assertEquals(901, summary.totalXP);
        assertEquals("DEFENDER", summary.performanceRating);
    }

    // --- closingMessage content per rating ---

    @Test
    void closing_message_for_recruit_rating() {
        agent.deductXP(0.4); // 300 XP → RECRUIT
        GameSummary summary = agent.endSession();
        assertEquals("RECRUIT", summary.performanceRating);
        assertTrue(summary.closingMessage.contains("recruit"));
    }

    @Test
    void closing_message_for_analyst_rating() {
        agent.processOutcome("NORMAL", false, false); // 600 XP → ANALYST
        GameSummary summary = agent.endSession();
        assertEquals("ANALYST", summary.performanceRating);
        assertTrue(summary.closingMessage.contains("Analyst"));
    }

    @Test
    void closing_message_for_specialist_rating() {
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("NORMAL", false, false); // 900 XP → SPECIALIST
        GameSummary summary = agent.endSession();
        assertEquals("SPECIALIST", summary.performanceRating);
        assertTrue(summary.closingMessage.contains("Specialist"));
    }

    @Test
    void closing_message_for_defender_rating() {
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false);
        agent.processOutcome("SUPER_EFFECTIVE", false, false); // 1100 XP → DEFENDER
        GameSummary summary = agent.endSession();
        assertEquals("DEFENDER", summary.performanceRating);
        assertTrue(summary.closingMessage.contains("DEFENDER"));
    }

    // --- deductXP edge cases ---

    @Test
    void deduct_xp_with_zero_fraction_deducts_minimum_of_one() {
        // fraction 0.0 → floor(500 * 0.0) = 0 → max(1, 0) = 1 → 499 remaining
        int remaining = agent.deductXP(0.0);
        assertEquals(499, remaining);
    }
}
