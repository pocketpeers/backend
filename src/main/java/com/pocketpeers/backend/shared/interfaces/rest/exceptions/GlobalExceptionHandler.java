package com.pocketpeers.backend.shared.interfaces.rest.exceptions;

import com.pocketpeers.backend.operations.domain.exceptions.DuplicateReceiptException;
import com.pocketpeers.backend.users.domain.exceptions.CurrentPasswordMismatchException;
import com.pocketpeers.backend.users.domain.exceptions.IdentityLookupLimitException;
import com.pocketpeers.backend.users.domain.exceptions.IdentityMismatchException;
import com.pocketpeers.backend.users.domain.exceptions.InvalidCredentialsException;
import com.pocketpeers.backend.users.domain.exceptions.InvalidPasswordResetCodeException;
import com.pocketpeers.backend.users.domain.exceptions.InvalidSignUpCodeException;
import com.pocketpeers.backend.users.domain.exceptions.UsernameAlreadyTakenException;
import com.pocketpeers.backend.users.domain.exceptions.WeakPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> handle(IllegalArgumentException ex){
        Map<String, String> error = new HashMap<>();
        error.put("error", "Bad Request");
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> handle(MethodArgumentNotValidException ex){
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    /**
     * Credenciales incorrectas: 401, no 500.
     *
     * <p>Antes estas fallas caian en el manejador generico de abajo y la
     * aplicacion movil recibia un "Internal Server Error" cuando alguien
     * simplemente se equivocaba de contrasena. Una clave mal escrita no es un
     * fallo del servidor y la interfaz no puede distinguir uno de otro si ambos
     * llegan con el mismo codigo.</p>
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String,String>> handle(InvalidCredentialsException ex) {
        return error("Unauthorized", ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    /**
     * Contrasena actual incorrecta al cambiarla: 400, no 401.
     *
     * <p>La sesion sigue siendo valida; lo que fallo es un campo enviado. Si
     * respondiera 401, el cliente lo interpretaria como sesion vencida y cerraria
     * la sesion del usuario por un error de tipeo.</p>
     */
    @ExceptionHandler(CurrentPasswordMismatchException.class)
    public ResponseEntity<Map<String,String>> handle(CurrentPasswordMismatchException ex) {
        return error("Bad Request", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /** Nombre de usuario ocupado: 409, para que la app pueda sugerir otro. */
    @ExceptionHandler(UsernameAlreadyTakenException.class)
    public ResponseEntity<Map<String,String>> handle(UsernameAlreadyTakenException ex) {
        return error("Conflict", ex.getMessage(), HttpStatus.CONFLICT);
    }

    /** Contrasena que no cumple la politica: 400, con el motivo concreto. */
    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<Map<String,String>> handle(WeakPasswordException ex) {
        return error("Bad Request", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /** Codigo de recuperacion invalido o vencido: 400. */
    @ExceptionHandler(InvalidPasswordResetCodeException.class)
    public ResponseEntity<Map<String,String>> handle(InvalidPasswordResetCodeException ex) {
        return error("Bad Request", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /** Codigo de verificacion del correo invalido o vencido. */
    @ExceptionHandler(InvalidSignUpCodeException.class)
    public ResponseEntity<Map<String,String>> handle(InvalidSignUpCodeException ex) {
        return error("Bad Request", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * El nombre no corresponde al DNI: 422, con un codigo propio en {@code error}.
     *
     * <p>No es un 400 generico porque la aplicacion movil tiene que reconocerlo
     * para mostrar un dialogo que pida corregir el nombre, en vez del aviso
     * pasajero que usa para los demas errores del formulario.</p>
     */
    @ExceptionHandler(IdentityMismatchException.class)
    public ResponseEntity<Map<String,String>> handle(IdentityMismatchException ex) {
        return error(IdentityMismatchException.CODE, ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /** Topes de verificacion de DNI agotados: 429, con un codigo propio en {@code error}. */
    @ExceptionHandler(IdentityLookupLimitException.class)
    public ResponseEntity<Map<String,String>> handle(IdentityLookupLimitException ex) {
        return error(IdentityLookupLimitException.CODE, ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Comprobante ya registrado: 409, con el gasto en conflicto en el cuerpo.
     *
     * <p>Va aparte del 400 generico porque no es un campo mal enviado sino un
     * choque con algo que ya existe, y porque la aplicacion movil necesita los
     * identificadores para poder ofrecer "ver el gasto donde ya esta" en vez de
     * limitarse a mostrar el texto del error.</p>
     */
    @ExceptionHandler(DuplicateReceiptException.class)
    public ResponseEntity<Map<String,String>> handle(DuplicateReceiptException ex) {
        Map<String, String> body = new HashMap<>();
        body.put("error", "Conflict");
        body.put("message", ex.getMessage());
        body.put("signal", ex.getSignal().name());
        body.put("conflictingReceiptId", String.valueOf(ex.getConflictingReceiptId()));
        body.put("conflictingExpenseId", String.valueOf(ex.getConflictingExpenseId()));
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String,String>> handle(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String,String>> error(String title, String message, HttpStatus status) {
        Map<String, String> body = new HashMap<>();
        body.put("error", title);
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
