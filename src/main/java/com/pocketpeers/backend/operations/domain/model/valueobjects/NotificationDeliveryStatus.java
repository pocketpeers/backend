package com.pocketpeers.backend.operations.domain.model.valueobjects;

/**
 * Resultado del intento de entrega de una notificacion.
 *
 * <p>Es un enum y no un booleano porque "no llego" agrupa situaciones que hay
 * que poder separar. Un usuario que nunca registro un dispositivo no es lo mismo
 * que uno cuyo token vencio, ni que un fallo del servicio: las tres se veian
 * identicas cuando solo se guardaba si el envio fue exitoso.</p>
 *
 * <p>La distincion importa para el analisis del efecto de los recordatorios: sin
 * ella, un aviso que la persona ignoro y uno que nunca le llego producen el
 * mismo dato, y cualquier conclusion sobre si los recordatorios funcionan queda
 * contaminada.</p>
 */
public enum NotificationDeliveryStatus {

    /** Registrada, sin intento de envio todavia. */
    PENDING,

    /** Al menos un dispositivo acepto el mensaje. */
    SENT,

    /** El usuario no tiene ningun dispositivo registrado: nunca pudo llegarle. */
    NO_DEVICE,

    /** Se intento en todos sus dispositivos y ninguno acepto. */
    FAILED,

    /** Firebase no esta configurado en este entorno; no se intento nada. */
    SKIPPED,

    /**
     * Registro anterior a este seguimiento.
     *
     * <p>Existe para no confundir "no sabemos que paso" con "esta en cola". Al
     * analizar el efecto de los recordatorios, estas filas hay que excluirlas en
     * lugar de contarlas como entregadas o como fallidas.</p>
     */
    UNKNOWN
}
