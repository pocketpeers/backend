package com.pocketpeers.backend.users.domain.exceptions;

/**
 * La contrasena actual no coincide, al intentar cambiarla.
 *
 * <p>Se separa de {@link InvalidCredentialsException} aunque el chequeo sea el
 * mismo, porque el codigo HTTP debe ser distinto. Aqui la sesion es valida: lo
 * que esta mal es un dato del formulario, no la autenticacion. Devolver 401
 * llevaria al cliente a concluir que la sesion vencio y a cerrarla, dejando al
 * usuario fuera por escribir mal su clave actual.</p>
 */
public class CurrentPasswordMismatchException extends RuntimeException {
    public CurrentPasswordMismatchException() {
        super("La contrasena actual no es correcta");
    }
}
