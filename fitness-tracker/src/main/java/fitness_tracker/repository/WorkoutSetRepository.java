package fitness_tracker.repository;

import fitness_tracker.entity.User;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.enums.CompletionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    // 查詢某次訓練（session）底下的所有動作
    List<WorkoutSet> findBySessionId(Long sessionId);

    // 給新手模式主項重量進階用：查最近一次「真的完成」的紀錄，依訓練日期新到舊排序
    List<WorkoutSet> findBySession_UserAndExerciseNameInAndCompletionStatusOrderBySession_WorkoutDateDescIdDesc(
            User user, Collection<String> exerciseNames, CompletionStatus completionStatus);
}
