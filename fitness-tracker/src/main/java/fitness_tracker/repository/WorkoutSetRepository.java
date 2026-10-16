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

    // 給停滯偵測用：查某個主項最近幾次的紀錄（不分完成狀態），依訓練日期新到舊排序，
    // 從最新的往回數連續幾次不是 COMPLETE，藉此判斷是不是卡關了
    List<WorkoutSet> findTop10BySession_UserAndExerciseNameOrderBySession_WorkoutDateDescIdDesc(
            User user, String exerciseName);
}
