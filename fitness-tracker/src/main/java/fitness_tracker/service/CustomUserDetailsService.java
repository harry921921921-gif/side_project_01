package fitness_tracker.service;

import fitness_tracker.entity.User;
import fitness_tracker.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.trim();
        User u = users.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("找不到帳號：" + normalizedEmail));
        return org.springframework.security.core.userdetails.User
                .withUsername(u.getEmail())
                .password(u.getPasswordHash())
                .disabled(!u.isEnabled())
                .authorities("ROLE_USER")
                .build();
    }
}
