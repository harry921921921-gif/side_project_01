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
        validate(bodyWeight);
        log.info("Creating body weight record for date={} weightKg={}", bodyWeight.getRecordedDate(), bodyWeight.getWeightKg());
        repository.save(bodyWeight);
        log.info("Created body weight record id={}", bodyWeight.getId());
    }

    // 數字技術上合法但明顯不合理（負數、0、誇張大的數字）時要擋下來，不然會悄悄污染歷史紀錄跟趨勢圖統計；
    // 範圍跟表單上 <input> 的 min/max 保持一致，這裡只是補上伺服器端沒有前端擋住時的最後一道防線
    private void validate(BodyWeight bodyWeight) {
        Double weight = bodyWeight.getWeightKg();
        if (weight == null || weight < 20 || weight > 300) {
            throw new IllegalArgumentException("體重必須介於 20～300 公斤之間");
        }
        Double bodyFat = bodyWeight.getBodyFatPct();
        if (bodyFat != null && (bodyFat < 1 || bodyFat > 70)) {
            throw new IllegalArgumentException("體脂率必須介於 1～70% 之間");
        }
        Double muscle = bodyWeight.getSkeletalMuscleKg();
        if (muscle != null && (muscle < 1 || muscle > 100)) {
            throw new IllegalArgumentException("骨骼肌重必須介於 1～100 公斤之間");
        }
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

    // 同一天、同時間點（早上/晚上/其他）再存一次時，改成更新既有那筆而不是新增一筆重複的，
    // 避免手殘連點兩次、或表單重送，把歷史紀錄表格跟趨勢圖悄悄灌出一堆一模一樣的資料
    public void save(BodyWeight bodyWeight, User user) {
        bodyWeight.setUser(user);
        String timeOfDay = bodyWeight.getTimeOfDay();
        if (timeOfDay != null && !timeOfDay.isBlank()) {
            Optional<BodyWeight> existing = repository.findByUserAndRecordedDateAndTimeOfDay(
                    user, bodyWeight.getRecordedDate(), timeOfDay);
            if (existing.isPresent()) {
                BodyWeight target = existing.get();
                target.setWeightKg(bodyWeight.getWeightKg());
                target.setBodyFatPct(bodyWeight.getBodyFatPct());
                target.setSkeletalMuscleKg(bodyWeight.getSkeletalMuscleKg());
                target.setNote(bodyWeight.getNote());
                save(target);
                return;
            }
        }
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
