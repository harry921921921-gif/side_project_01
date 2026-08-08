package fitness_tracker.enums;

public enum CompletionStatus {
    COMPLETE,
    FAILED,
    DROPPED,
    PAIN;

    // 表單送出的完成狀態是文字（可能是空字串代表使用者沒填），統一在這裡轉換，
    // MVC 表單跟 REST API 兩條路徑都能共用，不用各自重寫一次剖析邏輯
    public static CompletionStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("completionStatus 不合法");
        }
    }
}
