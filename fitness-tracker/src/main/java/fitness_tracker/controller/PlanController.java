package fitness_tracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller  // 訓練計劃頁（靜態原型，前端 JS 計算）
public class PlanController {

    // GET /plan → templates/plan/index.html
    @GetMapping("/plan")
    public String plan() {
        return "plan/index";
    }
}
