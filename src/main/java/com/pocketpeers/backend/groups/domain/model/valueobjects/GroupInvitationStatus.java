package com.pocketpeers.backend.groups.domain.model.valueobjects;

/**
 * Estado de una invitacion por nombre de usuario.
 *
 * <p>No hay un estado "vencida": una invitacion PENDING cuya fecha de
 * vencimiento ya paso se trata como vencida al leerla. Asi no hace falta una
 * tarea programada que recorra la tabla solo para cambiarle el estado.</p>
 */
public enum GroupInvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED
}
