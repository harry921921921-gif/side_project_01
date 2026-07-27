package fitness_tracker.service;

import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import fitness_tracker.repository.LiftPrRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LiftPrService {

    private static final Logger log = LoggerFactory.getLogger(LiftPrService.class);

    private final LiftPrRepository repo;

    public LiftPrService(LiftPrRepository repo) { this.repo = repo; }

    public List<LiftPr> findByUser(User user) { return repo.findByUser(user); }

    // 存/更新某動作的 PR；1RM 用 Epley 估算
    @Transactional
    public void save(User user, String exerciseName, double weight, int reps) {
        LiftPr pr = repo.findByUserAndExerciseName(user, exerciseName).orElseGet(LiftPr::new);
        pr.setUser(user);
        pr.setExerciseName(exerciseName);
        pr.setWeightKg(weight);
        pr.setReps(reps);
        int rc = Math.min(Math.max(reps, 1), 10);
        double orm = (reps == 1) ? weight : weight * (1 + rc / 30.0);
        pr.setOneRepMax(Math.round(orm * 10) / 10.0);
        log.info("Saving PR for userId={}: exercise={}, weight={}, reps={}", user.getId(), exerciseName, weight, reps);
        repo.save(pr);
    }
}
