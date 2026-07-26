package fitness_tracker.service;

// 依運動科學（NSCA / Epley 反推）由「%1RM 區間 + 組數 + 目標 RIR」算出合理處方，
// 次數不再寫死，避免「90% 卻要做 6 下」這種強度/次數矛盾。
public class StrengthPrescription {

    public record Prescription(double weightKg, int sets, int repsLow, int repsHigh, double workPct, int rir) {}

    public enum Phase {
        ANATOMICAL_ADAPTATION(0.62, 0.68, 3, 3),  // 解剖適應
        HYPERTROPHY          (0.70, 0.80, 4, 2),  // 肌肥大
        MAX_STRENGTH         (0.85, 0.875, 4, 1); // 最大力量（修正：不再 90%x5x3-6）

        final double pctLo, pctHi;
        final int sets, targetRir;
        Phase(double lo, double hi, int sets, int rir) { this.pctLo = lo; this.pctHi = hi; this.sets = sets; this.targetRir = rir; }
    }

    // Epley 反推：某 %1RM 練到力竭(RIR 0)大約能做幾下
    private static double maxRepsAt(double pct) { return 30.0 * (1.0 / pct - 1.0); }

    // 目標 RIR 下的實際次數 = 力竭次數 - 保留次數（至少 1）
    private static int repsAtRir(double pct, int rir) { return Math.max((int) Math.round(maxRepsAt(pct)) - rir, 1); }

    // 進位到最接近的 2.5kg
    private static double roundToPlate(double weight) { return Math.round(weight / 2.5) * 2.5; }

    public static Prescription prescribe(double oneRepMax, Phase phase, int targetRir) {
        double workPct = (phase.pctLo + phase.pctHi) / 2.0;
        double weight  = roundToPlate(oneRepMax * workPct);
        int repsLow  = repsAtRir(phase.pctHi, targetRir);   // 較重端 -> 次數少
        int repsHigh = repsAtRir(phase.pctLo, targetRir);   // 較輕端 -> 次數多
        return new Prescription(weight, phase.sets, repsLow, repsHigh, workPct, targetRir);
    }

    public static Prescription prescribe(double oneRepMax, Phase phase) {
        return prescribe(oneRepMax, phase, phase.targetRir);
    }
}
