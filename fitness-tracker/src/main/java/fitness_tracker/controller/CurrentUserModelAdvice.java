package fitness_tracker.controller;

import fitness_tracker.entity.User;
import fitness_tracker.service.CurrentUserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// 把目前登入的使用者放進每個頁面的 Model，讓 navbar 能顯示「Hi, 顯示名稱」。
// /login、/register 是匿名可訪問的頁面，沒有登入時 CurrentUserService 會丟例外，這裡接住讓頁面照樣正常渲染。
@ControllerAdvice
public class CurrentUserModelAdvice {

    private final CurrentUserService currentUserService;

    public CurrentUserModelAdvice(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @ModelAttribute("currentUser")
    public User currentUser() {
        try {
            return currentUserService.getCurrentUser();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
