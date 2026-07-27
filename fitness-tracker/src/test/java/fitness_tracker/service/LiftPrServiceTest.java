package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import fitness_tracker.repository.LiftPrRepository;

@ExtendWith(MockitoExtension.class)
class LiftPrServiceTest {

    @Mock
    private LiftPrRepository repo;

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
}
