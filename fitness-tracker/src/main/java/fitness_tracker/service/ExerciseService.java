package fitness_tracker.service;

import fitness_tracker.entity.Exercise;
import fitness_tracker.repository.ExerciseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ExerciseService {

    private final ExerciseRepository repository;

    public ExerciseService(ExerciseRepository repository) {
        this.repository = repository;
    }

    // 查全部（下拉選單用）
    public List<Exercise> findAll() {
        return repository.findAllByOrderByBodyPartAscNameAsc();
    }

    // 依部位篩選
    public List<Exercise> findByBodyPart(String bodyPart) {
        return repository.findByBodyPartOrderByNameAsc(bodyPart);
    }

    // 文字模糊搜尋（自動完成）
    public List<Exercise> search(String keyword) {
        return repository.findByNameContainingIgnoreCaseOrderByNameAsc(keyword);
    }

    // 新增自訂動作（會先確認名稱沒重複）
    public Optional<Exercise> addCustom(String name, String bodyPart, String category) {
        if (repository.existsByName(name)) {
            return Optional.empty();    // 已存在，回空
        }
        Exercise ex = new Exercise(name, bodyPart, category);
        ex.setPreset(false);            // 標記為用戶自訂
        return Optional.of(repository.save(ex));
    }

    // 初始化用：資料表是否已有資料
    public boolean hasData() {
        return repository.count() > 0;
    }

    // 批次寫入預設動作（給 DataInitializer 用）
    public void saveAll(List<Exercise> exercises) {
        repository.saveAll(exercises);
    }
}
