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
}
