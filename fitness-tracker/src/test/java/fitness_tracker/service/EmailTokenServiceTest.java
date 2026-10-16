package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fitness_tracker.entity.EmailToken;
import fitness_tracker.entity.User;
import fitness_tracker.repository.EmailTokenRepository;

@ExtendWith(MockitoExtension.class)
class EmailTokenServiceTest {

    @Mock
    private EmailTokenRepository repo;

    @InjectMocks
    private EmailTokenService service;

    private EmailToken tokenOf(String type, boolean used, LocalDateTime expiresAt) {
        EmailToken t = new EmailToken();
        t.setToken("tok-123");
        t.setUser(new User());
        t.setType(type);
        t.setUsed(used);
        t.setExpiresAt(expiresAt);
        return t;
    }

    @Test
    void validateFetchesTokenWithUserEagerlyToAvoidLazyInitializationOutsideTransaction() {
        EmailToken t = tokenOf(EmailTokenService.RESET, false, LocalDateTime.now().plusHours(1));
        when(repo.findByTokenFetchUser("tok-123")).thenReturn(Optional.of(t));

        Optional<EmailToken> result = service.validate("tok-123", EmailTokenService.RESET);

        assertTrue(result.isPresent());
        // getUser() must be safely accessible here (outside any transaction), proving it was fetch-joined
        assertTrue(result.get().getUser() != null);
        verify(repo).findByTokenFetchUser("tok-123");
    }

    @Test
    void validateRejectsAlreadyUsedToken() {
        EmailToken t = tokenOf(EmailTokenService.RESET, true, LocalDateTime.now().plusHours(1));
        when(repo.findByTokenFetchUser("tok-123")).thenReturn(Optional.of(t));

        assertFalse(service.validate("tok-123", EmailTokenService.RESET).isPresent());
    }

    @Test
    void validateRejectsExpiredToken() {
        EmailToken t = tokenOf(EmailTokenService.RESET, false, LocalDateTime.now().minusMinutes(1));
        when(repo.findByTokenFetchUser("tok-123")).thenReturn(Optional.of(t));

        assertFalse(service.validate("tok-123", EmailTokenService.RESET).isPresent());
    }

    @Test
    void validateRejectsMismatchedType() {
        EmailToken t = tokenOf(EmailTokenService.VERIFY, false, LocalDateTime.now().plusHours(1));
        when(repo.findByTokenFetchUser("tok-123")).thenReturn(Optional.of(t));

        assertFalse(service.validate("tok-123", EmailTokenService.RESET).isPresent());
    }
}
