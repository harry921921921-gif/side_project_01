package fitness_tracker.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import fitness_tracker.entity.Exercise;
import fitness_tracker.repository.ExerciseRepository;

@Service
public class ExerciseService {

    private final ExerciseRepository repository;

    public ExerciseService(ExerciseRepository repository) {
        this.repository = repository;
    }

    public List<Exercise> findAll() {
        return repository.findAllByOrderByBodyPartAscOrderIndexAscNameAsc();
    }

    public List<Exercise> findByBodyPart(String bodyPart) {
        return repository.findByBodyPartOrderByOrderIndexAscNameAsc(bodyPart);
    }

    public List<Exercise> search(String keyword) {
        return repository.findByNameContainingIgnoreCaseOrderByNameAsc(keyword);
    }

    public Optional<Exercise> addCustom(String name, String bodyPart, String category) {
        if (name == null || bodyPart == null) return Optional.empty();
        String trimmed = name.trim();
        String normalizedBodyPart = bodyPart.trim();
        if (trimmed.isEmpty() || normalizedBodyPart.isEmpty() || repository.existsByName(trimmed)) return Optional.empty();
        List<Exercise> existing = repository.findByBodyPartOrderByOrderIndexDesc(normalizedBodyPart);
        int nextIdx = existing.isEmpty() ? 1
                : (existing.get(0).getOrderIndex() == null ? 1 : existing.get(0).getOrderIndex() + 1);
        Exercise ex = new Exercise(trimmed, normalizedBodyPart, category != null ? category : "COMPOUND");
        ex.setPreset(false);
        ex.setOrderIndex(nextIdx);
        return Optional.of(repository.save(ex));
    }

    public void delete(Long id) {
        repository.findById(id).ifPresent(ex -> {
            if (!ex.isPreset()) repository.deleteById(id);
        });
    }

    public void moveUp(Long id) {
        repository.findById(id).ifPresent(ex -> {
            List<Exercise> group = repository.findByBodyPartOrderByOrderIndexAscNameAsc(ex.getBodyPart());
            for (int i = 1; i < group.size(); i++) {
                if (group.get(i).getId().equals(id)) {
                    swapOrder(group.get(i - 1), group.get(i));
                    return;
                }
            }
        });
    }

    public void moveDown(Long id) {
        repository.findById(id).ifPresent(ex -> {
            List<Exercise> group = repository.findByBodyPartOrderByOrderIndexAscNameAsc(ex.getBodyPart());
            for (int i = 0; i < group.size() - 1; i++) {
                if (group.get(i).getId().equals(id)) {
                    swapOrder(group.get(i), group.get(i + 1));
                    return;
                }
            }
        });
    }

    private void swapOrder(Exercise a, Exercise b) {
        Integer tmp = a.getOrderIndex();
        a.setOrderIndex(b.getOrderIndex());
        b.setOrderIndex(tmp);
        repository.save(a);
        repository.save(b);
    }

    public boolean hasData() {
        return repository.count() > 0;
    }

    public void saveAll(List<Exercise> exercises) {
        repository.saveAll(exercises);
    }
}
