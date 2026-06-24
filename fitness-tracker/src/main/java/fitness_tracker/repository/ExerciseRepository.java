package fitness_tracker.repository;

import fitness_tracker.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    // 全部動作，依部位 → 名稱排序（給下拉選單用）
    List<Exercise> findAllByOrderByBodyPartAscNameAsc();

    // 依部位篩選（前端按部位 tab 篩選時用）
    List<Exercise> findByBodyPartOrderByNameAsc(String bodyPart);

    // 文字模糊搜尋（前端打字自動完成用），忽略大小寫
    List<Exercise> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    // 確認動作名稱是否已存在（新增自訂動作前檢查）
    boolean existsByName(String name);
}
