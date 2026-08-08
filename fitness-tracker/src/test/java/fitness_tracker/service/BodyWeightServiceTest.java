package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fitness_tracker.entity.BodyWeight;
import fitness_tracker.entity.User;
import fitness_tracker.repository.BodyWeightRepository;

// 稽核抓出這個服務完全沒有測試覆蓋——這裡補上這次剛加的兩個關鍵行為：範圍檢查（擋離譜數字）
// 跟同一天同時段重複送出時改成更新既有紀錄（不再灌出重複列）
@ExtendWith(MockitoExtension.class)
class BodyWeightServiceTest {

    @Mock
    private BodyWeightRepository repository;

    @InjectMocks
    private BodyWeightService service;

    private BodyWeight recordOf(double weightKg) {
        BodyWeight bw = new BodyWeight();
        bw.setRecordedDate(LocalDate.of(2026, 8, 1));
        bw.setWeightKg(weightKg);
        bw.setTimeOfDay("MORNING");
        return bw;
    }

    @Test
    void saveAcceptsWeightWithinValidRange() {
        BodyWeight bw = recordOf(70.5);
        User user = new User();
        when(repository.findByUserAndRecordedDateAndTimeOfDay(user, bw.getRecordedDate(), "MORNING"))
                .thenReturn(Optional.empty());

        service.save(bw, user);

        verify(repository).save(bw);
    }

    @Test
    void saveRejectsWeightBelowMinimum() {
        BodyWeight bw = recordOf(-50);
        User user = new User();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.save(bw, user));
        assertEquals("體重必須介於 20～300 公斤之間", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void saveRejectsWeightAboveMaximum() {
        BodyWeight bw = recordOf(999999999999.0);
        User user = new User();

        assertThrows(IllegalArgumentException.class, () -> service.save(bw, user));
        verify(repository, never()).save(any());
    }

    @Test
    void saveRejectsBodyFatPctOutOfRange() {
        BodyWeight bw = recordOf(70);
        bw.setBodyFatPct(150.0);
        User user = new User();
        when(repository.findByUserAndRecordedDateAndTimeOfDay(user, bw.getRecordedDate(), "MORNING"))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.save(bw, user));
        assertEquals("體脂率必須介於 1～70% 之間", ex.getMessage());
    }

    // 同一天、同時段（早上）已經有一筆紀錄時，再存一次應該更新既有那筆，不是新增一筆重複的
    @Test
    void saveUpdatesExistingRecordForSameDateAndTimeOfDay() {
        User user = new User();
        BodyWeight existing = recordOf(70.0);
        existing.setUser(user);

        BodyWeight incoming = recordOf(71.5);
        incoming.setNote("重新量一次");
        when(repository.findByUserAndRecordedDateAndTimeOfDay(user, incoming.getRecordedDate(), "MORNING"))
                .thenReturn(Optional.of(existing));

        service.save(incoming, user);

        assertEquals(71.5, existing.getWeightKg());
        assertEquals("重新量一次", existing.getNote());
        verify(repository).save(existing);
        verify(repository, never()).save(incoming);
    }

    @Test
    void saveCreatesNewRecordWhenNoneExistsForThatDateAndTimeOfDay() {
        User user = new User();
        BodyWeight incoming = recordOf(71.5);
        when(repository.findByUserAndRecordedDateAndTimeOfDay(user, incoming.getRecordedDate(), "MORNING"))
                .thenReturn(Optional.empty());

        service.save(incoming, user);

        verify(repository).save(incoming);
    }
}
