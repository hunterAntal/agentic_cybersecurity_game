package agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefenderAgentTest {

    private DefenderAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DefenderAgent();
    }

    // --- computeEffectiveness: SUPER_EFFECTIVE cases ---

    @Test
    void block_against_phishing_is_super_effective() {
        assertEquals("SUPER_EFFECTIVE", agent.computeEffectiveness("BLOCK", "PHISHING"));
    }

    @Test
    void patch_against_bruteforce_is_super_effective() {
        assertEquals("SUPER_EFFECTIVE", agent.computeEffectiveness("PATCH", "BRUTEFORCE"));
    }

    @Test
    void scan_against_malware_is_super_effective() {
        assertEquals("SUPER_EFFECTIVE", agent.computeEffectiveness("SCAN", "MALWARE"));
    }

    // --- computeEffectiveness: WEAK cases ---

    @Test
    void patch_against_phishing_is_weak() {
        assertEquals("WEAK", agent.computeEffectiveness("PATCH", "PHISHING"));
    }

    @Test
    void scan_against_phishing_is_weak() {
        assertEquals("WEAK", agent.computeEffectiveness("SCAN", "PHISHING"));
    }

    @Test
    void block_against_bruteforce_is_weak() {
        assertEquals("WEAK", agent.computeEffectiveness("BLOCK", "BRUTEFORCE"));
    }

    @Test
    void scan_against_bruteforce_is_weak() {
        assertEquals("WEAK", agent.computeEffectiveness("SCAN", "BRUTEFORCE"));
    }

    @Test
    void patch_against_malware_is_weak() {
        assertEquals("WEAK", agent.computeEffectiveness("PATCH", "MALWARE"));
    }

    // --- computeEffectiveness: NORMAL case ---

    @Test
    void block_against_malware_is_normal() {
        assertEquals("NORMAL", agent.computeEffectiveness("BLOCK", "MALWARE"));
    }

    // --- computeEffectiveness: case-insensitive threat input ---

    @Test
    void block_against_lowercase_phishing_is_super_effective() {
        assertEquals("SUPER_EFFECTIVE", agent.computeEffectiveness("BLOCK", "phishing"));
    }

    @Test
    void patch_against_lowercase_bruteforce_is_super_effective() {
        assertEquals("SUPER_EFFECTIVE", agent.computeEffectiveness("PATCH", "bruteforce"));
    }

    @Test
    void scan_against_lowercase_malware_is_super_effective() {
        assertEquals("SUPER_EFFECTIVE", agent.computeEffectiveness("SCAN", "malware"));
    }

    // --- computeEffectiveness: unknown threat type ---

    @Test
    void any_move_against_unknown_threat_is_normal() {
        assertEquals("NORMAL", agent.computeEffectiveness("PATCH", "UNKNOWN"));
        assertEquals("NORMAL", agent.computeEffectiveness("SCAN",  "UNKNOWN"));
        assertEquals("NORMAL", agent.computeEffectiveness("BLOCK", "UNKNOWN"));
    }

    // --- recommendMove ---

    @Test
    void recommends_block_for_phishing() {
        assertEquals("BLOCK", agent.recommendMove("PHISHING"));
    }

    @Test
    void recommends_patch_for_bruteforce() {
        assertEquals("PATCH", agent.recommendMove("BRUTEFORCE"));
    }

    @Test
    void recommends_scan_for_malware() {
        assertEquals("SCAN", agent.recommendMove("MALWARE"));
    }

    @Test
    void recommends_scan_for_unknown_threat() {
        // SCAN has the highest utility for an unknown threat:
        // PATCH: 0.1 - 0.30 - 0.05 = -0.25
        // SCAN:  0.1 - 0.10 - 0.10 = -0.10  ← best
        // BLOCK: 0.1 - 0.20 - 0.20 = -0.30
        assertEquals("SCAN", agent.recommendMove("UNKNOWN"));
    }
}
