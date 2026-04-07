package game;

import agents.DefenderAgent;
import agents.MonitoringScoringAgent;
import bridge.GameStateBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameFlowControllerTest {

    @Mock
    GameStateBridge mockBridge;

    // --- shutdown ---

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void shutdown_stops_the_controller_thread() throws InterruptedException {
        GameFlowController controller = new GameFlowController(mockBridge);
        Thread thread = new Thread(controller);
        thread.setDaemon(true);
        thread.start();

        controller.shutdown();
        thread.join(2000);

        assertFalse(thread.isAlive(), "Controller thread should stop after shutdown()");
    }

    // --- READY → FINISH flow ---

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void finish_command_after_ready_triggers_game_over() throws InterruptedException {
        MonitoringScoringAgent scoring = new MonitoringScoringAgent();

        when(mockBridge.getScoringAgent()).thenReturn(scoring);

        GameFlowController controller = new GameFlowController(mockBridge);
        Thread thread = new Thread(controller);
        thread.setDaemon(true);
        thread.start();

        // Wait for playIntro (playIntro() has Thread.sleep(200); 400 ms gives buffer for scheduling overhead)
        Thread.sleep(400);
        controller.receiveInput("READY");

        // Allow session start and startWave dialogs (no-ops on mock) to complete on the game thread
        Thread.sleep(200);
        controller.receiveInput("FINISH");

        // Allow handleFinish to invoke sendGameOver on the game thread
        Thread.sleep(200);

        verify(mockBridge, atLeastOnce()).sendGameOver(any(GameSummary.class));

        controller.shutdown();
        thread.join(1000);
    }

    // --- HELP / HINT command ---

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void hint_command_sends_recommendation_dialog() throws InterruptedException {
        MonitoringScoringAgent scoring = new MonitoringScoringAgent();
        DefenderAgent defender = new DefenderAgent();

        when(mockBridge.getScoringAgent()).thenReturn(scoring);
        when(mockBridge.getDefenderAgent()).thenReturn(defender);

        GameFlowController controller = new GameFlowController(mockBridge);
        Thread thread = new Thread(controller);
        thread.setDaemon(true);
        thread.start();

        Thread.sleep(400); // playIntro() has Thread.sleep(200); 400 ms gives buffer for scheduling overhead
        controller.receiveInput("READY");
        Thread.sleep(200); // allow startWave() dialog calls to complete on the game thread

        controller.receiveInput("HINT");
        Thread.sleep(200); // allow handleHelp() dialog calls to complete on the game thread

        // HINT handling always sends "Analyzing threat..." followed by context-specific hints
        verify(mockBridge, atLeastOnce()).sendDialog(eq("DEFENDER"), eq("Analyzing threat..."));

        controller.shutdown();
        thread.join(1000);
    }

    // --- valid move (SUPER_EFFECTIVE) ---

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void super_effective_move_grants_xp_and_prompts_next() throws InterruptedException {
        MonitoringScoringAgent scoring = spy(new MonitoringScoringAgent());
        DefenderAgent defender = spy(new DefenderAgent());

        when(mockBridge.getScoringAgent()).thenReturn(scoring);
        when(mockBridge.getDefenderAgent()).thenReturn(defender);

        // Force a known threat so we can send the matching super-effective move
        doReturn("SUPER_EFFECTIVE").when(defender).evaluateMove(anyString(), anyString(), anyBoolean(), anyBoolean());
        doReturn(150).when(scoring).processOutcome(eq("SUPER_EFFECTIVE"), anyBoolean(), anyBoolean());

        GameFlowController controller = new GameFlowController(mockBridge);
        Thread thread = new Thread(controller);
        thread.setDaemon(true);
        thread.start();

        Thread.sleep(400); // playIntro() has Thread.sleep(200); 400 ms gives buffer for scheduling overhead
        controller.receiveInput("READY");
        Thread.sleep(200); // allow startWave() dialog calls to complete on the game thread

        controller.receiveInput("PATCH"); // move (actual super-effectiveness is forced above)
        Thread.sleep(200); // allow sendOutcomeDialog() to complete on the game thread

        // After a successful (non-WEAK) move the controller should prompt for NEXT or FINISH
        verify(mockBridge, atLeastOnce()).sendDialog(eq("DEFENDER"),
                contains("Type NEXT for the next threat or FINISH to end your session."));

        controller.shutdown();
        thread.join(1000);
    }

    // --- weak move triggers XP deduction and retry prompt ---

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void weak_move_deducts_xp_and_prompts_retry() throws InterruptedException {
        MonitoringScoringAgent scoring = spy(new MonitoringScoringAgent());
        DefenderAgent defender = spy(new DefenderAgent());

        when(mockBridge.getScoringAgent()).thenReturn(scoring);
        when(mockBridge.getDefenderAgent()).thenReturn(defender);

        // Force a WEAK outcome regardless of actual move/threat pairing
        doReturn("WEAK").when(defender).evaluateMove(anyString(), anyString(), anyBoolean(), anyBoolean());

        GameFlowController controller = new GameFlowController(mockBridge);
        Thread thread = new Thread(controller);
        thread.setDaemon(true);
        thread.start();

        Thread.sleep(400); // playIntro() has Thread.sleep(200); 400 ms gives buffer for scheduling overhead
        controller.receiveInput("READY");
        Thread.sleep(200); // allow startWave() dialog calls to complete on the game thread

        controller.receiveInput("PATCH");
        Thread.sleep(200); // allow sendWeakDialog() and deductXP() to complete on the game thread

        // After a WEAK move the controller should tell the player to try again
        verify(mockBridge, atLeastOnce()).sendDialog(eq("DEFENDER"), eq("Try again, recruit."));

        controller.shutdown();
        thread.join(1000);
    }

    // --- unknown command ---

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void unknown_command_sends_error_dialog() throws InterruptedException {
        MonitoringScoringAgent scoring = new MonitoringScoringAgent();

        when(mockBridge.getScoringAgent()).thenReturn(scoring);

        GameFlowController controller = new GameFlowController(mockBridge);
        Thread thread = new Thread(controller);
        thread.setDaemon(true);
        thread.start();

        Thread.sleep(400); // playIntro() has Thread.sleep(200); 400 ms gives buffer for scheduling overhead
        controller.receiveInput("READY");
        Thread.sleep(200); // allow startWave() dialog calls to complete on the game thread

        controller.receiveInput("INVALID_CMD");
        Thread.sleep(200); // allow the default switch branch to call sendDialog on the game thread

        verify(mockBridge, atLeastOnce()).sendDialog(eq("SYSTEM"), contains("Unknown command"));

        controller.shutdown();
        thread.join(1000);
    }
}
