package app.hirecockpit.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail p = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        p.setTitle("Validation failed");
        p.setProperty("code", "VALIDATION_ERROR");
        p.setProperty("timestamp", Instant.now().toString());
        p.setProperty("fields", ex.getBindingResult().getFieldErrors().stream().map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage()))).toList());
        return p;
    }
}
