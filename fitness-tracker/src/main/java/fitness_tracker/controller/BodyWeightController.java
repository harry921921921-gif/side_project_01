package fitness_tracker.controller;

import fitness_tracker.entity.BodyWeight;
import fitness_tracker.service.BodyWeightService;
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

    public BodyWeightController(BodyWeightService service) {
        this.service = service;
    }

    @GetMapping
    public String index(Model model) {
        List<BodyWeight> records = service.findAll();
        model.addAttribute("records", records);

        // 直接傳 List<Map>，Thymeleaf 自動轉成 JS array
        List<Map<String, Object>> chartData = new ArrayList<>();
        for (BodyWeight r : records) {
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
        service.save(bw);
        return REDIRECT;
    }

    @PostMapping("/update/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedDate,
            @RequestParam Double weightKg,
            @RequestParam(required = false) String timeOfDay,
            @RequestParam(required = false) Double bodyFatPct,
            @RequestParam(required = false) Double skeletalMuscleKg,
            @RequestParam(required = false) String note) {

        service.findById(id).ifPresent(bw -> {
            bw.setRecordedDate(recordedDate);
            bw.setWeightKg(weightKg);
            bw.setTimeOfDay(timeOfDay);
            bw.setBodyFatPct(bodyFatPct);
            bw.setSkeletalMuscleKg(skeletalMuscleKg);
            bw.setNote(note);
            service.save(bw);
        });
        return REDIRECT;
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return REDIRECT;
    }
}
