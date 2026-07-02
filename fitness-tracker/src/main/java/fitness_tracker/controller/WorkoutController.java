package fitness_tracker.controller;

import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.service.BodyPartService;
import fitness_tracker.service.ExerciseService;
import fitness_tracker.service.WorkoutService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/workout")
public class WorkoutController {

    private final WorkoutService service;
    private final BodyPartService bodyPartService;
    private final ExerciseService exerciseService;

    public WorkoutController(WorkoutService service,
                             BodyPartService bodyPartService,
                             ExerciseService exerciseService) {
        this.service = service;
        this.bodyPartService = bodyPartService;
        this.exerciseService = exerciseService;
    }

    @GetMapping
    public String index(Model model) {
        List<WorkoutSession> sessions = service.findAll();
        model.addAttribute("sessions", sessions);
        model.addAttribute("bodyParts", bodyPartService.findAll());
        model.addAttribute("exercises", exerciseService.findAll());

        // 日曆資料：date -> [bodyPart...]（直接傳 Map，Thymeleaf 自動轉 JS 物件）
        Map<String, List<String>> calendarData = new LinkedHashMap<>();
        for (WorkoutSession s : sessions) {
            calendarData.computeIfAbsent(s.getWorkoutDate().toString(), k -> new ArrayList<>())
                        .add(s.getBodyPart() != null ? s.getBodyPart() : "");
        }
        model.addAttribute("calendarData", calendarData);

        // 給 JS 用的精簡 sessions（避免傳 JPA entity 造成序列化問題）
        List<Map<String, Object>> sessionsForJS = sessions.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("workoutDate", s.getWorkoutDate().toString());
            sm.put("bodyPart", s.getBodyPart());
            sm.put("note", s.getNote());
            List<Map<String, Object>> setsForJS = s.getSets().stream().map(set -> {
                Map<String, Object> setMap = new LinkedHashMap<>();
                setMap.put("exerciseName", set.getExerciseName());
                setMap.put("weightKg", set.getWeightKg());
                setMap.put("sets", set.getSets());
                setMap.put("reps", set.getReps());
                setMap.put("restSeconds", set.getRestSeconds());
                return setMap;
            }).collect(Collectors.toList());
            sm.put("sets", setsForJS);
            return sm;
        }).collect(Collectors.toList());
        model.addAttribute("sessionsData", sessionsForJS);

        // 給 JS 用的精簡 exercises
        List<Map<String, Object>> exercisesForJS = exerciseService.findAll().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getName());
            m.put("bodyPart", e.getBodyPart());
            return m;
        }).collect(Collectors.toList());
        model.addAttribute("exercisesData", exercisesForJS);

        return "workout/index";
    }

    @PostMapping
    public String save(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workoutDate,
            @RequestParam String bodyPart,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) List<String> exerciseNames,
            @RequestParam(required = false) List<Double> weightKgs,
            @RequestParam(required = false) List<Integer> sets,
            @RequestParam(required = false) List<Integer> reps,
            @RequestParam(required = false) List<Integer> restSeconds) {

        WorkoutSession session = new WorkoutSession();
        session.setWorkoutDate(workoutDate);
        session.setBodyPart(bodyPart);
        session.setNote(note);

        service.save(session,
                exerciseNames != null ? exerciseNames : List.of(),
                weightKgs, sets, reps, restSeconds);
        return "redirect:/workout";
    }

    @PostMapping("/update/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workoutDate,
            @RequestParam String bodyPart,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) List<String> exerciseNames,
            @RequestParam(required = false) List<Double> weightKgs,
            @RequestParam(required = false) List<Integer> sets,
            @RequestParam(required = false) List<Integer> reps,
            @RequestParam(required = false) List<Integer> restSeconds) {

        service.update(id, workoutDate, bodyPart, note,
                exerciseNames, weightKgs, sets, reps, restSeconds);
        return "redirect:/workout";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/workout";
    }
}
