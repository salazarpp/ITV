package com.pokesync.presentation.exception;

import com.pokesync.application.exception.AuthenticationFailedException;
import com.pokesync.application.exception.PasswordTooLongException;
import com.pokesync.application.exception.UserAlreadyExistsException;
import com.pokesync.application.exception.InvalidPokemonRequestException;
import com.pokesync.application.exception.PokemonNotFoundException;
import com.pokesync.application.exception.PokemonAlreadySynchronizedException;
import com.pokesync.application.exception.PokeApiUnavailableException;
import com.pokesync.application.exception.PokeApiTimeoutException;
import com.pokesync.infrastructure.observability.SafeExceptionDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiError> authenticationFailed() {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    ResponseEntity<ApiError> duplicateUser() {
        return error(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "Username or email is already registered");
    }

    @ExceptionHandler(PasswordTooLongException.class)
    ResponseEntity<ApiError> invalidArgument() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request contains invalid values");
    }

    @ExceptionHandler(InvalidPokemonRequestException.class)
    ResponseEntity<ApiError> invalidPokemon() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request contains invalid values");
    }

    @ExceptionHandler(PokemonNotFoundException.class)
    ResponseEntity<ApiError> missingPokemon() {
        return error(HttpStatus.NOT_FOUND, "POKEMON_NOT_FOUND", "Pokemon was not found");
    }

    @ExceptionHandler(PokemonAlreadySynchronizedException.class)
    ResponseEntity<ApiError> duplicatePokemon() {
        return error(HttpStatus.CONFLICT, "POKEMON_ALREADY_SYNCHRONIZED", "Pokemon is already synchronized");
    }

    @ExceptionHandler(PokeApiUnavailableException.class)
    ResponseEntity<ApiError> upstreamUnavailable(PokeApiUnavailableException exception) {
        log.error("POKESYNC-UPSTREAM-ERR-502 | PokeAPI failure diagnostic={}",
                SafeExceptionDiagnostics.describe(exception));
        return error(HttpStatus.BAD_GATEWAY, "POKE_API_UNAVAILABLE", "Pokemon provider is unavailable");
    }

    @ExceptionHandler(PokeApiTimeoutException.class)
    ResponseEntity<ApiError> upstreamTimeout(PokeApiTimeoutException exception) {
        log.error("POKESYNC-UPSTREAM-ERR-504 | PokeAPI timeout diagnostic={}",
                SafeExceptionDiagnostics.describe(exception));
        return error(HttpStatus.GATEWAY_TIMEOUT, "POKE_API_TIMEOUT", "Pokemon provider timed out");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedError(Exception exception) {
        log.error("POKESYNC-API-ERR-500 | Unhandled failure diagnostic={}",
                SafeExceptionDiagnostics.describe(exception));
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = status.value() == 400 ? "INVALID_REQUEST" : "HTTP_ERROR";
        String message = status.value() == 400 ? "Request contains invalid values" : "Request could not be processed";
        log.warn("POKESYNC-API-ERR-{} | framework rejection type={}", status.value(), exception.getClass().getName());
        if (exception instanceof MethodArgumentNotValidException validation) {
            validation.getBindingResult().getFieldErrors().forEach(field ->
                    log.warn("POKESYNC-VALIDATION-ERR-400 | field={} constraint={}", field.getField(), field.getCode()));
        }
        return super.handleExceptionInternal(exception,
                ApiError.of(status.value(), code, message), headers, status, request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        if (status.is4xxClientError()) {
            log.warn("POKESYNC-API-ERR-{} | code={}", status.value(), code);
        }
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message));
    }
}
