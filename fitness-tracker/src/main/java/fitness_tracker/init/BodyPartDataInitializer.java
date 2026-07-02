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
        if (bodyPartService.hasData()) return;

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
}
