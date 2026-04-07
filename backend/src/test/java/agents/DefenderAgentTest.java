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
}
