package fitness_tracker.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fitness_tracker.entity.BodyWeight;
import fitness_tracker.repository.BodyWeightRepository;

@Service
public class BodyWeightService {

    private static final Logger log = LoggerFactory.getLogger(BodyWeightService.class);

    private final BodyWeightRepository repository;

    public BodyWeightService(BodyWeightRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<BodyWeight> findAll() {
        return repository.findAllByOrderByRecordedDateDescCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<BodyWeight> findPage(Pageable pageable) {
        return repository.findAllByOrderByRecordedDateDescCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<BodyWeight> findLatest() {
        List<BodyWeight> all = findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    public void save(BodyWeight bodyWeight) {
        log.info("Creating body weight record for date={} weightKg={}", bodyWeight.getRecordedDate(), bodyWeight.getWeightKg());
        repository.save(bodyWeight);
        log.info("Created body weight record id={}", bodyWeight.getId());
    }

    public Optional<BodyWeight> findById(long id) {
        return repository.findById(id);
    }

    public void delete(long id) {
        log.info("Deleting body weight record id={}", id);
        repository.deleteById(id);
        log.info("Deleted body weight record id={}", id);
    }
}
