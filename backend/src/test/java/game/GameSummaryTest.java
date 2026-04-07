package game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSummaryTest {

    @Test
    void constructor_sets_all_fields_correctly() {
        GameSummary summary = new GameSummary(750, 3, 2, 1, 0.667, "SPECIALIST", "Impressive work.");

        assertEquals(750,        summary.totalXP);
        assertEquals(3,          summary.wavesCompleted);
        assertEquals(2,          summary.correctFirstAttempts);
        assertEquals(1,          summary.helpUsedCount);
        assertEquals(0.667,      summary.accuracyRate, 0.0001);
        assertEquals("SPECIALIST", summary.performanceRating);
        assertEquals("Impressive work.", summary.closingMessage);
    }

    @Test
    void zero_value_summary_is_valid() {
        GameSummary summary = new GameSummary(0, 0, 0, 0, 0.0, "RECRUIT", "Keep training.");

        assertEquals(0,        summary.totalXP);
        assertEquals(0,        summary.wavesCompleted);
        assertEquals(0,        summary.correctFirstAttempts);
        assertEquals(0,        summary.helpUsedCount);
        assertEquals(0.0,      summary.accuracyRate);
        assertEquals("RECRUIT",     summary.performanceRating);
        assertEquals("Keep training.", summary.closingMessage);
    }

    @Test
    void accuracy_rate_of_one_is_stored_exactly() {
        GameSummary summary = new GameSummary(500, 5, 5, 0, 1.0, "ANALYST", "Good instincts.");
        assertEquals(1.0, summary.accuracyRate, 0.0);
    }
}
