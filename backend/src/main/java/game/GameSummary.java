package game;

public class GameSummary {
    public final int totalXP;
    public final int wavesCompleted;
    public final int correctFirstAttempts;
    public final int helpUsedCount;
    public final double accuracyRate;
    public final String performanceRating;
    public final String closingMessage;

    public GameSummary(int totalXP, int wavesCompleted, int correctFirstAttempts,
                       int helpUsedCount, double accuracyRate,
                       String performanceRating, String closingMessage) {
        this.totalXP = totalXP;
        this.wavesCompleted = wavesCompleted;
        this.correctFirstAttempts = correctFirstAttempts;
        this.helpUsedCount = helpUsedCount;
        this.accuracyRate = accuracyRate;
        this.performanceRating = performanceRating;
        this.closingMessage = closingMessage;
    }
}
