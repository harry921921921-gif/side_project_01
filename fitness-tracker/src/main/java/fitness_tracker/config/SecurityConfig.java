package fitness_tracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 簽署「記住我」cookie 用的密鑰，可用 REMEMBER_ME_KEY 環境變數覆蓋
    @Value("${REMEMBER_ME_KEY:fitness-tracker-remember-me-dev-key}")
    private String rememberMeKey;

    // BCrypt：密碼加鹽雜湊，資料庫只存雜湊值
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**", "/images/**", "/webjars/**", "/error").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")     // 用 email 當帳號
                .passwordParameter("password")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .rememberMe(rm -> rm
                .key(rememberMeKey)
                .tokenValiditySeconds(14 * 24 * 60 * 60)   // 14 天
                .rememberMeParameter("remember-me")        // 對應 login.html 的 checkbox name
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );
        return http.build();
    }
}
