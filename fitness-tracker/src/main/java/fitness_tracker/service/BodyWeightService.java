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
import fitness_tracker.entity.User;
import fitness_tracker.exception.ResourceNotFoundException;
import fitness_tracker.repository.BodyWeightRepository;

@Service
public class BodyWeightService {

    private static final Logger log = LoggerFactory.getLogger(BodyWeightService.class);

    private final BodyWeightRepository repository;

    public BodyWeightService(BodyWeightRepository repository) {
        this.repository = repository;
    }

    // ── 舊版（未過濾使用者）：保留給既有呼叫端/測試相容，正式流程請一律用帶 User 的版本 ──
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

    // ── 使用者過濾版：controller 一律用這組 ──
    @Transactional(readOnly = true)
    public List<BodyWeight> findAll(User user) {
        return repository.findAllByUserOrderByRecordedDateDescCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public Page<BodyWeight> findPage(Pageable pageable, User user) {
        return repository.findAllByUserOrderByRecordedDateDescCreatedAtDesc(user, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<BodyWeight> findLatest(User user) {
        return repository.findTopByUserOrderByRecordedDateDescCreatedAtDesc(user);
    }

    public void save(BodyWeight bodyWeight, User user) {
        bodyWeight.setUser(user);
        save(bodyWeight);
    }

    @Transactional(readOnly = true)
    public Optional<BodyWeight> findById(long id, User user) {
        return repository.findByIdAndUser(id, user);
    }

    public void delete(long id, User user) {
        BodyWeight bodyWeight = repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("找不到 id=" + id + " 的體重紀錄"));
        log.info("Deleting body weight record id={}", id);
        repository.delete(bodyWeight);
        log.info("Deleted body weight record id={}", id);
    }
}
