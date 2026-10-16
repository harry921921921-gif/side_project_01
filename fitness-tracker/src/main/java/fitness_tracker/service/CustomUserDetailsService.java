package fitness_tracker.service;

import fitness_tracker.config.RequestIpHolder;
import fitness_tracker.entity.User;
import fitness_tracker.enums.Role;
import fitness_tracker.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final LoginAttemptService loginAttemptService;

    public CustomUserDetailsService(UserRepository users, LoginAttemptService loginAttemptService) {
        this.users = users;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.trim();
        User u = users.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("找不到帳號：" + normalizedEmail));
        String[] authorities = u.getRole() == Role.ADMIN
                ? new String[]{"ROLE_USER", "ROLE_ADMIN"}
                : new String[]{"ROLE_USER"};
        // 同一個「信箱＋來源 IP」連續登入失敗太多次時鎖住帳號一段時間——DaoAuthenticationProvider
        // 會自己檢查 accountLocked，鎖住的話直接丟 LockedException，連密碼都不用比對。
        // 這裡拿不到 HttpServletRequest（UserDetailsService 介面沒有這個參數），改從 ClientIpFilter
        // 存進 ThreadLocal 的 RequestIpHolder 讀目前請求的來源 IP
        return org.springframework.security.core.userdetails.User
                .withUsername(u.getEmail())
                .password(u.getPasswordHash())
                .disabled(!u.isEnabled())
                .accountLocked(loginAttemptService.isLocked(normalizedEmail, RequestIpHolder.get()))
                .authorities(authorities)
                .build();
    }
}
