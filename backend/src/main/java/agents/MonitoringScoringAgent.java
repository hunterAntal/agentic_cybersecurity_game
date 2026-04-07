package agents;

import bridge.GameStateBridge;
import game.GameSummary;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

public class MonitoringScoringAgent extends Agent {

    private int totalXP;
    private int wavesCompleted;
    private int correctFirstAttempts;
    private int helpUsedCount;
    private boolean sessionActive;

    private String currentThreatType;

    public synchronized void startSession() {
        totalXP = 500;
        wavesCompleted = 0;
        correctFirstAttempts = 0;
        helpUsedCount = 0;
        sessionActive = true;
        System.out.println("[MonitoringScoringAgent] Session started. Starting XP=500");
    }

    public synchronized void startWave(String threatType) {
        this.currentThreatType = threatType;
        System.out.println("[MonitoringScoringAgent] Wave started. Threat: " + threatType);
    }

    public synchronized void recordHelpUsed() {
        helpUsedCount++;
    }

    public synchronized int processOutcome(String effectiveness,
                                            boolean helpUsed,
                                            boolean isRetry) {
        int xp = computeXP(effectiveness, helpUsed, isRetry);

        totalXP += xp;
        wavesCompleted++;

        if (!isRetry) {
            correctFirstAttempts++;
        }

        double accuracy = wavesCompleted > 0
                ? (double) correctFirstAttempts / wavesCompleted
                : 0.0;

        System.out.printf("[MonitoringScoringAgent] Outcome=%s help=%b retry=%b → +%d XP (total=%d)%n",
                effectiveness, helpUsed, isRetry, xp, totalXP);

        GameStateBridge bridge = GameStateBridge.getInstance();
        if (bridge != null) {
            bridge.writeScoreState(xp, totalXP, wavesCompleted, accuracy);
        }

        return xp;
    }

    public synchronized GameSummary endSession() {
        sessionActive = false;

        double accuracy = wavesCompleted > 0
                ? (double) correctFirstAttempts / wavesCompleted
                : 0.0;

        String rating = performanceRating(totalXP);
        String closing = closingMessage(rating);

        System.out.printf("[MonitoringScoringAgent] Session ended. XP=%d Rating=%s%n",
                totalXP, rating);

        return new GameSummary(totalXP, wavesCompleted, correctFirstAttempts,
                helpUsedCount, accuracy, rating, closing);
    }

    public synchronized int deductXP(double fraction) {
        int deduction = Math.max(1, (int)(totalXP * fraction));
        totalXP = Math.max(0, totalXP - deduction);

        double accuracy = wavesCompleted > 0
                ? (double) correctFirstAttempts / wavesCompleted
                : 0.0;

        System.out.printf("[MonitoringScoringAgent] XP deducted: -%d (fraction=%.2f, remaining=%d)%n",
                deduction, fraction, totalXP);

        GameStateBridge bridge = GameStateBridge.getInstance();
        if (bridge != null) {
            bridge.writeScoreState(-deduction, totalXP, wavesCompleted, accuracy);
        }
        return totalXP;
    }

    private int computeXP(String effectiveness, boolean helpUsed, boolean isRetry) {
        if (isRetry || helpUsed) return 25;

        return switch (effectiveness.toUpperCase()) {
            case "SUPER_EFFECTIVE" -> 150;
            case "NORMAL"          -> 100;
            default -> 0;
        };
    }

    private String performanceRating(int xp) {
        if (xp <= 300)       return "RECRUIT";
        if (xp <= 600)       return "ANALYST";
        if (xp <= 900)       return "SPECIALIST";
        return "DEFENDER";
    }

    private String closingMessage(String rating) {
        return switch (rating) {
            case "RECRUIT"    -> "You are learning the basics, recruit. Keep training.";
            case "ANALYST"    -> "Solid instincts, Analyst. The network is safer with you on watch.";
            case "SPECIALIST" -> "Impressive work, Specialist. You anticipate threats before they escalate.";
            case "DEFENDER"   -> "Outstanding. You have earned the title of DEFENDER. The network is in good hands.";
            default -> "";
        };
    }

    @Override
    protected void setup() {
        System.out.println("[MonitoringScoringAgent] Starting up: " + getAID().getName());

        GameStateBridge bridge = GameStateBridge.getInstance();
        if (bridge != null) {
            bridge.registerScoring(this);
        }

        startSession();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("scoring");
        sd.setName("MonitoringScoringAgent");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("[MonitoringScoringAgent] Registered with DF as service type 'scoring'");
        } catch (FIPAException e) {
            System.err.println("[MonitoringScoringAgent] DF registration failed: " + e.getMessage());
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException ignored) {}
        System.out.println("[MonitoringScoringAgent] Shutting down.");
    }
}
