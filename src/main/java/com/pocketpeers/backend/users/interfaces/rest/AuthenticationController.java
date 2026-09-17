package com.pocketpeers.backend.users.interfaces.rest;

import com.pocketpeers.backend.users.domain.model.commands.ChangePasswordCommand;
import com.pocketpeers.backend.users.domain.model.commands.ConfirmPasswordResetCommand;
import com.pocketpeers.backend.users.domain.model.commands.RequestPasswordResetCommand;
import com.pocketpeers.backend.users.domain.services.UserCommandService;
import com.pocketpeers.backend.users.interfaces.rest.resources.AuthenticatedUserResource;
import com.pocketpeers.backend.users.interfaces.rest.resources.ChangePasswordResource;
import com.pocketpeers.backend.users.interfaces.rest.resources.ConfirmPasswordResetResource;
import com.pocketpeers.backend.users.interfaces.rest.resources.MessageResource;
import com.pocketpeers.backend.users.interfaces.rest.resources.RequestPasswordResetResource;
import com.pocketpeers.backend.users.interfaces.rest.resources.SignInResource;
import com.pocketpeers.backend.users.interfaces.rest.resources.SignUpResource;
import com.pocketpeers.backend.users.interfaces.rest.resources.UserResource;
import com.pocketpeers.backend.users.interfaces.rest.transform.AuthenticatedUserResourceFromEntityAssembler;
import com.pocketpeers.backend.users.interfaces.rest.transform.SignInCommandFromResourceAssembler;
import com.pocketpeers.backend.users.interfaces.rest.transform.SignUpCommandFromResourceAssembler;
import com.pocketpeers.backend.users.interfaces.rest.transform.UserResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "/api/v1/authentication", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Authentication Endpoints")
public class AuthenticationController {
    private final UserCommandService userCommandService;

    public AuthenticationController(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    /**
     * Handles the sign-in request.
     * @param signInResource the sign-in request body.
     * @return the authenticated user resource.
     */
    @PostMapping("/sign-in")
    public ResponseEntity<AuthenticatedUserResource> signIn(@RequestBody SignInResource signInResource) {
        var signInCommand = SignInCommandFromResourceAssembler.toCommandFromResource(signInResource);
        var authenticatedUser = userCommandService.handle(signInCommand);
        if (authenticatedUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var authenticatedUserResource = AuthenticatedUserResourceFromEntityAssembler.toResourceFromEntity(authenticatedUser.get().getLeft(), authenticatedUser.get().getRight());
        return ResponseEntity.ok(authenticatedUserResource);
    }

    /**
     * Handles the sign-up request.
     * @param signUpResource the sign-up request body.
     * @return the created user resource.
     */
    @PostMapping("/sign-up")
    public ResponseEntity<UserResource> signUp(@RequestBody SignUpResource signUpResource) {
        var signUpCommand = SignUpCommandFromResourceAssembler.toCommandFromResource(signUpResource);
        var user = userCommandService.handle(signUpCommand);
        if (user.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var userResource = UserResourceFromEntityAssembler.toResourceFromEntity(user.get());
        return new ResponseEntity<>(userResource, HttpStatus.CREATED);

    }

    /**
     * Solicita un codigo para restablecer una contrasena olvidada.
     *
     * <p>Responde siempre 200 con el mismo mensaje, exista o no el correo. Una
     * respuesta distinta permitiria usar este endpoint para averiguar que correos
     * estan registrados en la plataforma.</p>
     */
    @Operation(summary = "Solicitar codigo de recuperacion",
            description = "Envia un codigo de 6 digitos al correo. Responde igual exista o no la cuenta.")
    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResource> requestPasswordReset(
            @RequestBody RequestPasswordResetResource resource) {
        userCommandService.handle(new RequestPasswordResetCommand(resource.email()));
        return ResponseEntity.ok(new MessageResource(
                "Si el correo esta registrado, recibiras un codigo en unos minutos."));
    }

    /**
     * Canjea el codigo recibido por correo por una contrasena nueva.
     */
    @Operation(summary = "Confirmar recuperación de contraseña",
            description = "Valida el código y establece la contraseña nueva.")
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<MessageResource> confirmPasswordReset(
            @RequestBody ConfirmPasswordResetResource resource) {
        userCommandService.handle(new ConfirmPasswordResetCommand(
                resource.email(), resource.code(), resource.newPassword()));
        return ResponseEntity.ok(new MessageResource("Tu contraseña fue actualizada."));
    }

    /**
     * Cambia la contrasena de un usuario que ya inicio sesion.
     *
     * <p>El usuario sale del token, no del cuerpo de la peticion: si viniera del
     * cuerpo, cualquiera con una sesion valida podria cambiarle la contrasena a
     * otra persona.</p>
     */
    @Operation(summary = "Cambiar contraseña",
            description = "Requiere sesión iniciada y conocer la contraseña actual.")
    @PutMapping("/password")
    public ResponseEntity<MessageResource> changePassword(
            @RequestBody ChangePasswordResource resource) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userCommandService.handle(new ChangePasswordCommand(
                authentication.getName(), resource.currentPassword(), resource.newPassword()));
        return ResponseEntity.ok(new MessageResource("Tu contraseña fue actualizada."));
    }
}