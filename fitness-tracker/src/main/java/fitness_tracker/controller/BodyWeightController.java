package fitness_tracker.controller;

import fitness_tracker.entity.BodyWeight;
import fitness_tracker.service.BodyWeightService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/body-weight")  // 這個 Controller 底下所有路徑都以 /body-weight 開頭
public class BodyWeightController {

    private final BodyWeightService service;

    public BodyWeightController(BodyWeightService service) {
        this.service = service;
    }

    // GET /body-weight → 顯示體重紀錄頁面
    @GetMapping
    public String index(Model model) {
        model.addAttribute("records", service.findAll());
        return "body-weight/index";  // → templates/body-weight/index.html
    }

    // POST /body-weight → 儲存新體重紀錄
    @PostMapping
    public String save(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedDate,
            @RequestParam Double weightKg,
            @RequestParam(required = false) String timeOfDay,
            @RequestParam(required = false) String note) {

        BodyWeight bodyWeight = new BodyWeight();
        bodyWeight.setRecordedDate(recordedDate);
        bodyWeight.setWeightKg(weightKg);
        bodyWeight.setTimeOfDay(timeOfDay);
        bodyWeight.setNote(note);
        service.save(bodyWeight);

        // redirect：儲存後跳回列表頁（避免重新整理時重複送出表單）
        return "redirect:/body-weight";
    }

    // POST /body-weight/delete/{id} → 刪除指定紀錄
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/body-weight";
    }
}
