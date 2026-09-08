package com.pocketpeers.backend.pbl.domain.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recalculo nocturno de PeerScore.
 *
 * <p>Hace falta porque el score se mueve sin que nadie haga nada: el peso de
 * cada evento decae de forma continua, asi que el historial de quien dejo de
 * usar la app envejece hora a hora. Sin esta corrida, su score se congelaria en
 * el valor que tenia el dia de su ultimo pago y afirmaria una confianza que ya
 * no esta respaldada por nada reciente.</p>
 *
 * <p>Es tambien el unico momento en que alguien puede cambiar de nivel sin
 * haber registrado un evento, y por eso reevalua las insignias de nivel de
 * quienes cruzaron uno.</p>
 */
@Component
public class PeerScoreScheduledTasks {

    private final PeerScoreService peerScoreService;
    private final PblCommandService pblCommandService;
    private final GroupStatsProvider groupStatsProvider;

    public PeerScoreScheduledTasks(PeerScoreService peerScoreService,
                                   PblCommandService pblCommandService,
                                   GroupStatsProvider groupStatsProvider) {
        this.peerScoreService = peerScoreService;
        this.pblCommandService = pblCommandService;
        this.groupStatsProvider = groupStatsProvider;
    }

    // A las 03:30 no se cruza con ningun otro trabajo: los castigos por
    // vencimiento corren a las 00:05 y los recordatorios a las 08:00, asi que
    // los eventos de la noche ya estan registrados cuando esto empieza.
    @Scheduled(cron = "0 30 3 * * ?", zone = "America/Lima")
    public void recalculateEveryScore() {
        // Las estadisticas se tiran a proposito antes de empezar. La corrida
        // recorre a todos los usuarios con una sola foto, y esa foto tiene que
        // reflejar los eventos del dia y no lo que quedo cacheado en la tarde.
        groupStatsProvider.invalidate();

        for (Long userId : peerScoreService.recalculateAll()) {
            pblCommandService.refreshLevelBadges(userId);
        }
    }
}
