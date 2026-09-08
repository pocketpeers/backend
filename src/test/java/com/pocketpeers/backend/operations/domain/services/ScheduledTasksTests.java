package com.pocketpeers.backend.operations.domain.services;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledTasksTests {

    @Mock
    private ExpensesNotificationService expensesNotificationService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PblCommandService pblCommandService;

    @Mock
    private PaymentCommandService paymentCommandService;

    @InjectMocks
    private ScheduledTasks scheduledTasks;

    @Test
    void dailyRemindersDoNotRegisterOverduePenalties() {
        // Los dos trabajos corren a horas distintas: los castigos a medianoche,
        // cuando cierra el plazo, y los recordatorios a las 08:00, cuando una
        // notificacion es bienvenida.
        scheduledTasks.sendDailyPaymentReminders();

        verify(expensesNotificationService).createPaymentReminders();
        verifyNoInteractions(paymentCommandService, paymentRepository, pblCommandService);
    }

    @Test
    void overdueRunDelegatesToThePaymentService() {
        // El planificador solo decide cuando. Que registrar lo decide el
        // servicio de pagos, que es quien sabe leer del pago el acreedor, el
        // monto y el plazo que el evento de reputacion necesita.
        scheduledTasks.registerOverduePaymentPenalties();

        verify(paymentCommandService).registerOverduePenalties();
        verifyNoInteractions(expensesNotificationService, pblCommandService);
    }
}
