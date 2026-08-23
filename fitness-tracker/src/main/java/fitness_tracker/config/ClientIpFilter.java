package fitness_tracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 把目前請求的來源 IP 放進 RequestIpHolder，供 CustomUserDetailsService 判斷帳號鎖定時使用。
// 正式環境部署在反向代理後面（application-prod.properties 有設 server.forward-headers-strategy=
// framework），Spring 會先把 X-Forwarded-For 換算回 request.getRemoteAddr()，這裡拿到的就是
// 真實來源 IP，不是代理伺服器自己的 IP。
//
// 故意不標 @Component：這個 filter 只給 Spring Security 內部的 SecurityFilterChain 用
// （見 SecurityConfig.filterChain 的 addFilterBefore），不是標準的 servlet filter。如果標了
// @Component，Spring Boot 會「額外」把它自動註冊成獨立的 servlet filter，導致同一個請求跑兩次
// ——雖然這個 filter 本身冪等（重複設定/清除 ThreadLocal 無害），但沒必要留這個容易混淆的重複註冊。
public class ClientIpFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            RequestIpHolder.set(request.getRemoteAddr());
            chain.doFilter(request, response);
        } finally {
            RequestIpHolder.clear();
        }
    }
}
