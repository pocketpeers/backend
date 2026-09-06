package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.valueobjects.NotificationDeliveryStatus;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentReminderType;
import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "payment_reminders")
public class PaymentReminder extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentReminderType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    private LocalDateTime readAt;

    /**
     * Como termino el intento de entrega.
     *
     * <p>Antes solo se guardaba que la notificacion se habia creado. Eso deja sin
     * respuesta la pregunta que importa cuando se mide el efecto de los
     * recordatorios: si la persona no pago, fue porque ignoro el aviso o porque
     * nunca le llego.</p>
     *
     * <p>La columna se deja anulable a proposito. El esquema se genera con
     * {@code ddl-auto=update}, y agregar una columna NOT NULL a una tabla que ya
     * tiene filas falla. Los recordatorios anteriores a este cambio quedan con
     * null, que se interpreta como "no se registro": ver
     * {@link #getDeliveryStatus()}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotificationDeliveryStatus deliveryStatus = NotificationDeliveryStatus.PENDING;

    /** Momento en que al menos un dispositivo acepto el mensaje. */
    private LocalDateTime sentAt;

    /**
     * Cuantos dispositivos del usuario aceptaron el mensaje.
     *
     * <p>Es {@code Integer} y no {@code int} por una razon concreta: Hibernate
     * mapea los primitivos como NOT NULL, y agregar una columna NOT NULL a una
     * tabla que ya tiene filas falla. Con {@code ddl-auto=update} ese fallo se
     * registra pero no detiene el arranque, asi que la aplicacion levanta sin la
     * columna y revienta en la primera consulta.</p>
     */
    private Integer deliveredDevices;

    /** Codigo de error del ultimo fallo, para diagnosticar sin revisar los logs. */
    @Column(length = 120)
    private String deliveryDetail;

    public PaymentReminder() {
    }

    public PaymentReminder(User user, Payment payment, PaymentReminderType type, String title, String body) {
        this.user = user;
        this.payment = payment;
        this.type = type;
        this.title = title;
        this.body = body;
        this.deliveryStatus = NotificationDeliveryStatus.PENDING;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead() {
        this.readAt = LocalDateTime.now();
    }

    /** Registra que la entrega funciono en al menos un dispositivo. */
    public void markDelivered(int devices) {
        this.deliveryStatus = NotificationDeliveryStatus.SENT;
        this.deliveredDevices = devices;
        this.sentAt = LocalDateTime.now();
        this.deliveryDetail = null;
    }

    /** Registra que el envio no prospero, con el motivo. */
    public void markNotDelivered(NotificationDeliveryStatus status, String detail) {
        this.deliveryStatus = status;
        this.deliveredDevices = 0;
        // El detalle se recorta porque los mensajes de Firebase pueden ser largos
        // y aqui solo interesa el codigo para agrupar fallos.
        this.deliveryDetail = detail == null || detail.length() <= 120
                ? detail
                : detail.substring(0, 120);
    }

    /** Si el aviso efectivamente llego a algun dispositivo. */
    public boolean wasDelivered() {
        return deliveryStatus == NotificationDeliveryStatus.SENT;
    }

    /**
     * Estado de entrega, tratando los registros historicos como desconocidos.
     *
     * <p>Las filas creadas antes de que existiera este seguimiento tienen null.
     * Devolverlas como PENDING seria mentir: nadie va a intentar enviarlas, y al
     * analizar el efecto de los recordatorios apareceria como si hubiera cientos
     * de envios en cola. UNKNOWN dice lo que realmente se sabe de ellas.</p>
     */
    public NotificationDeliveryStatus getDeliveryStatus() {
        return deliveryStatus == null ? NotificationDeliveryStatus.UNKNOWN : deliveryStatus;
    }

    /** Los registros anteriores a este seguimiento no tienen conteo: cuentan como cero. */
    public int getDeliveredDevices() {
        return deliveredDevices == null ? 0 : deliveredDevices;
    }
}
