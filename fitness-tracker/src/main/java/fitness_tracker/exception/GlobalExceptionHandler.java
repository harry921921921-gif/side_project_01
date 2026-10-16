package fitness_tracker.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        if (isHtmlRequest(request)) {
            ModelAndView modelAndView = new ModelAndView("error");
            modelAndView.setStatus(HttpStatus.NOT_FOUND);
            modelAndView.addObject("message", ex.getMessage());
            return modelAndView;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "NOT_FOUND");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> fieldNames = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .toList();
        log.warn("Validation failed for {} {} on fields {}", request.getMethod(), request.getRequestURI(), fieldNames);
        if (isHtmlRequest(request)) {
            ModelAndView modelAndView = new ModelAndView("error");
            modelAndView.setStatus(HttpStatus.BAD_REQUEST);
            modelAndView.addObject("message", "請修正輸入資料");
            return modelAndView;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "VALIDATION_FAILED");
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorMap)
                .toList();
        body.put("message", "請修正輸入資料");
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // 表單欄位打了無法轉成數字/日期的內容（例如體重打成文字、或整個欄位被清空後送出）——
    // 這種本質上就是「使用者輸入錯了」，不該落到最下面那個通用 500 崩潰頁
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch for {} {} on parameter '{}': {}", request.getMethod(), request.getRequestURI(), ex.getName(), ex.getValue());
        return badRequest(request, "「" + ex.getName() + "」欄位的格式不正確，請確認輸入內容後再送出");
    }

    // 必填的表單欄位整個沒有送出（例如日期欄位被清空、或請求被截斷）——同樣是輸入問題，不是系統錯誤
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getParameterName());
        return badRequest(request, "「" + ex.getParameterName() + "」為必填欄位，請填寫後再送出");
    }

    // Service 層自己判斷輸入不合理時丟出來的（例如體重超出合理範圍、身體部位不存在）——
    // 訊息本身就是寫給使用者看的，直接回傳即可
    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid input for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return badRequest(request, ex.getMessage());
    }

    // 資料庫唯一性衝突（例如同時間有兩個請求搶著建立同一筆資料）——不是系統掛了，是使用者這次
    // 操作剛好撞到別的資料，一樣走友善的表單錯誤流程
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return badRequest(request, "這筆資料跟現有資料衝突（可能是名稱重複），請確認後再試一次");
    }

    // 用錯 HTTP 方法（例如直接用瀏覽器 GET 一個只接受 POST 的網址）是使用者/連結本身的問題，
    // 該回 405，不該被通用 Exception 處理器吞成看起來像系統掛掉的 500
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not supported for {} {}", request.getMethod(), request.getRequestURI());
        String message = "這個網址不支援「" + ex.getMethod() + "」這種請求方式";
        if (isHtmlRequest(request)) {
            ModelAndView modelAndView = new ModelAndView("error");
            modelAndView.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
            modelAndView.addObject("message", message);
            return modelAndView;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "METHOD_NOT_ALLOWED");
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    // 表單打錯格式時，優先導回原本那頁並用 flash 訊息顯示錯誤（跟 AuthController 註冊/重設密碼
    // 已經在用的模式一致），不要把使用者丟到一個空白的通用錯誤頁、剛才填的東西全部消失——
    // 這對訓練紀錄這種一次要填好幾個動作的表單特別重要。只有在抓不到 Referer（沒有上一頁可回）
    // 時才退回顯示原本的錯誤頁當保底。
    //
    // HttpServletResponse 故意不當方法參數宣告——一旦某個 @ExceptionHandler 方法的簽章裡出現
    // HttpServletResponse，Spring 的參數解析器會把這次請求標成「已經處理過」，連帶讓後面要靠
    // ModelAndView 走 Thymeleaf 正常算圖的保底分支失效、掉回 Spring 內建的 Whitelabel 錯誤頁。
    // 改成需要時才用 RequestContextHolder 動態拿，繞開這個副作用。
    private Object badRequest(HttpServletRequest request, String message) {
        if (isHtmlRequest(request)) {
            String targetPath = refererPath(request.getHeader("Referer"));
            if (targetPath != null) {
                HttpServletResponse response = currentResponse();
                if (response != null) {
                    FlashMap flashMap = new FlashMap();
                    flashMap.put("formError", message);
                    flashMap.setTargetRequestPath(targetPath);
                    RequestContextUtils.getFlashMapManager(request).saveOutputFlashMap(flashMap, request, response);
                    return new ModelAndView("redirect:" + targetPath, HttpStatus.FOUND);
                }
            }
            ModelAndView modelAndView = new ModelAndView("error");
            modelAndView.setStatus(HttpStatus.BAD_REQUEST);
            modelAndView.addObject("message", message);
            return modelAndView;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "BAD_REQUEST");
        body.put("message", message);
        return ResponseEntity.badRequest().body(body);
    }

    private HttpServletResponse currentResponse() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getResponse();
        }
        return null;
    }

    private String refererPath(String referer) {
        if (referer == null || referer.isBlank()) return null;
        try {
            String path = URI.create(referer).getPath();
            return (path == null || path.isBlank()) ? null : path;
        } catch (Exception ex) {
            return null;
        }
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        if (isHtmlRequest(request)) {
            ModelAndView modelAndView = new ModelAndView("error");
            modelAndView.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            modelAndView.addObject("message", "系統發生錯誤，請稍後再試");
            return modelAndView;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "INTERNAL_SERVER_ERROR");
        body.put("message", "系統發生錯誤，請稍後再試");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private boolean isHtmlRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private Map<String, String> toFieldErrorMap(FieldError fieldError) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", fieldError.getField());
        error.put("message", fieldError.getDefaultMessage());
        return error;
    }
}
