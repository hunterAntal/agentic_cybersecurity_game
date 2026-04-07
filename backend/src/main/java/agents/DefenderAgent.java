package agents;

import bridge.GameStateBridge;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

public class DefenderAgent extends Agent {

    private static final String[] MOVES = {"PATCH", "SCAN", "BLOCK"};

    private double expectedReduction(String move, String threat) {
        return switch (threat.toUpperCase()) {
            case "PHISHING"   -> move.equals("BLOCK") ? 1.0 : 0.1;
            case "BRUTEFORCE" -> move.equals("PATCH") ? 1.0 : 0.1;
            case "MALWARE"    -> move.equals("SCAN")  ? 1.0 : 0.1;
            default -> 0.1;
        };
    }

    private double cost(String move) {
        return switch (move) {
            case "PATCH"   -> 0.30;
            case "SCAN"    -> 0.10;
            case "BLOCK"   -> 0.20;
            default -> 1.0;
        };
    }

    private double falsePositiveRisk(String move) {
        return switch (move) {
            case "PATCH"   -> 0.05;
            case "SCAN"    -> 0.10;
            case "BLOCK"   -> 0.20;
            default -> 1.0;
        };
    }

    private double utility(String move, String threat) {
        return expectedReduction(move, threat) - cost(move) - falsePositiveRisk(move);
    }

    public String recommendMove(String threatType) {
        String best = MOVES[0];
        double bestScore = utility(MOVES[0], threatType);
        for (int i = 1; i < MOVES.length; i++) {
            double score = utility(MOVES[i], threatType);
            if (score > bestScore) {
                bestScore = score;
                best = MOVES[i];
            }
        }
        return best;
    }

    public String evaluateMove(String move, String threatType,
                                boolean helpUsed, boolean isRetry) {
        String result = computeEffectiveness(move, threatType);

        GameStateBridge bridge = GameStateBridge.getInstance();
        if (bridge != null) {
            bridge.writeDefenderOutcome(result, move, threatType, helpUsed, isRetry);
        }

        return result;
    }

    String computeEffectiveness(String move, String threat) {
        boolean superEffective = switch (threat.toUpperCase()) {
            case "PHISHING"   -> move.equals("BLOCK");
            case "BRUTEFORCE" -> move.equals("PATCH");
            case "MALWARE"    -> move.equals("SCAN");
            default -> false;
        };
        if (superEffective) return "SUPER_EFFECTIVE";

        boolean isWeakPairing = switch (threat.toUpperCase()) {
            case "PHISHING"   -> move.equals("PATCH") || move.equals("SCAN");
            case "BRUTEFORCE" -> move.equals("BLOCK") || move.equals("SCAN");
            case "MALWARE"    -> move.equals("PATCH");
            default -> false;
        };

        return isWeakPairing ? "WEAK" : "NORMAL";
    }

    @Override
    protected void setup() {
        System.out.println("[DefenderAgent] Starting up: " + getAID().getName());

        GameStateBridge bridge = GameStateBridge.getInstance();
        if (bridge != null) {
            bridge.registerDefender(this);
        }

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("defender");
        sd.setName("DefenderAgent");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("[DefenderAgent] Registered with DF as service type 'defender'");
        } catch (FIPAException e) {
            System.err.println("[DefenderAgent] DF registration failed: " + e.getMessage());
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException ignored) {}
        System.out.println("[DefenderAgent] Shutting down.");
    }
}
