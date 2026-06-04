package fitness_tracker.controller;

import fitness_tracker.dto.BodyWeightRequest;
import fitness_tracker.entity.BodyWeight;
import fitness_tracker.service.BodyWeightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/body-weights")
public class BodyWeightApiController {

    private final BodyWeightService service;

    public BodyWeightApiController(BodyWeightService service) {
        this.service = service;
    }

    // GET /api/body-weights
    @GetMapping
    public List<BodyWeight> list() {
        return service.findAll();
    }

    // GET /api/body-weights/latest
    @GetMapping("/latest")
    public ResponseEntity<BodyWeight> latest() {
        return service.findLatest()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/body-weights
    // Body: { "recordedDate": "2026-06-02", "weightKg": 75.5, "timeOfDay": "MORNING", "note": "..." }
    @PostMapping
    public ResponseEntity<BodyWeight> create(@RequestBody BodyWeightRequest req) {
        BodyWeight bodyWeight = new BodyWeight();
        bodyWeight.setRecordedDate(req.recordedDate());
        bodyWeight.setWeightKg(req.weightKg());
        bodyWeight.setTimeOfDay(req.timeOfDay());
        bodyWeight.setNote(req.note());
        service.save(bodyWeight);
        return ResponseEntity.ok(bodyWeight);
    }

    // PUT /api/body-weights/{id}
    // Body: { "recordedDate": "2026-06-02", "weightKg": 75.5, "timeOfDay": "MORNING", "note": "..." }
    @PutMapping("/{id}")
    public ResponseEntity<BodyWeight> update(@PathVariable long id, @RequestBody BodyWeightRequest req) {
        return service.findById(id).map(bodyWeight -> {
            bodyWeight.setRecordedDate(req.recordedDate());
            bodyWeight.setWeightKg(req.weightKg());
            bodyWeight.setTimeOfDay(req.timeOfDay());
            bodyWeight.setNote(req.note());
            service.save(bodyWeight);
            return ResponseEntity.ok(bodyWeight);
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE /api/body-weights/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
