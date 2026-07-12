package fitness_tracker.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fitness_tracker.dto.BodyWeightRequest;
import fitness_tracker.dto.BodyWeightResponse;
import fitness_tracker.entity.BodyWeight;
import fitness_tracker.service.BodyWeightService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/body-weights")
public class BodyWeightApiController {

    private final BodyWeightService service;

    public BodyWeightApiController(BodyWeightService service) {
        this.service = service;
    }

    @GetMapping
    public Page<BodyWeightResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findPage(pageable).map(this::toResponse);
    }

    @GetMapping("/latest")
    public ResponseEntity<BodyWeightResponse> latest() {
        return service.findLatest()
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<BodyWeightResponse> create(@Valid @RequestBody BodyWeightRequest req) {
        BodyWeight bodyWeight = new BodyWeight();
        bodyWeight.setRecordedDate(req.recordedDate());
        bodyWeight.setWeightKg(req.weightKg());
        bodyWeight.setTimeOfDay(req.timeOfDay());
        bodyWeight.setNote(req.note());
        service.save(bodyWeight);
        return ResponseEntity.ok(toResponse(bodyWeight));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BodyWeightResponse> update(@PathVariable long id, @Valid @RequestBody BodyWeightRequest req) {
        return service.findById(id).map(bodyWeight -> {
            bodyWeight.setRecordedDate(req.recordedDate());
            bodyWeight.setWeightKg(req.weightKg());
            bodyWeight.setTimeOfDay(req.timeOfDay());
            bodyWeight.setNote(req.note());
            service.save(bodyWeight);
            return ResponseEntity.ok(toResponse(bodyWeight));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private BodyWeightResponse toResponse(BodyWeight bodyWeight) {
        return new BodyWeightResponse(
                bodyWeight.getId(),
                bodyWeight.getRecordedDate(),
                bodyWeight.getWeightKg(),
                bodyWeight.getTimeOfDay(),
                bodyWeight.getNote(),
                bodyWeight.getCreatedAt()
        );
    }
}
