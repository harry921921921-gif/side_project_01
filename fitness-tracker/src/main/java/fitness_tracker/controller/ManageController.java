package fitness_tracker.controller;

import fitness_tracker.service.BodyPartService;
import fitness_tracker.service.ExerciseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/manage")
public class ManageController {

    private final BodyPartService bodyPartService;
    private final ExerciseService exerciseService;

    public ManageController(BodyPartService bodyPartService, ExerciseService exerciseService) {
        this.bodyPartService = bodyPartService;
        this.exerciseService = exerciseService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("bodyParts", bodyPartService.findAll());
        model.addAttribute("exercises", exerciseService.findAll());
        return "manage/index";
    }

    // ── 訓練部位管理 ──
    @PostMapping("/body-part/add")
    public String addBodyPart(@RequestParam String name) {
        bodyPartService.add(name);
        return "redirect:/manage";
    }

    @PostMapping("/body-part/delete/{id}")
    public String deleteBodyPart(@PathVariable Long id) {
        bodyPartService.delete(id);
        return "redirect:/manage";
    }

    @PostMapping("/body-part/move-up/{id}")
    public String moveUpBodyPart(@PathVariable Long id) {
        bodyPartService.moveUp(id);
        return "redirect:/manage";
    }

    @PostMapping("/body-part/move-down/{id}")
    public String moveDownBodyPart(@PathVariable Long id) {
        bodyPartService.moveDown(id);
        return "redirect:/manage";
    }

    // ── 訓練動作管理 ──
    @PostMapping("/exercise/add")
    public String addExercise(@RequestParam String name,
                              @RequestParam String bodyPart,
                              @RequestParam(required = false, defaultValue = "COMPOUND") String category) {
        exerciseService.addCustom(name, bodyPart, category);
        return "redirect:/manage";
    }

    @PostMapping("/exercise/delete/{id}")
    public String deleteExercise(@PathVariable Long id) {
        exerciseService.delete(id);
        return "redirect:/manage";
    }

    @PostMapping("/exercise/move-up/{id}")
    public String moveUpExercise(@PathVariable Long id) {
        exerciseService.moveUp(id);
        return "redirect:/manage";
    }

    @PostMapping("/exercise/move-down/{id}")
    public String moveDownExercise(@PathVariable Long id) {
        exerciseService.moveDown(id);
        return "redirect:/manage";
    }
}
