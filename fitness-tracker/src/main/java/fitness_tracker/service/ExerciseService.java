package fitness_tracker.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import fitness_tracker.entity.Exercise;
import fitness_tracker.entity.User;
import fitness_tracker.repository.ExerciseRepository;

@Service
public class ExerciseService {

    private final ExerciseRepository repository;

    public ExerciseService(ExerciseRepository repository) {
        this.repository = repository;
    }

    public List<Exercise> findAll() {
        return repository.findAllByOrderByBodyPartAscOrderIndexAscNameAsc();
    }

    public List<Exercise> findByBodyPart(String bodyPart) {
        return repository.findByBodyPartOrderByOrderIndexAscNameAsc(bodyPart);
    }

    public List<Exercise> search(String keyword) {
        return repository.findByNameContainingIgnoreCaseOrderByNameAsc(keyword);
    }

    // 給「新增動作」搜尋建議/自動排課候選池用：只回全站共用的動作（createdBy=null）
    // 加上這個使用者自己建的個人自訂動作，不會看到別人的個人自訂動作。
    // 動作目錄規模小（幾十~上百筆），直接查全部再用 Java 過濾，不用為此另外寫一堆組合查詢方法
    public List<Exercise> findVisibleTo(User user) {
        return findAll().stream().filter(e -> visibleTo(e, user)).toList();
    }

    public List<Exercise> findVisibleTo(String bodyPart, User user) {
        return findByBodyPart(bodyPart).stream().filter(e -> visibleTo(e, user)).toList();
    }

    public boolean visibleTo(Exercise e, User user) {
        Long ownerId = e.getCreatedBy() != null ? e.getCreatedBy().getId() : null;
        if (ownerId == null) return true;
        return user != null && ownerId.equals(user.getId());
    }

    public Optional<Exercise> addCustom(String name, String bodyPart, String category) {
        if (name == null || bodyPart == null) return Optional.empty();
        String trimmed = name.trim();
        String normalizedBodyPart = bodyPart.trim();
        if (trimmed.isEmpty() || normalizedBodyPart.isEmpty() || repository.existsByName(trimmed)) return Optional.empty();
        List<Exercise> existing = repository.findByBodyPartOrderByOrderIndexDesc(normalizedBodyPart);
        int nextIdx = existing.isEmpty() ? 1
                : (existing.get(0).getOrderIndex() == null ? 1 : existing.get(0).getOrderIndex() + 1);
        Exercise ex = new Exercise(trimmed, normalizedBodyPart, category != null ? category : "COMPOUND");
        ex.setPreset(false);
        ex.setOrderIndex(nextIdx);
        return Optional.of(repository.save(ex));
    }

    // 使用者在訓練紀錄/課表編輯彈窗打字「＋新增動作」時建立個人自訂動作——不用 ADMIN 權限，
    // 但只有本人看得到（見 findVisibleTo）。名字如果剛好撞到既有的動作（不管是全站共用還是
    // 別人自訂的，名字本身在資料庫是全域唯一），直接沿用那一筆讓使用者可以用，不當成錯誤擋下來：
    // 對使用者來說「打這個名字要能用」才是重點，背後究竟是新建還是沿用既有資料是無感的實作細節
    public Optional<Exercise> addPersonal(User user, String name, String bodyPart, String category) {
        if (user == null || name == null) return Optional.empty();
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        Optional<Exercise> existing = repository.findByName(trimmed);
        if (existing.isPresent()) return existing;
        String normalizedBodyPart = (bodyPart == null || bodyPart.isBlank()) ? "全身" : bodyPart.trim();
        Exercise ex = new Exercise(trimmed, normalizedBodyPart, category != null && !category.isBlank() ? category : "ISOLATION");
        ex.setPreset(false);
        ex.setCreatedBy(user);
        return Optional.of(repository.save(ex));
    }

    public void delete(Long id) {
        repository.findById(id).ifPresent(ex -> {
            if (!ex.isPreset()) repository.deleteById(id);
        });
    }

    public void moveUp(Long id) {
        repository.findById(id).ifPresent(ex -> {
            List<Exercise> group = repository.findByBodyPartOrderByOrderIndexAscNameAsc(ex.getBodyPart());
            for (int i = 1; i < group.size(); i++) {
                if (group.get(i).getId().equals(id)) {
                    swapOrder(group.get(i - 1), group.get(i));
                    return;
                }
            }
        });
    }

    public void moveDown(Long id) {
        repository.findById(id).ifPresent(ex -> {
            List<Exercise> group = repository.findByBodyPartOrderByOrderIndexAscNameAsc(ex.getBodyPart());
            for (int i = 0; i < group.size() - 1; i++) {
                if (group.get(i).getId().equals(id)) {
                    swapOrder(group.get(i), group.get(i + 1));
                    return;
                }
            }
        });
    }

    private void swapOrder(Exercise a, Exercise b) {
        Integer tmp = a.getOrderIndex();
        a.setOrderIndex(b.getOrderIndex());
        b.setOrderIndex(tmp);
        repository.save(a);
        repository.save(b);
    }

    public boolean hasData() {
        return repository.count() > 0;
    }

    public void saveAll(List<Exercise> exercises) {
        repository.saveAll(exercises);
    }
}
