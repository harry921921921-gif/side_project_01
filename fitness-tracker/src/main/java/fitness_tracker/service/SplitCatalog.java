package fitness_tracker.service;

import java.util.List;
import java.util.Map;

// 課表分化的唯一定義來源：「一週幾天」→ 每天的名稱、動作篩選條件（movement 或 bodyPart 擇一）、
// 該天的主項清單（給 AI 教練快照文字與新手/老手重量計算用）。
// WorkoutPlanService 用篩選條件去 Exercise 表動態選動作；TrainingPlanService 只需要名稱/主項，
// 兩邊共用同一份定義，不再各自維護一份會走鐘的複製品。
public final class SplitCatalog {

    public record DaySplitDef(String name, List<String> movements, List<String> bodyParts, List<String> mainLifts) {

        public static DaySplitDef byMovement(String name, List<String> mainLifts, String... movements) {
            return new DaySplitDef(name, List.of(movements), List.of(), mainLifts);
        }

        public static DaySplitDef byBodyPart(String name, List<String> mainLifts, String... bodyParts) {
            return new DaySplitDef(name, List.of(), List.of(bodyParts), mainLifts);
        }
    }

    public static final Map<Integer, List<DaySplitDef>> SPLITS = Map.of(
            1, List.of(
                    DaySplitDef.byBodyPart("全身", List.of("深蹲", "臥推"), "胸", "背", "腿", "肩", "手臂")
            ),
            2, List.of(
                    DaySplitDef.byMovement("上半身", List.of("臥推", "肩推"), "PUSH", "PULL"),
                    DaySplitDef.byMovement("下半身", List.of("深蹲"), "LEGS")
            ),
            3, List.of(
                    DaySplitDef.byMovement("推日", List.of("臥推", "肩推"), "PUSH"),
                    DaySplitDef.byMovement("拉日", List.of("硬舉"), "PULL"),
                    DaySplitDef.byMovement("腿日", List.of("深蹲"), "LEGS")
            ),
            4, List.of(
                    DaySplitDef.byMovement("上肢 A", List.of("臥推", "肩推"), "PUSH", "PULL"),
                    DaySplitDef.byMovement("下肢 A", List.of("深蹲"), "LEGS"),
                    DaySplitDef.byMovement("上肢 B", List.of("肩推", "臥推"), "PUSH", "PULL"),
                    DaySplitDef.byMovement("下肢 B", List.of("硬舉"), "LEGS")
            ),
            5, List.of(
                    DaySplitDef.byBodyPart("胸", List.of("臥推"), "胸"),
                    DaySplitDef.byBodyPart("背", List.of("硬舉"), "背"),
                    DaySplitDef.byBodyPart("腿", List.of("深蹲"), "腿"),
                    DaySplitDef.byBodyPart("肩", List.of("肩推"), "肩"),
                    DaySplitDef.byBodyPart("手臂", List.of(), "手臂")
            ),
            6, List.of(
                    DaySplitDef.byMovement("推 A", List.of("臥推", "肩推"), "PUSH"),
                    DaySplitDef.byMovement("拉 A", List.of("硬舉"), "PULL"),
                    DaySplitDef.byMovement("腿 A", List.of("深蹲"), "LEGS"),
                    DaySplitDef.byMovement("推 B", List.of("肩推", "臥推"), "PUSH"),
                    DaySplitDef.byMovement("拉 B", List.of("硬舉"), "PULL"),
                    DaySplitDef.byMovement("腿 B", List.of("深蹲"), "LEGS")
            )
    );

    // 依「一週幾天」取分化，超過 6 天沿用 6 天那份（跟原本 TrainingPlanService 的 clamp 邏輯一致）
    public static List<DaySplitDef> forDays(int days) {
        int d = Math.min(Math.max(days, 1), 6);
        return SPLITS.getOrDefault(d, SPLITS.get(3));
    }

    private SplitCatalog() {}
}
