package fitness_tracker.controller;

import fitness_tracker.entity.User;
import fitness_tracker.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
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

    // 給 navbar 判斷「目前在哪一頁」用，直接拿注入的 HttpServletRequest 比較可靠——
    // 這個專案的 Thymeleaf 設定下 #httpServletRequest 這個內建運算式物件在部分渲染情境
    // （例如透過 forward 到 /error 之類的二次轉發）會是 null，直接注入才不會踩到這個坑
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
