package game;

import agents.BruteForceAgent;
import agents.DefenderAgent;
import agents.MalwarePropagationAgent;
import agents.MonitoringScoringAgent;
import agents.PhishingAttackAgent;
import bridge.GameStateBridge;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GameFlowController implements Runnable {

    private static final String[] THREATS = {"PHISHING", "BRUTEFORCE", "MALWARE"};
    private static final Random RNG = new Random();
    private static final String[] PHISHING_NODES = {
        "NODE-01", "NODE-02", "NODE-03", "NODE-04",
        "NODE-05", "NODE-06", "NODE-07", "NODE-08"
    };

    private final GameStateBridge bridge;
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    private String currentThreat;
    private double currentConfidence;
    private int currentSampleIndex;
    private boolean helpUsedThisWave;
    private boolean retryThisWave;
    private boolean sessionEnded = false;

    public GameFlowController(GameStateBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void run() {
        try {
            playIntro();

            while (running) {
                while (running) {
                    String input = waitForInput();
                    if ("READY".equals(input)) break;
                    bridge.sendDialog("SYSTEM", "Type READY to begin.");
                }

                MonitoringScoringAgent scoring = bridge.getScoringAgent();
                sessionEnded = false;
                if (scoring != null) {
                    scoring.startSession();
                    bridge.writeScoreState(0, 500, 0, 0.0);
                }

                playSession();

                if (sessionEnded) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void playSession() throws InterruptedException {
        while (running) {
            startWave();
            boolean waveComplete = false;

            while (!waveComplete && running) {
                String input = waitForInput();

                switch (input) {
                    case "HELP", "HINT" -> handleHelp();
                    case "PATCH", "SCAN", "BLOCK" -> {
                        boolean complete = handleMove(input);
                        if (complete) {
                            waveComplete = true;
                            if (sessionEnded) return;
                            String next = waitForNextOrFinish();
                            if ("FINISH".equals(next)) {
                                handleFinish();
                                return;
                            }
                        }
                    }
                    case "FINISH" -> {
                        handleFinish();
                        return;
                    }
                    default -> bridge.sendDialog("SYSTEM",
                            "Unknown command. Valid commands: PATCH | SCAN | BLOCK | HINT | HELP | NEXT | FINISH");
                }
            }

            if (sessionEnded) return;
        }
    }

    public void receiveInput(String input) {
        inputQueue.add(input);
    }

    public void shutdown() {
        running = false;
        inputQueue.add("__SHUTDOWN__");
    }

    private void playIntro() throws InterruptedException {
        bridge.sendDialog("SYSTEM", "Initializing secure connection...");
        Thread.sleep(200);
        bridge.sendDialog("DEFENDER", "Welcome, recruit. I am DEFENDER.");
        bridge.sendDialog("DEFENDER", "Our network is under attack. Hostile agents are probing our systems.");
        bridge.sendDialog("DEFENDER", "Your mission is to detect these threats and neutralize them before they cause irreversible damage.");
        bridge.sendDialog("DEFENDER", "You will learn to PATCH vulnerabilities, SCAN for intrusions, BLOCK compromised nodes, and use HINT when you need guidance.");
        bridge.sendDialog("DEFENDER", "Type READY to begin.");
    }

    private void startWave() throws InterruptedException {
        currentThreat = selectThreat();
        helpUsedThisWave = false;
        retryThisWave = false;

        MonitoringScoringAgent scoring = bridge.getScoringAgent();
        if (scoring != null) {
            scoring.startWave(currentThreat);
        }

        switch (currentThreat) {
            case "PHISHING" -> {
                String phishingNode = PHISHING_NODES[RNG.nextInt(PHISHING_NODES.length)];
                bridge.sendDialog("DEFENDER", "ALERT. PHISHING AGENT detected on " + phishingNode + ".");
                bridge.sendDialog("DEFENDER", "It is sending spoofed emails to your users, attempting to steal credentials.");
            }
            case "BRUTEFORCE" -> {
                bridge.sendDialog("DEFENDER", "ALERT. BRUTE FORCE AGENT detected.");
                bridge.sendDialog("DEFENDER", "It is hammering our login systems, attempting to crack user passwords.");
            }
            case "MALWARE" -> {
                bridge.sendDialog("DEFENDER", "ALERT. MALWARE AGENT detected on the network.");
                bridge.sendDialog("DEFENDER", "It is spreading between nodes, compromising systems as it propagates.");
            }
        }

        bridge.sendDialog("DEFENDER",
                String.format("Threat confidence: %.1f%%", currentConfidence * 100));
        bridge.sendDialog("DEFENDER", "Available moves: PATCH | SCAN | BLOCK | HINT");
        bridge.sendDialog("DEFENDER", "What is your move, recruit?");
    }

    private String selectThreat() {
        String threat = THREATS[RNG.nextInt(THREATS.length)];
        currentSampleIndex = RNG.nextInt(5);

        double[] confs = getConfidencesForThreat(threat);
        currentConfidence = (confs != null && confs.length > currentSampleIndex)
                ? confs[currentSampleIndex]
                : 0.5;

        return threat;
    }

    private double[] getConfidencesForThreat(String threat) {
        return switch (threat) {
            case "PHISHING"   -> bridge.getPhishingAgent() != null ? bridge.getPhishingAgent().getConfidences() : null;
            case "BRUTEFORCE" -> bridge.getBruteForceAgent() != null ? bridge.getBruteForceAgent().getConfidences() : null;
            case "MALWARE"    -> bridge.getMalwareAgent() != null ? bridge.getMalwareAgent().getConfidences() : null;
            default -> null;
        };
    }

    private void handleHelp() throws InterruptedException {
        if (!helpUsedThisWave) {
            helpUsedThisWave = true;
            MonitoringScoringAgent scoring = bridge.getScoringAgent();
            if (scoring != null) {
                scoring.recordHelpUsed();
            }
        }

        DefenderAgent defender = bridge.getDefenderAgent();
        String rec = (defender != null) ? defender.recommendMove(currentThreat) : "BLOCK";

        bridge.sendDialog("DEFENDER", "Analyzing threat...");

        switch (currentThreat) {
            case "PHISHING" -> {
                bridge.sendDialog("DEFENDER", "PHISHING AGENT is exploiting user trust through deceptive emails.");
                bridge.sendDialog("DEFENDER", "Recommended move: BLOCK");
                bridge.sendDialog("DEFENDER", "Isolating the compromised node cuts off the attacker's foothold in the network.");
            }
            case "BRUTEFORCE" -> {
                bridge.sendDialog("DEFENDER", "BRUTE FORCE AGENT is exploiting unpatched vulnerabilities to crack credentials.");
                bridge.sendDialog("DEFENDER", "Recommended move: PATCH");
                bridge.sendDialog("DEFENDER", "Patching closes the entry point the agent is actively exploiting. No vulnerability, no attack.");
            }
            case "MALWARE" -> {
                bridge.sendDialog("DEFENDER", "MALWARE AGENT is spreading through active network traffic patterns.");
                bridge.sendDialog("DEFENDER", "Recommended move: SCAN");
                bridge.sendDialog("DEFENDER", "Anomaly detection catches the malware spreading between nodes before it propagates further.");
            }
        }

        bridge.sendDialog("DEFENDER", "What is your move, recruit?");
    }

    private boolean handleMove(String move) throws InterruptedException {
        DefenderAgent defender = bridge.getDefenderAgent();
        if (defender == null) {
            bridge.sendDialog("SYSTEM", "Defender system offline. Please restart.");
            return false;
        }

        String effectiveness = defender.evaluateMove(move, currentThreat, helpUsedThisWave, retryThisWave);

        if ("WEAK".equals(effectiveness)) {
            sendWeakDialog(move, currentThreat);
            MonitoringScoringAgent scoring = bridge.getScoringAgent();
            int remaining = (scoring != null) ? scoring.deductXP(currentConfidence) : 0;
            if (remaining <= 0) {
                handleGameOver();
                sessionEnded = true;
                return true;
            }
            bridge.sendDialog("DEFENDER",
                    String.format("XP remaining: %d. The attack remains active.", remaining));
            bridge.sendDialog("DEFENDER", "Try again, recruit.");
            bridge.sendDialog("DEFENDER", "Available moves: PATCH | SCAN | BLOCK | HINT");
            retryThisWave = true;
            return false;
        }

        MonitoringScoringAgent scoring = bridge.getScoringAgent();
        int xpAwarded = 0;
        if (scoring != null) {
            xpAwarded = scoring.processOutcome(effectiveness, helpUsedThisWave, retryThisWave);
        }

        sendOutcomeDialog(effectiveness, move, currentThreat);
        bridge.sendDialog("DEFENDER", "+" + xpAwarded + " XP");
        bridge.sendDialog("DEFENDER", "Type NEXT for the next threat or FINISH to end your session.");
        return true;
    }

    private void handleGameOver() {
        bridge.sendDialog("DEFENDER", "SYSTEM BREACH. You have been overwhelmed.");
        bridge.sendDialog("DEFENDER", "The network has fallen. XP depleted.");
        MonitoringScoringAgent scoring = bridge.getScoringAgent();
        if (scoring != null) {
            GameSummary summary = scoring.endSession();
            bridge.sendGameOver(summary);
        }
    }

    private void sendWeakDialog(String move, String threat) {
        switch (threat) {
            case "PHISHING" -> {
                if (move.equals("BLOCK")) {
                } else if (move.equals("PATCH")) {
                    bridge.sendDialog("DEFENDER", "PATCH had little effect...");
                    bridge.sendDialog("DEFENDER", "Patching vulnerabilities won't stop a social engineering attack. Target the node directly.");
                } else if (move.equals("SCAN")) {
                    bridge.sendDialog("DEFENDER", "SCAN had little effect...");
                    bridge.sendDialog("DEFENDER", "PHISHING AGENT is targeting users directly through email. You need to isolate the compromised node.");
                }
            }
            case "BRUTEFORCE" -> {
                if (move.equals("PATCH")) {
                } else if (move.equals("BLOCK")) {
                    bridge.sendDialog("DEFENDER", "BLOCK had little effect...");
                    bridge.sendDialog("DEFENDER", "BRUTE FORCE AGENT is targeting credentials, not network access. Blocking a node won't stop it.");
                } else if (move.equals("SCAN")) {
                    bridge.sendDialog("DEFENDER", "SCAN had little effect...");
                    bridge.sendDialog("DEFENDER", "BRUTE FORCE AGENT is using credential attacks, not network intrusion. Patch the vulnerability.");
                }
            }
            case "MALWARE" -> {
                if (move.equals("SCAN")) {
                } else if (move.equals("PATCH")) {
                    bridge.sendDialog("DEFENDER", "PATCH had little effect...");
                    bridge.sendDialog("DEFENDER", "MALWARE AGENT is already spreading through active connections. Scan for it first.");
                } else if (move.equals("BLOCK")) {
                    bridge.sendDialog("DEFENDER", "BLOCK had some effect but was not optimal...");
                    bridge.sendDialog("DEFENDER", "Isolating helps but SCAN would catch the spread pattern earlier and more effectively.");
                }
            }
        }
    }

    private void sendOutcomeDialog(String effectiveness, String move, String threat) {
        if ("SUPER_EFFECTIVE".equals(effectiveness)) {
            switch (threat) {
                case "PHISHING" -> {
                    bridge.sendDialog("DEFENDER", "BLOCK was super effective!");
                    bridge.sendDialog("DEFENDER", "Isolating the compromised node cut off PHISHING AGENT's foothold. Attack neutralized.");
                }
                case "BRUTEFORCE" -> {
                    bridge.sendDialog("DEFENDER", "PATCH was super effective!");
                    bridge.sendDialog("DEFENDER", "Closing the vulnerability removed BRUTE FORCE AGENT's entry point. No vulnerability, no attack.");
                }
                case "MALWARE" -> {
                    bridge.sendDialog("DEFENDER", "SCAN was super effective!");
                    bridge.sendDialog("DEFENDER", "Anomaly detection caught MALWARE AGENT spreading between nodes. Infection contained.");
                }
            }
        } else {
            bridge.sendDialog("DEFENDER", move + " was effective. Threat level reduced.");
        }
    }

    private String waitForNextOrFinish() throws InterruptedException {
        while (running) {
            String input = waitForInput();
            if ("NEXT".equals(input) || "FINISH".equals(input)) return input;
            bridge.sendDialog("SYSTEM", "Type NEXT for the next threat or FINISH to end your session.");
        }
        return "FINISH";
    }

    private void handleFinish() {
        bridge.sendDialog("DEFENDER", "Session complete. Compiling your results...");

        MonitoringScoringAgent scoring = bridge.getScoringAgent();
        if (scoring != null) {
            GameSummary summary = scoring.endSession();
            bridge.sendGameOver(summary);
        }
    }

    private String waitForInput() throws InterruptedException {
        while (running) {
            String input = inputQueue.take();
            if ("__SHUTDOWN__".equals(input)) {
                running = false;
                throw new InterruptedException("Shutdown requested");
            }
            return input;
        }
        throw new InterruptedException("Controller stopped");
    }
}
