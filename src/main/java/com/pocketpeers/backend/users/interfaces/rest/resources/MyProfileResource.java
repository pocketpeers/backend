package com.pocketpeers.backend.users.interfaces.rest.resources;

/**
 * El perfil propio, el unico que incluye el documento de identidad.
 *
 * <p>Existe separado de {@link UserInformationResource} a proposito. Aquel lo
 * devuelven tambien {@code GET /{id}} y {@code GET /userId/{userId}}, que sirven
 * perfiles ajenos: anadir ahi el documento habria dejado el DNI de cualquier
 * usuario al alcance de cualquier otro con sesion iniciada.</p>
 *
 * <p>Separarlos hace que esa garantia sea estructural y no una convencion que
 * alguien pueda romper sin darse cuenta el dia que reutilice el assembler
 * equivocado.</p>
 */
public record MyProfileResource(Long id,
                                String username,
                                String fullName,
                                String phoneNumber,
                                String photo,
                                String email,
                                Long userId,
                                /** Nulo en las cuentas anteriores a que se pidiera el documento. */
                                String documentType,
                                String documentNumber) {
}
