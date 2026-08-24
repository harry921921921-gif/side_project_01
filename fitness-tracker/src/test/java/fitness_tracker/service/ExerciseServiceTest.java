package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import fitness_tracker.entity.Exercise;
import fitness_tracker.entity.User;
import fitness_tracker.repository.ExerciseRepository;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    @Mock
    private ExerciseRepository repository;

    @InjectMocks
    private ExerciseService service;

    private static User userWithId(long id) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void visibleToAllowsGlobalExercisesForEveryone() {
        Exercise global = new Exercise("深蹲", "腿", "COMPOUND");
        assertTrue(service.visibleTo(global, userWithId(1L)));
        assertTrue(service.visibleTo(global, null));
    }

    @Test
    void visibleToOnlyAllowsOwnerForPersonalExercises() {
        User owner = userWithId(1L);
        User someoneElse = userWithId(2L);
        Exercise personal = new Exercise("壺鈴甩擺", "全身", "ISOLATION");
        personal.setCreatedBy(owner);

        assertTrue(service.visibleTo(personal, owner));
        assertFalse(service.visibleTo(personal, someoneElse));
        assertFalse(service.visibleTo(personal, null));
    }

    // 兩個使用者各自的個人自訂動作互相看不到彼此的——這是選「個人自定義」方案時最在意的那一點
    @Test
    void findVisibleToExcludesOtherUsersPersonalExercises() {
        User me = userWithId(1L);
        User someoneElse = userWithId(2L);
        Exercise global = new Exercise("深蹲", "腿", "COMPOUND");
        Exercise mine = new Exercise("我的自訂動作", "全身", "ISOLATION");
        mine.setCreatedBy(me);
        Exercise theirs = new Exercise("別人的自訂動作", "全身", "ISOLATION");
        theirs.setCreatedBy(someoneElse);

        when(repository.findAllByOrderByBodyPartAscOrderIndexAscNameAsc())
                .thenReturn(List.of(global, mine, theirs));

        List<Exercise> visible = service.findVisibleTo(me);

        assertEquals(List.of(global, mine), visible);
    }

    @Test
    void addPersonalCreatesNewExerciseOwnedByUser() {
        User user = userWithId(1L);
        when(repository.findByName("壺鈴甩擺")).thenReturn(Optional.empty());
        when(repository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Exercise> result = service.addPersonal(user, "壺鈴甩擺", "全身", "ISOLATION");

        assertTrue(result.isPresent());
        ArgumentCaptor<Exercise> captor = ArgumentCaptor.forClass(Exercise.class);
        verify(repository).save(captor.capture());
        Exercise saved = captor.getValue();
        assertEquals("壺鈴甩擺", saved.getName());
        assertEquals(user, saved.getCreatedBy());
        assertFalse(saved.isPreset());
    }

    // 沒帶 bodyPart 時落回「全身」，不是拋錯——這個新增流程是從搜尋框直接觸發的，不會每次都問使用者部位
    @Test
    void addPersonalDefaultsBodyPartToWholeBodyWhenBlank() {
        User user = userWithId(1L);
        when(repository.findByName("新動作")).thenReturn(Optional.empty());
        when(repository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Exercise> result = service.addPersonal(user, "新動作", "", null);

        assertTrue(result.isPresent());
        assertEquals("全身", result.get().getBodyPart());
        assertEquals("ISOLATION", result.get().getCategory());
    }

    // 名字撞到既有的動作（不管是全站共用還是別人自訂的）就直接沿用那一筆，不當成錯誤擋下來
    @Test
    void addPersonalReusesExistingExerciseWhenNameAlreadyTaken() {
        User user = userWithId(1L);
        Exercise existing = new Exercise("臥推", "胸", "COMPOUND");
        when(repository.findByName("臥推")).thenReturn(Optional.of(existing));

        Optional<Exercise> result = service.addPersonal(user, "臥推", "胸", "COMPOUND");

        assertEquals(Optional.of(existing), result);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
