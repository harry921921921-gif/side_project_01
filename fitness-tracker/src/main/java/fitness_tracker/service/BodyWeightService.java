package fitness_tracker.service;

import fitness_tracker.entity.BodyWeight;
import fitness_tracker.repository.BodyWeightRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service  // 告訴 Spring 這是 Service 層，可以被注入（@Autowired / 建構子注入）
public class BodyWeightService {

    private final BodyWeightRepository repository;

    // 建構子注入（Spring 推薦做法，比 @Autowired 欄位注入更安全）
    public BodyWeightService(BodyWeightRepository repository) {
        this.repository = repository;
    }

    // 查詢全部體重紀錄，最新的在前
    public List<BodyWeight> findAll() {
        return repository.findAllByOrderByRecordedDateDescCreatedAtDesc();
    }

    // 查最新一筆體重（給首頁 Dashboard 顯示）
    // Optional 代表「可能有、也可能沒有」，避免 NullPointerException
    public Optional<BodyWeight> findLatest() {
        List<BodyWeight> all = findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    // 儲存體重紀錄
    public void save(BodyWeight bodyWeight) {
        repository.save(bodyWeight);
    }

    // 刪除
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
