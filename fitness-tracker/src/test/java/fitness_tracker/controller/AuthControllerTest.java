package fitness_tracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import fitness_tracker.entity.User;
import fitness_tracker.repository.UserRepository;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.EmailService;
import fitness_tracker.service.EmailTokenService;

// 稽核抓出這條路徑（帳號建立）完全沒有測試覆蓋——這裡補上註冊流程最重要的幾個分支：
// 正常註冊、信箱已存在（一般情況跟同時搶註冊的競態條件兩種）、密碼太弱。
// @WebMvcTest 不會載入專案自己的 SecurityConfig（那個是 @Configuration 不是
// @Controller/@ControllerAdvice，預設不會被這個測試切面掃到），所以 Spring Security
// 的自動設定會用預設值擋掉所有請求；這裡測的是 controller 邏輯本身，不是「/register
// 有沒有正確 permitAll」這件事（那個已經在正式環境手動驗證過），所以直接關掉安全過濾器
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository users;

    @MockBean
    private PasswordEncoder encoder;

    @MockBean
    private EmailService emailService;

    @MockBean
    private EmailTokenService tokenService;

    // 導覽列的 CurrentUserModelAdvice 需要這個 bean 才能在測試切面裡把 context 組起來
    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void registerSucceedsWithNewEmail() throws Exception {
        given(users.existsByEmail("new@example.com")).willReturn(false);
        given(encoder.encode(anyString())).willReturn("hashed");

        mockMvc.perform(post("/register")
                        .param("displayName", "新使用者")
                        .param("email", "new@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        verify(users).save(any(User.class));
    }

    @Test
    void registerRejectsAlreadyRegisteredEmail() throws Exception {
        given(users.existsByEmail("taken@example.com")).willReturn(true);

        mockMvc.perform(post("/register")
                        .param("displayName", "使用者")
                        .param("email", "taken@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("error", "這個信箱已經註冊過了"));

        verify(users, never()).save(any());
    }

    // 兩個人同時搶註冊同一個信箱：existsByEmail 檢查當下都還沒有人存在，但真的存檔時
    // 唯一鍵衝突——要友善地回到註冊頁，不是讓使用者看到 500
    @Test
    void registerHandlesRaceConditionOnDuplicateEmail() throws Exception {
        given(users.existsByEmail("race@example.com")).willReturn(false);
        given(encoder.encode(anyString())).willReturn("hashed");
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(users).save(any(User.class));

        mockMvc.perform(post("/register")
                        .param("displayName", "使用者")
                        .param("email", "race@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("error", "這個信箱已經註冊過了"));
    }

    @Test
    void registerRejectsWeakPassword() throws Exception {
        mockMvc.perform(post("/register")
                        .param("displayName", "使用者")
                        .param("email", "weak@example.com")
                        .param("password", "1234567")   // 少於 8 碼
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("error", "請填寫完整，密碼至少 8 碼"));

        verify(users, never()).save(any());
    }

    // 忘記密碼：不管信箱存不存在都回同樣的訊息，避免被拿來枚舉哪些信箱有註冊過
    @Test
    void forgotPasswordDoesNotRevealWhetherEmailExists() throws Exception {
        given(users.findByEmail("nobody@example.com")).willReturn(java.util.Optional.empty());

        mockMvc.perform(post("/forgot")
                        .param("email", "nobody@example.com")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot?sent"));

        verify(emailService, never()).sendPasswordReset(any(), anyString());
    }
}
