package fitness_tracker.config;

// 給 CustomUserDetailsService 這種被 Spring Security 內部呼叫、拿不到 HttpServletRequest 的地方
// 查詢目前請求的來源 IP（帳號鎖定要看「信箱+IP」，不能只看信箱——見 LoginAttemptService 的說明）。
// 由 ClientIpFilter 在請求一進來就設定、結束時清掉，避免執行緒池重複使用時殘留上一個請求的值。
public final class RequestIpHolder {

    private static final ThreadLocal<String> CURRENT_IP = new ThreadLocal<>();

    private RequestIpHolder() {}

    public static void set(String ip) { CURRENT_IP.set(ip); }
    public static String get() { return CURRENT_IP.get(); }
    public static void clear() { CURRENT_IP.remove(); }
}
