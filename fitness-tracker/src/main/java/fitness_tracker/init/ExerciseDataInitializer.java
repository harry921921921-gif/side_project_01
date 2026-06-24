package fitness_tracker.init;

import fitness_tracker.entity.Exercise;
import fitness_tracker.service.ExerciseService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 應用程式啟動時自動執行一次。
 * 若 exercise 資料表是空的，就寫入所有預設動作。
 * 之後每次啟動都會先確認是否已有資料，所以不會重複寫入。
 */
@Component
public class ExerciseDataInitializer implements CommandLineRunner {

    private final ExerciseService exerciseService;

    public ExerciseDataInitializer(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @Override
    public void run(String... args) {
        if (exerciseService.hasData()) return; // 已有資料就跳過

        List<Exercise> presets = List.of(

            // ── 胸部 (Chest) ───────────────────────────────────
            new Exercise("臥推",           "胸", "COMPOUND"),
            new Exercise("上斜臥推",       "胸", "COMPOUND"),
            new Exercise("下斜臥推",       "胸", "COMPOUND"),
            new Exercise("啞鈴臥推",       "胸", "COMPOUND"),
            new Exercise("啞鈴上斜臥推",   "胸", "COMPOUND"),
            new Exercise("胸推機",         "胸", "COMPOUND"),
            new Exercise("啞鈴飛鳥",       "胸", "ISOLATION"),
            new Exercise("繩索夾胸",       "胸", "ISOLATION"),
            new Exercise("蝴蝶機夾胸",     "胸", "ISOLATION"),

            // ── 背部 (Back) ────────────────────────────────────
            new Exercise("硬舉",           "背", "COMPOUND"),
            new Exercise("引體向上",       "背", "COMPOUND"),
            new Exercise("滑輪下拉",       "背", "COMPOUND"),
            new Exercise("正手滑輪下拉",   "背", "COMPOUND"),
            new Exercise("槓鈴划船",       "背", "COMPOUND"),
            new Exercise("坐姿滑輪划船",   "背", "COMPOUND"),
            new Exercise("啞鈴單臂划船",   "背", "COMPOUND"),
            new Exercise("T槓划船",        "背", "COMPOUND"),
            new Exercise("直臂下壓",       "背", "ISOLATION"),

            // ── 腿部 (Legs) ────────────────────────────────────
            new Exercise("深蹲",           "腿", "COMPOUND"),
            new Exercise("前蹲",           "腿", "COMPOUND"),
            new Exercise("羅馬尼亞硬舉",   "腿", "COMPOUND"),
            new Exercise("腿推機",         "腿", "COMPOUND"),
            new Exercise("保加利亞分腿蹲", "腿", "COMPOUND"),
            new Exercise("弓步蹲",         "腿", "COMPOUND"),
            new Exercise("腿彎舉",         "腿", "ISOLATION"),
            new Exercise("腿伸展",         "腿", "ISOLATION"),
            new Exercise("小腿提升",       "腿", "ISOLATION"),
            new Exercise("坐姿小腿提升",   "腿", "ISOLATION"),

            // ── 肩部 (Shoulders) ───────────────────────────────
            new Exercise("槓鈴肩推",       "肩", "COMPOUND"),
            new Exercise("啞鈴肩推",       "肩", "COMPOUND"),
            new Exercise("阿諾德推舉",     "肩", "COMPOUND"),
            new Exercise("啞鈴側舉",       "肩", "ISOLATION"),
            new Exercise("繩索側舉",       "肩", "ISOLATION"),
            new Exercise("啞鈴前舉",       "肩", "ISOLATION"),
            new Exercise("反向飛鳥",       "肩", "ISOLATION"),
            new Exercise("面拉",           "肩", "ISOLATION"),

            // ── 手臂 (Arms) ────────────────────────────────────
            new Exercise("槓鈴彎舉",       "手臂", "ISOLATION"),
            new Exercise("啞鈴彎舉",       "手臂", "ISOLATION"),
            new Exercise("鎚式彎舉",       "手臂", "ISOLATION"),
            new Exercise("斜托彎舉",       "手臂", "ISOLATION"),
            new Exercise("繩索彎舉",       "手臂", "ISOLATION"),
            new Exercise("窄距臥推",       "手臂", "COMPOUND"),
            new Exercise("三頭下壓",       "手臂", "ISOLATION"),
            new Exercise("繩索三頭下壓",   "手臂", "ISOLATION"),
            new Exercise("過頭三頭伸展",   "手臂", "ISOLATION"),
            new Exercise("雙槓撐體",       "手臂", "COMPOUND"),

            // ── 核心 (Core) ────────────────────────────────────
            new Exercise("棒式",           "核心", "ISOLATION"),
            new Exercise("捲腹",           "核心", "ISOLATION"),
            new Exercise("仰臥起坐",       "核心", "ISOLATION"),
            new Exercise("懸垂舉腿",       "核心", "ISOLATION"),
            new Exercise("俄羅斯轉體",     "核心", "ISOLATION"),
            new Exercise("滾輪",           "核心", "COMPOUND"),
            new Exercise("側棒式",         "核心", "ISOLATION")
        );

        exerciseService.saveAll(presets);
        System.out.println("[ExerciseDataInitializer] 已寫入 " + presets.size() + " 筆預設動作");
    }
}
