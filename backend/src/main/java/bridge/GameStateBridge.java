package bridge;

import agents.BruteForceAgent;
import agents.DefenderAgent;
import agents.MalwarePropagationAgent;
import agents.MonitoringScoringAgent;
import agents.PhishingAttackAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import game.GameFlowController;
import game.GameSummary;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

public class GameStateBridge extends WebSocketServer {

    private static GameStateBridge instance;

    public static GameStateBridge getInstance() {
        return instance;
    }

    private volatile String lastOutcomeType;
    private volatile String lastMoveUsed;
    private volatile String lastThreatType;
    private volatile boolean lastHelpUsed;
    private volatile boolean lastIsRetry;

    private volatile int lastXpAwarded;
    private volatile int totalXP;
    private volatile int wavesCompleted;
    private volatile double accuracyRate;

    private final AtomicReference<DefenderAgent> defenderAgent = new AtomicReference<>();
    private final AtomicReference<MonitoringScoringAgent> scoringAgent = new AtomicReference<>();
    private final AtomicReference<PhishingAttackAgent> phishingAgent = new AtomicReference<>();
    private final AtomicReference<BruteForceAgent> bruteForceAgent = new AtomicReference<>();
    private final AtomicReference<MalwarePropagationAgent> malwareAgent = new AtomicReference<>();

    private volatile WebSocket activeConn;
    private volatile GameFlowController activeController;

    private final ObjectMapper mapper = new ObjectMapper();

    public GameStateBridge(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
        instance = this;
    }

    public void registerDefender(DefenderAgent agent) {
        defenderAgent.set(agent);
    }

    public void registerScoring(MonitoringScoringAgent agent) {
        scoringAgent.set(agent);
    }

    public DefenderAgent getDefenderAgent() {
        return defenderAgent.get();
    }

    public MonitoringScoringAgent getScoringAgent() {
        return scoringAgent.get();
    }

    public void registerPhishing(PhishingAttackAgent agent) {
        phishingAgent.set(agent);
    }

    public void registerBruteForce(BruteForceAgent agent) {
        bruteForceAgent.set(agent);
    }

    public void registerMalware(MalwarePropagationAgent agent) {
        malwareAgent.set(agent);
    }

    public PhishingAttackAgent getPhishingAgent() {
        return phishingAgent.get();
    }

    public BruteForceAgent getBruteForceAgent() {
        return bruteForceAgent.get();
    }

    public MalwarePropagationAgent getMalwareAgent() {
        return malwareAgent.get();
    }

    public synchronized void writeDefenderOutcome(String outcomeType, String move,
                                                   String threat, boolean helpUsed,
                                                   boolean isRetry) {
        this.lastOutcomeType = outcomeType;
        this.lastMoveUsed = move;
        this.lastThreatType = threat;
        this.lastHelpUsed = helpUsed;
        this.lastIsRetry = isRetry;
    }

    public synchronized void writeScoreState(int xpAwarded, int totalXP,
                                              int wavesCompleted, double accuracyRate) {
        this.lastXpAwarded = xpAwarded;
        this.totalXP = totalXP;
        this.wavesCompleted = wavesCompleted;
        this.accuracyRate = accuracyRate;
        broadcastScoreUpdate(xpAwarded, totalXP, wavesCompleted, accuracyRate);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("[Bridge] Client connected: " + conn.getRemoteSocketAddress());
        activeConn = conn;

        Thread gameThread = new Thread(() -> {
            waitForAgents();
            GameFlowController controller = new GameFlowController(this);
            activeController = controller;
            controller.run();
        }, "game-flow-thread");
        gameThread.setDaemon(true);
        gameThread.start();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[Bridge] Client disconnected");
        if (activeController != null) {
            activeController.shutdown();
        }
        activeConn = null;
        activeController = null;
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            var node = mapper.readTree(message);
            String type = node.get("type").asText();
            if ("PLAYER_INPUT".equals(type)) {
                String input = node.get("input").asText().toUpperCase().trim();
                System.out.println("[Bridge] Player input: " + input);
                if (activeController != null) {
                    activeController.receiveInput(input);
                }
            }
        } catch (Exception e) {
            System.err.println("[Bridge] Failed to parse message: " + message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[Bridge] WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[Bridge] WebSocket server started on port 8887");
    }

    public void sendDialog(String speaker, String text) {
        try {
            String msg = mapper.writeValueAsString(
                    java.util.Map.of("type", "DIALOG", "speaker", speaker, "text", text));
            sendToActive(msg);
        } catch (Exception e) {
            System.err.println("[Bridge] sendDialog error: " + e.getMessage());
        }
    }

    private void broadcastScoreUpdate(int xpAwarded, int totalXP,
                                       int wavesCompleted, double accuracyRate) {
        try {
            String msg = mapper.writeValueAsString(java.util.Map.of(
                    "type", "SCORE_UPDATE",
                    "xpAwarded", xpAwarded,
                    "totalXP", totalXP,
                    "wavesCompleted", wavesCompleted,
                    "accuracyRate", accuracyRate));
            sendToActive(msg);
        } catch (Exception e) {
            System.err.println("[Bridge] broadcastScoreUpdate error: " + e.getMessage());
        }
    }

    public void sendGameOver(GameSummary summary) {
        try {
            String msg = mapper.writeValueAsString(java.util.Map.of(
                    "type", "GAME_OVER",
                    "totalXP", summary.totalXP,
                    "wavesCompleted", summary.wavesCompleted,
                    "correctFirstAttempts", summary.correctFirstAttempts,
                    "helpUsedCount", summary.helpUsedCount,
                    "accuracyRate", summary.accuracyRate,
                    "performanceRating", summary.performanceRating,
                    "closingMessage", summary.closingMessage));
            sendToActive(msg);
        } catch (Exception e) {
            System.err.println("[Bridge] sendGameOver error: " + e.getMessage());
        }
    }

    private void sendToActive(String msg) {
        WebSocket conn = activeConn;
        if (conn != null && conn.isOpen()) {
            conn.send(msg);
        }
    }

    private void waitForAgents() {
        int attempts = 0;
        while ((defenderAgent.get() == null || scoringAgent.get() == null) && attempts < 50) {
            try {
                Thread.sleep(200);
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (defenderAgent.get() == null || scoringAgent.get() == null) {
            System.err.println("[Bridge] WARNING: agents not ready after timeout");
        }
    }
}
