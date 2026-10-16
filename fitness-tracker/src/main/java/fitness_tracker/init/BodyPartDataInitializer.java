package fitness_tracker.init;

import fitness_tracker.entity.BodyPart;
import fitness_tracker.service.BodyPartService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(1)
public class BodyPartDataInitializer implements CommandLineRunner {

    private final BodyPartService bodyPartService;

    public BodyPartDataInitializer(BodyPartService bodyPartService) {
        this.bodyPartService = bodyPartService;
    }

    @Override
    public void run(String... args) {
        if (!bodyPartService.hasData()) {
            List<BodyPart> presets = List.of(
                new BodyPart("胸",   1),
                new BodyPart("背",   2),
                new BodyPart("腿",   3),
                new BodyPart("肩",   4),
                new BodyPart("手臂", 5),
                new BodyPart("核心", 6),
                new BodyPart("全身", 7)
            );
            bodyPartService.saveAll(presets);
            System.out.println("[BodyPartDataInitializer] 已寫入 " + presets.size() + " 個預設訓練部位");
        }

        // 訓練計劃頁的分化名稱（推日/拉日/腿日等），讓「依課表匯入」寫進訓練紀錄的 bodyPart
        // 跟課表顯示的名稱完全一致，不用再做名稱轉換。add() 本身會擋重複，可安全每次啟動都跑。
        List<String> planSplitNames = List.of(
            "上半身", "下半身",
            "推日", "拉日", "腿日",
            "上肢 A", "下肢 A", "上肢 B", "下肢 B",
            "推 A", "拉 A", "腿 A", "推 B", "拉 B", "腿 B"
        );
        planSplitNames.forEach(bodyPartService::add);
    }
}
