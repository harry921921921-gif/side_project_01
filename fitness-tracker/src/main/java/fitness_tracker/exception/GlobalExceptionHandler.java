package fitness_tracker.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

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

    private Object badRequest(HttpServletRequest request, String message) {
        if (isHtmlRequest(request)) {
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
