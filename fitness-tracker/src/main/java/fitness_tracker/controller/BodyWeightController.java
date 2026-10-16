package fitness_tracker.controller;

import fitness_tracker.entity.BodyWeight;
import fitness_tracker.entity.User;
import fitness_tracker.service.BodyWeightService;
import fitness_tracker.service.CurrentUserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/body-weight")
public class BodyWeightController {

    private static final String REDIRECT = "redirect:/body-weight";
    private final BodyWeightService service;
    private final CurrentUserService currentUserService;

    public BodyWeightController(BodyWeightService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    private static final int PAGE_SIZE = 30;

    @GetMapping
    public String index(@RequestParam(defaultValue = "0") int page, Model model) {
        // 全部歷史都要拿來給圖表/統計用（趨勢線、最低/平均/最高本來就要看全部紀錄，不能只看這一頁）
        List<BodyWeight> all = service.findAll(currentUserService.getCurrentUser());

        // 歷史紀錄表格才分頁：一直往下長、又完全沒有分頁的話，記錄久了一頁會塞進上百列
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int from = currentPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, all.size());
        List<BodyWeight> records = all.isEmpty() ? List.of() : all.subList(from, to);

        model.addAttribute("records", records);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalRecords", all.size());

        // 直接傳 List<Map>，Thymeleaf 自動轉成 JS array
        List<Map<String, Object>> chartData = new ArrayList<>();
        for (BodyWeight r : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r.getRecordedDate().toString());
            m.put("weight", r.getWeightKg());
            m.put("timeOfDay", r.getTimeOfDay() != null ? r.getTimeOfDay() : "");
            m.put("bodyFat", r.getBodyFatPct());
            m.put("muscle", r.getSkeletalMuscleKg());
            chartData.add(m);
        }
        model.addAttribute("chartData", chartData);
        return "body-weight/index";
    }

    @PostMapping
    public String save(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedDate,
            @RequestParam Double weightKg,
            @RequestParam(required = false) String timeOfDay,
            @RequestParam(required = false) Double bodyFatPct,
            @RequestParam(required = false) Double skeletalMuscleKg,
            @RequestParam(required = false) String note) {

        BodyWeight bw = new BodyWeight();
        bw.setRecordedDate(recordedDate);
        bw.setWeightKg(weightKg);
        bw.setTimeOfDay(timeOfDay);
        bw.setBodyFatPct(bodyFatPct);
        bw.setSkeletalMuscleKg(skeletalMuscleKg);
        bw.setNote(note);
        service.save(bw, currentUserService.getCurrentUser());
        return REDIRECT;
    }

    // 編輯/刪除都帶著目前在第幾頁一起送出，處理完導回同一頁，不會因為改一筆資料就被彈回第 1 頁
    @PostMapping("/update/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedDate,
            @RequestParam Double weightKg,
            @RequestParam(required = false) String timeOfDay,
            @RequestParam(required = false) Double bodyFatPct,
            @RequestParam(required = false) Double skeletalMuscleKg,
            @RequestParam(required = false) String note,
            @RequestParam(defaultValue = "0") int page) {

        User user = currentUserService.getCurrentUser();
        service.findById(id, user).ifPresent(bw -> {
            bw.setRecordedDate(recordedDate);
            bw.setWeightKg(weightKg);
            bw.setTimeOfDay(timeOfDay);
            bw.setBodyFatPct(bodyFatPct);
            bw.setSkeletalMuscleKg(skeletalMuscleKg);
            bw.setNote(note);
            service.save(bw);
        });
        return REDIRECT + "?page=" + page;
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, @RequestParam(defaultValue = "0") int page) {
        service.delete(id, currentUserService.getCurrentUser());
        return REDIRECT + "?page=" + page;
    }
}
