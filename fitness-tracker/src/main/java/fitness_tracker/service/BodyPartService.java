package fitness_tracker.service;

import fitness_tracker.entity.BodyPart;
import fitness_tracker.repository.BodyPartRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BodyPartService {

    private final BodyPartRepository repository;

    public BodyPartService(BodyPartRepository repository) {
        this.repository = repository;
    }

    public List<BodyPart> findAll() {
        return repository.findAllByOrderByOrderIndexAsc();
    }

    public boolean hasData() {
        return repository.count() > 0;
    }

    public void saveAll(List<BodyPart> list) {
        repository.saveAll(list);
    }

    public Optional<String> add(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || repository.existsByName(trimmed)) return Optional.empty();
        int nextIdx = repository.findAllByOrderByOrderIndexAsc().stream()
                .mapToInt(BodyPart::getOrderIndex).max().orElse(0) + 1;
        BodyPart bp = new BodyPart(trimmed, nextIdx);
        bp.setPreset(false);
        return Optional.of(repository.save(bp).getName());
    }

    public void delete(Long id) {
        repository.findById(id).ifPresent(bp -> {
            if (!bp.isPreset()) repository.deleteById(id);
        });
    }

    public void moveUp(Long id) {
        List<BodyPart> all = repository.findAllByOrderByOrderIndexAsc();
        for (int i = 1; i < all.size(); i++) {
            if (all.get(i).getId().equals(id)) {
                swap(all.get(i - 1), all.get(i));
                return;
            }
        }
    }

    public void moveDown(Long id) {
        List<BodyPart> all = repository.findAllByOrderByOrderIndexAsc();
        for (int i = 0; i < all.size() - 1; i++) {
            if (all.get(i).getId().equals(id)) {
                swap(all.get(i), all.get(i + 1));
                return;
            }
        }
    }

    private void swap(BodyPart a, BodyPart b) {
        int tmp = a.getOrderIndex();
        a.setOrderIndex(b.getOrderIndex());
        b.setOrderIndex(tmp);
        repository.save(a);
        repository.save(b);
    }
}
