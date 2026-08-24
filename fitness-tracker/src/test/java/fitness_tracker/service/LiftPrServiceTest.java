package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import fitness_tracker.repository.LiftPrRepository;

@ExtendWith(MockitoExtension.class)
class LiftPrServiceTest {

    @Mock
    private LiftPrRepository repo;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private LiftPrService service;

    private final User user = new User();

    @Test
    void saveEstimatesOneRepMaxUsingEpleyFormulaForMultipleReps() {
        when(repo.findByUserAndExerciseName(user, "深蹲")).thenReturn(Optional.empty());
        ArgumentCaptor<LiftPr> captor = ArgumentCaptor.forClass(LiftPr.class);

        service.save(user, "深蹲", 100.0, 5);

        verify(repo).save(captor.capture());
        LiftPr saved = captor.getValue();
        assertEquals("深蹲", saved.getExerciseName());
        assertEquals(100.0, saved.getWeightKg());
        assertEquals(5, saved.getReps());
        assertEquals(116.7, saved.getOneRepMax()); // 100 * (1 + 5/30)
    }

    @Test
    void saveUsesWeightDirectlyAsOneRepMaxWhenRepsIsOne() {
        when(repo.findByUserAndExerciseName(user, "臥推")).thenReturn(Optional.empty());
        ArgumentCaptor<LiftPr> captor = ArgumentCaptor.forClass(LiftPr.class);

        service.save(user, "臥推", 90.0, 1);

        verify(repo).save(captor.capture());
        assertEquals(90.0, captor.getValue().getOneRepMax());
    }

    @Test
    void saveUpdatesExistingPrInsteadOfCreatingDuplicate() {
        LiftPr existing = new LiftPr();
        existing.setExerciseName("硬舉");
        when(repo.findByUserAndExerciseName(user, "硬舉")).thenReturn(Optional.of(existing));
        when(repo.save(any(LiftPr.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save(user, "硬舉", 140.0, 3);

        verify(repo).save(existing);
        assertEquals(140.0, existing.getWeightKg());
        assertEquals(3, existing.getReps());
    }

    // saveManual 跟 save 不同的地方：主要動作名稱也可以存（課表編輯彈窗要接管漸進起點），
    // 而且要多存組數/休息秒數——save() 沒有這兩個欄位
    @Test
    void saveManualAllowsMainLiftNamesAndPersistsSetsAndRestSeconds() {
        when(repo.findByUserAndExerciseName(user, "臥推")).thenReturn(Optional.empty());
        ArgumentCaptor<LiftPr> captor = ArgumentCaptor.forClass(LiftPr.class);

        service.saveManual(user, "臥推", "strength", 80.0, 5, 5, 180);

        verify(repo).save(captor.capture());
        LiftPr saved = captor.getValue();
        assertEquals("臥推", saved.getExerciseName());
        assertEquals(80.0, saved.getWeightKg());
        assertEquals(5, saved.getSets());
        assertEquals(5, saved.getReps());
        assertEquals(180, saved.getRestSeconds());
        assertEquals(93.3, saved.getOneRepMax()); // 80 * (1 + 5/30)
    }

    @Test
    void saveManualUpdatesExistingOverrideInsteadOfCreatingDuplicate() {
        LiftPr existing = new LiftPr();
        existing.setExerciseName("肩推");
        when(repo.findByUserAndExerciseName(user, "肩推")).thenReturn(Optional.of(existing));
        when(repo.save(any(LiftPr.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveManual(user, "肩推", "hyper", 45.0, 4, 8, 120);

        verify(repo).save(existing);
        assertEquals(45.0, existing.getWeightKg());
        assertEquals(4, existing.getSets());
        assertEquals(8, existing.getReps());
        assertEquals(120, existing.getRestSeconds());
    }

    // 這就是使用者要求的行為：在最大力量期存的重量/組數/休息只能在最大力量期套用，不能滲透到
    // 肌耐力期或肌肥大期——兩個階段分開存、分開讀，互不覆蓋
    @Test
    void saveManualKeepsSeparateOverridesPerPhaseWithoutLeaking() {
        LiftPr existing = new LiftPr();
        existing.setExerciseName("深蹲");
        when(repo.findByUserAndExerciseName(user, "深蹲")).thenReturn(Optional.of(existing));
        when(repo.save(any(LiftPr.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveManual(user, "深蹲", "strength", 100.0, 5, 5, 180);
        service.saveManual(user, "深蹲", "adapt", 60.0, 4, 15, 60);

        Map<String, LiftPrService.PhaseOverride> overrides = service.phaseOverridesOf(existing);
        assertEquals(2, overrides.size());
        assertEquals(100.0, overrides.get("strength").weightKg());
        assertEquals(5, overrides.get("strength").sets());
        assertEquals(5, overrides.get("strength").reps());
        assertEquals(60.0, overrides.get("adapt").weightKg());
        assertEquals(4, overrides.get("adapt").sets());
        assertEquals(15, overrides.get("adapt").reps());
    }
}
