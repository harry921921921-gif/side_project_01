package fitness_tracker.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fitness_tracker.entity.Exercise;
import fitness_tracker.repository.ExerciseRepository;

@ExtendWith(MockitoExtension.class)
class ExercisePresetRenameRunnerTest {

    @Mock
    private ExerciseRepository repo;

    @InjectMocks
    private ExercisePresetRenameRunner runner;

    // runner 會對「所有」RENAMES/MISSING 的名字呼叫 existsByName/findByName，
    // 每個測試只在意其中一個名字，其餘用這個 catch-all 擋掉，避免 strict stubbing 因為其他名字沒 stub 而報錯
    private void stubEverythingElseAsNotFound() {
        when(repo.existsByName(anyString())).thenReturn(false);
        when(repo.findByName(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void renamesOldPresetToNewCanonicalName() {
        stubEverythingElseAsNotFound();
        Exercise oldRow = new Exercise("槓鈴肩推", "肩", "COMPOUND");
        when(repo.findByName("槓鈴肩推")).thenReturn(Optional.of(oldRow));

        runner.run();

        assertEquals("肩推", oldRow.getName());
        verify(repo).save(oldRow);
    }

    @Test
    void skipsRenameWhenNewNameAlreadyExists() {
        stubEverythingElseAsNotFound();
        when(repo.existsByName("肩推")).thenReturn(true);

        runner.run();

        verify(repo, never()).findByName("槓鈴肩推");
    }

    @Test
    void insertsMissingPresetExerciseWithMovementSet() {
        ArgumentCaptor<Exercise> captor = ArgumentCaptor.forClass(Exercise.class);

        runner.run();

        verify(repo, times(3)).save(captor.capture());
        Exercise pushUp = captor.getAllValues().stream()
                .filter(e -> "伏地挺身".equals(e.getName())).findFirst().orElseThrow();
        assertEquals("胸", pushUp.getBodyPart());
        assertEquals("COMPOUND", pushUp.getCategory());
        assertEquals("PUSH", pushUp.getMovement());
    }

    @Test
    void skipsInsertingMissingExerciseWhenAlreadyPresent() {
        stubEverythingElseAsNotFound();
        when(repo.existsByName("伏地挺身")).thenReturn(true);
        when(repo.existsByName("聳肩")).thenReturn(true);
        when(repo.existsByName("集中彎舉")).thenReturn(true);

        runner.run();

        verify(repo, never()).save(any(Exercise.class));
    }
}
