package com.cocos.broker.infrastructure.web.error;

import com.cocos.broker.application.ConcurrentRequestException;
import com.cocos.broker.domain.exception.InstrumentNotFoundException;
import com.cocos.broker.domain.exception.InvalidOrderException;
import com.cocos.broker.domain.exception.OrderNotCancellableException;
import com.cocos.broker.domain.exception.OrderNotFoundException;
import com.cocos.broker.domain.exception.UserNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

/**
 * Traduce las excepciones a respuestas RFC 7807 (application/problem+json).
 *
 * <p>Importante: el rechazo de una orden por fondos/acciones insuficientes NO
 * llega acá; es un estado de negocio (REJECTED) que se persiste y se devuelve
 * con 201. Aquí solo se mapean errores reales de protocolo/entrada.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            UserNotFoundException.class,
            InstrumentNotFoundException.class,
            OrderNotFoundException.class
    })
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler({OrderNotCancellableException.class, ConcurrentRequestException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Operación no permitida en el estado actual", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ProblemDetail handleInvalidOrder(InvalidOrderException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Orden inválida", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Error de validación",
                "La solicitud contiene campos inválidos");
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::toError)
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Error de validación", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud malformada",
                "El cuerpo de la solicitud es inválido o ilegible");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                "El parámetro '" + ex.getName() + "' tiene un valor inválido");
    }

    /** Propiedad de ordenamiento inexistente (p. ej. {@code ?sort=foo}). */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                "La propiedad '" + ex.getPropertyName() + "' no existe");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        // Errores estándar de Spring MVC (ruta inexistente → 404, método no soportado → 405,
        // media type no soportado → 415, etc.) ya traen su propio ProblemDetail: lo respetamos.
        // Sin esto, el catch-all los convertiría en 500.
        if (ex instanceof ErrorResponse errorResponse) {
            return errorResponse.getBody();
        }
        log.error("Error no controlado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado");
    }

    private static Map<String, String> toError(ObjectError error) {
        String field = (error instanceof FieldError fe) ? fe.getField() : error.getObjectName();
        return Map.of("field", field, "message", error.getDefaultMessage() == null ? "" : error.getDefaultMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setDetail(detail);
        return pd;
    }
}
