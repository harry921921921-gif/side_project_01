package fitness_tracker.config;

import fitness_tracker.service.LoginAttemptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 簽署「記住我」cookie 用的密鑰——故意不給預設值：部署時忘記設定 REMEMBER_ME_KEY
    // 環境變數的話，寧可讓服務啟動失敗，也不要安靜地用一個寫在原始碼裡、任何人都看得到的
    // 金鑰簽 cookie（那等於任何知道使用者 email 的人都能偽造登入憑證）
    @Value("${REMEMBER_ME_KEY}")
    private String rememberMeKey;

    // BCrypt：密碼加鹽雜湊，資料庫只存雜湊值
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 登入失敗時記一筆失敗次數（信箱＋來源 IP）；成功則清掉——擋暴力破解/帳密填充用。
    // 帶 IP 是因為 LoginAttemptService 的鎖定改成「信箱＋IP」，不能只用信箱鎖（見該類別的說明）
    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler(LoginAttemptService loginAttemptService) {
        SimpleUrlAuthenticationFailureHandler delegate = new SimpleUrlAuthenticationFailureHandler("/login?error");
        return (request, response, exception) -> {
            if (!(exception instanceof AuthenticationServiceException)) {
                loginAttemptService.loginFailed(request.getParameter("email"), request.getRemoteAddr());
            }
            delegate.onAuthenticationFailure(request, response, exception);
        };
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler(LoginAttemptService loginAttemptService) {
        SavedRequestAwareAuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();
        delegate.setDefaultTargetUrl("/");
        delegate.setAlwaysUseDefaultTargetUrl(true);
        return (request, response, authentication) -> {
            loginAttemptService.loginSucceeded(authentication.getName(), request.getRemoteAddr());
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    // 記住我 token 存資料庫（persistent_logins，見 schema.sql），取代 Spring Security 預設那套
    // 無狀態、雜湊型的記住我機制。差別：舊機制的 cookie 一旦外流，在效期內（7 天）沒辦法單獨撤銷，
    // 登出也只是清瀏覽器端的 cookie，伺服器不知道也管不著；換成資料庫存的 token 之後，token 每次
    // 使用都會輪替，舊 token 被重放（代表 cookie 被偷了）會被偵測到並直接整組作廢，登出也才是
    // 真的讓伺服器端失去這組憑證，不是只清客戶端 cookie
    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            AuthenticationFailureHandler authenticationFailureHandler,
                                            AuthenticationSuccessHandler authenticationSuccessHandler,
                                            PersistentTokenRepository persistentTokenRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/verify", "/forgot", "/reset", "/css/**", "/js/**", "/images/**", "/webjars/**", "/error").permitAll()
                // /manage（部位/動作清單管理）跟新增動作的 API 動到的是全站共用的資料，
                // 不是使用者自己的個人資料，只有管理員能改，避免任何登入的人都能刪別人加的東西
                .requestMatchers("/manage/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/exercises").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // 在 UsernamePasswordAuthenticationFilter 之前就把來源 IP 放進 ThreadLocal，
            // 讓再往下一層的 CustomUserDetailsService（沒有 HttpServletRequest 可用）也查得到
            .addFilterBefore(new ClientIpFilter(), UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")     // 用 email 當帳號
                .passwordParameter("password")
                .successHandler(authenticationSuccessHandler)
                .failureHandler(authenticationFailureHandler)
                .permitAll()
            )
            .rememberMe(rm -> rm
                .key(rememberMeKey)
                .tokenRepository(persistentTokenRepository)
                .tokenValiditySeconds(7 * 24 * 60 * 60)    // 14 天縮短成 7 天：存的是個人健康資料，長效期的持久登入 cookie 風險較高
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
