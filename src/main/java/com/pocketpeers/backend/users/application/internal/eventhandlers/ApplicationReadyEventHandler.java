package com.pocketpeers.backend.users.application.internal.eventhandlers;

import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
import com.pocketpeers.backend.users.domain.model.commands.SeedRolesCommand;
import com.pocketpeers.backend.users.domain.services.RoleCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
public class ApplicationReadyEventHandler {
    private final RoleCommandService roleCommandService;
    private final PblCommandService pblCommandService;
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationReadyEventHandler.class);

    public ApplicationReadyEventHandler(RoleCommandService roleCommandService, PblCommandService pblCommandService) {
        this.roleCommandService = roleCommandService;
        this.pblCommandService = pblCommandService;
    }

    /**
     * Handle the ApplicationReadyEvent
     * This method is used to seed the roles and PBL badge catalog
     *
     * @param event the ApplicationReadyEvent the event to handle
     */
    @EventListener
    public void on(ApplicationReadyEvent event) {
        var applicationName = event.getApplicationContext().getId();
        LOGGER.info("Starting to verify if roles seeding is needed for {} at {}", applicationName, currentTimestamp());
        var seedRolesCommand = new SeedRolesCommand();
        roleCommandService.handle(seedRolesCommand);
        LOGGER.info("Roles seeding verification finished for {} at {}", applicationName, currentTimestamp());

        LOGGER.info("Starting to verify if PBL badge catalog seeding is needed for {} at {}", applicationName, currentTimestamp());
        pblCommandService.seedDefaultBadges();
        LOGGER.info("PBL badge catalog seeding verification finished for {} at {}", applicationName, currentTimestamp());
    }

    private Timestamp currentTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }
}
