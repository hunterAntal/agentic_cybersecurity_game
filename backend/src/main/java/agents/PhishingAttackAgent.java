package agents;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import bridge.GameStateBridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class PhishingAttackAgent extends Agent {

    private double[] confidences = new double[5];

    @Override
    protected void setup() {
        System.out.println("[PhishingAttackAgent] Starting up: " + getAID().getName());

        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "backend/predict_phishing.py");
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            for (int i = 0; i < confidences.length; i++) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    confidences[i] = Double.parseDouble(line.trim());
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            System.err.println("[PhishingAttackAgent] ML inference failed: " + e.getMessage());
        }
        System.out.print("[PhishingAttackAgent] Confidences:");
        for (double c : confidences) System.out.printf(" %.4f", c);
        System.out.println();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("phishing-attack");
        sd.setName("PhishingAttackAgent");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            System.err.println("[PhishingAttackAgent] DF registration failed: " + e.getMessage());
        }

        GameStateBridge bridge = GameStateBridge.getInstance();
        if (bridge != null) {
            bridge.registerPhishing(this);
        }
    }

    public double[] getConfidences() {
        return confidences;
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException ignored) {}
    }
}
