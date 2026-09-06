package com.pocketpeers.backend.operations.domain.model;

import com.pocketpeers.backend.operations.domain.model.entities.PaymentReminder;
import com.pocketpeers.backend.operations.domain.model.valueobjects.NotificationDeliveryStatus;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentReminderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el seguimiento de entrega de las notificaciones.
 *
 * <p>Antes solo se guardaba que el recordatorio se habia creado, asi que un aviso
 * que la persona ignoro y uno que nunca le llego producian el mismo dato. Estas
 * pruebas fijan que ahora se distingan.</p>
 */
class PaymentReminderDeliveryTest {

    private PaymentReminder reminder() {
        return new PaymentReminder(null, null, PaymentReminderType.DUE_TODAY,
                "Vence hoy", "Tu pago del Grupo Casa vence hoy");
    }

    @Test
    @DisplayName("Un recordatorio nuevo arranca pendiente de envio")
    void nuevoArrancaPendiente() {
        var reminder = reminder();

        assertEquals(NotificationDeliveryStatus.PENDING, reminder.getDeliveryStatus());
        assertFalse(reminder.wasDelivered());
        assertNull(reminder.getSentAt());
        assertEquals(0, reminder.getDeliveredDevices());
    }

    @Test
    @DisplayName("Una entrega exitosa registra cuando y a cuantos dispositivos")
    void entregaExitosaQuedaRegistrada() {
        var reminder = reminder();

        reminder.markDelivered(2);

        assertEquals(NotificationDeliveryStatus.SENT, reminder.getDeliveryStatus());
        assertTrue(reminder.wasDelivered());
        assertEquals(2, reminder.getDeliveredDevices());
        assertNotNull(reminder.getSentAt());
        assertNull(reminder.getDeliveryDetail());
    }

    @Test
    @DisplayName("Sin dispositivo registrado no es lo mismo que un fallo de envio")
    void sinDispositivoSeDistingueDeUnFallo() {
        // Es la distincion que da sentido a todo esto: a esta persona no habia
        // por donde avisarle, y contarlo como error de Firebase distorsionaria
        // cualquier medicion del efecto de los recordatorios.
        var sinDispositivo = reminder();
        var fallido = reminder();

        sinDispositivo.markNotDelivered(NotificationDeliveryStatus.NO_DEVICE, null);
        fallido.markNotDelivered(NotificationDeliveryStatus.FAILED, "UNREGISTERED");

        assertEquals(NotificationDeliveryStatus.NO_DEVICE, sinDispositivo.getDeliveryStatus());
        assertEquals(NotificationDeliveryStatus.FAILED, fallido.getDeliveryStatus());
        assertFalse(sinDispositivo.wasDelivered());
        assertFalse(fallido.wasDelivered());
        assertEquals("UNREGISTERED", fallido.getDeliveryDetail());
    }

    @Test
    @DisplayName("El detalle del fallo se recorta para no desbordar la columna")
    void detalleLargoSeRecorta() {
        var reminder = reminder();

        reminder.markNotDelivered(NotificationDeliveryStatus.FAILED, "x".repeat(500));

        assertEquals(120, reminder.getDeliveryDetail().length());
    }

    @Test
    @DisplayName("Un fallo posterior a una entrega borra el conteo de dispositivos")
    void falloPosteriorLimpiaElConteo() {
        var reminder = reminder();
        reminder.markDelivered(3);

        reminder.markNotDelivered(NotificationDeliveryStatus.FAILED, "INTERNAL");

        assertEquals(0, reminder.getDeliveredDevices());
        assertFalse(reminder.wasDelivered());
    }

    @Test
    @DisplayName("Los registros historicos se reportan como desconocidos, no como pendientes")
    void historicosSonDesconocidos() throws Exception {
        // Las filas anteriores a este cambio tienen null en la columna. Tratarlas
        // como PENDING haria parecer que hay cientos de envios en cola que nadie
        // va a intentar nunca.
        var reminder = reminder();
        var field = PaymentReminder.class.getDeclaredField("deliveryStatus");
        field.setAccessible(true);
        field.set(reminder, null);

        assertEquals(NotificationDeliveryStatus.UNKNOWN, reminder.getDeliveryStatus());
        assertFalse(reminder.wasDelivered());
    }
}
