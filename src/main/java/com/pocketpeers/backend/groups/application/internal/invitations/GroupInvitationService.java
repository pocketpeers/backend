package com.pocketpeers.backend.groups.application.internal.invitations;

import com.pocketpeers.backend.groups.application.internal.declarations.MembershipDeclarationService;
import com.pocketpeers.backend.groups.domain.model.entities.GroupInvitation;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupInvitationStatus;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupInvitationRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupRepository;
import com.pocketpeers.backend.operations.infrastructure.notifications.FcmNotificationService;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Invitaciones por nombre de usuario: el administrador invita, la persona
 * acepta (firmando la declaracion) o rechaza.
 *
 * <p>Convive con el codigo de invitacion, no lo reemplaza. El codigo sirve para
 * compartirlo por fuera de la app; esto, para cuando el administrador ya sabe
 * el usuario de la persona.</p>
 */
@Service
public class GroupInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupInvitationService.class);

    /** Una invitacion sin respuesta no queda abierta para siempre. */
    static final int EXPIRY_DAYS = 7;

    /**
     * Tras un rechazo, cuanto hay que esperar para volver a invitar. Sin esto,
     * rechazar no sirve de nada: el administrador puede reenviarla al instante.
     */
    static final int REINVITE_AFTER_REJECTION_DAYS = 3;

    /** Tope de invitaciones abiertas por grupo, contra el uso como spam. */
    static final int MAX_PENDING_PER_GROUP = 20;

    private final GroupInvitationRepository invitationRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final UserInformationRepository userInformationRepository;
    private final MembershipDeclarationService membershipDeclarationService;
    private final FcmNotificationService fcmNotificationService;
    private final Clock clock;

    @Autowired
    public GroupInvitationService(GroupInvitationRepository invitationRepository,
                                  GroupRepository groupRepository,
                                  GroupMemberRepository groupMemberRepository,
                                  UserRepository userRepository,
                                  UserInformationRepository userInformationRepository,
                                  MembershipDeclarationService membershipDeclarationService,
                                  FcmNotificationService fcmNotificationService) {
        this(invitationRepository, groupRepository, groupMemberRepository, userRepository,
                userInformationRepository, membershipDeclarationService, fcmNotificationService,
                Clock.systemDefaultZone());
    }

    GroupInvitationService(GroupInvitationRepository invitationRepository,
                           GroupRepository groupRepository,
                           GroupMemberRepository groupMemberRepository,
                           UserRepository userRepository,
                           UserInformationRepository userInformationRepository,
                           MembershipDeclarationService membershipDeclarationService,
                           FcmNotificationService fcmNotificationService,
                           Clock clock) {
        this.invitationRepository = invitationRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.userInformationRepository = userInformationRepository;
        this.membershipDeclarationService = membershipDeclarationService;
        this.fcmNotificationService = fcmNotificationService;
        this.clock = clock;
    }

    /** Si a esa persona se la puede invitar, y si no, por que. */
    public enum Availability {
        AVAILABLE,
        ALREADY_MEMBER,
        ALREADY_INVITED,
        RECENTLY_REJECTED
    }

    public record Candidate(User user, String fullName, String photo, Availability availability) {
    }

    /**
     * Busca a quien invitar, para mostrar su nombre y foto antes de confirmar.
     *
     * <p>Devolver vacio cuando no existe revela que ese usuario no esta
     * registrado. Se acepta a proposito: sin nombre y foto, un error de tipeo
     * manda la invitacion a otra persona, y el registro ya revela lo mismo al
     * avisar que un usuario esta ocupado.</p>
     */
    @Transactional(readOnly = true)
    public Optional<Candidate> findCandidate(Long groupId, String rawUsername) {
        return findUser(rawUsername).map(user -> new Candidate(
                user,
                fullNameOf(user),
                photoOf(user),
                availability(groupId, user, now())));
    }

    @Transactional
    public GroupInvitation invite(Long groupId, User admin, String rawUsername) {
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("El grupo no existe"));
        var invited = findUser(rawUsername)
                .orElseThrow(InvitedUserNotFoundException::new);
        var now = now();

        switch (availability(groupId, invited, now)) {
            case ALREADY_MEMBER -> throw new IllegalArgumentException("Esa persona ya es integrante del grupo");
            case ALREADY_INVITED -> throw new IllegalArgumentException("Esa persona ya tiene una invitacion pendiente");
            case RECENTLY_REJECTED -> throw new IllegalArgumentException(
                    "Esa persona rechazo la invitacion hace poco. Podras volver a invitarla en unos dias");
            case AVAILABLE -> {
            }
        }
        if (invitationRepository.countByGroup_IdAndStatusAndExpiresAtAfter(
                groupId, GroupInvitationStatus.PENDING, now) >= MAX_PENDING_PER_GROUP) {
            throw new IllegalArgumentException(
                    "El grupo ya tiene %d invitaciones pendientes. Espera a que respondan o cancela alguna"
                            .formatted(MAX_PENDING_PER_GROUP));
        }

        var invitation = invitationRepository.save(
                new GroupInvitation(group, invited, admin, now.plusDays(EXPIRY_DAYS)));

        push(invited.getId(), "Te invitaron a un grupo",
                displayName(admin) + " te invito a unirte a \"" + group.getName() + "\".",
                invitation, "group_invitation");
        return invitation;
    }

    /** Las invitaciones abiertas de una persona. */
    @Transactional
    public List<GroupInvitation> pendingFor(Long userId) {
        var now = now();
        var invitations = invitationRepository.findAllByInvitedUser_IdAndStatusAndExpiresAtAfterOrderByIdDesc(
                userId, GroupInvitationStatus.PENDING, now);
        // Si entro por su cuenta con el codigo, la invitacion ya no tiene sentido:
        // se cierra aqui en vez de mostrarle un "Aceptar" que fallaria.
        return invitations.stream()
                .filter(invitation -> {
                    if (isMember(invitation.getGroup().getId(), userId)) {
                        invitation.cancel(now);
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    /** Las invitaciones abiertas de un grupo, para que el administrador las vea o cancele. */
    @Transactional(readOnly = true)
    public List<GroupInvitation> pendingOfGroup(Long groupId) {
        return invitationRepository.findAllByGroup_IdAndStatusAndExpiresAtAfterOrderByIdDesc(
                groupId, GroupInvitationStatus.PENDING, now());
    }

    /**
     * Acepta y entra al grupo. La firma de la declaracion y el ingreso van en la
     * misma transaccion, igual que al entrar con codigo.
     */
    @Transactional
    public GroupMember accept(Long invitationId, User user, String acceptedVersion, String signatureImage) {
        var invitation = ownInvitation(invitationId, user);
        var now = now();
        var group = invitation.getGroup();
        if (isMember(group.getId(), user.getId())) {
            // Sin cerrarla aqui: la excepcion deshace la transaccion. La cierra
            // pendingFor la proxima vez que se listen.
            throw new IllegalArgumentException("Ya eres integrante de este grupo");
        }
        invitation.accept(now);

        membershipDeclarationService.sign(user.getId(), group.getId(), group.getName(),
                acceptedVersion, signatureImage);
        var member = groupMemberRepository.save(new GroupMember(group, user, GroupRole.MEMBER));

        push(invitation.getInvitedBy().getId(), "Invitacion aceptada",
                displayName(user) + " acepto tu invitacion y ya es parte de \"" + group.getName() + "\".",
                invitation, "group_invitation_accepted");
        return member;
    }

    @Transactional
    public GroupInvitation reject(Long invitationId, User user) {
        var invitation = ownInvitation(invitationId, user);
        invitation.reject(now());
        // Al administrador si se le avisa del rechazo, para que no se quede
        // esperando; el aviso no dice nada mas que eso.
        push(invitation.getInvitedBy().getId(), "Invitacion rechazada",
                displayName(user) + " no acepto la invitacion a \"" + invitation.getGroup().getName() + "\".",
                invitation, "group_invitation_rejected");
        return invitation;
    }

    @Transactional
    public GroupInvitation cancel(Long groupId, Long invitationId) {
        var invitation = invitationRepository.findById(invitationId)
                .filter(found -> found.getGroup().getId().equals(groupId))
                .orElseThrow(() -> new IllegalArgumentException("La invitacion no existe"));
        invitation.cancel(now());
        return invitation;
    }

    public String fullNameOf(User user) {
        return userInformationRepository.findByUserId(user.getId())
                .map(info -> info.getFullName())
                .orElse("");
    }

    public String photoOf(User user) {
        return userInformationRepository.findByUserId(user.getId())
                .map(info -> info.getPhoto())
                .orElse("");
    }

    private Availability availability(Long groupId, User user, LocalDateTime now) {
        if (isMember(groupId, user.getId())) {
            return Availability.ALREADY_MEMBER;
        }
        if (invitationRepository.existsByGroup_IdAndInvitedUser_IdAndStatusAndExpiresAtAfter(
                groupId, user.getId(), GroupInvitationStatus.PENDING, now)) {
            return Availability.ALREADY_INVITED;
        }
        var recentlyRejected = invitationRepository
                .findFirstByGroup_IdAndInvitedUser_IdAndStatusOrderByRespondedAtDesc(
                        groupId, user.getId(), GroupInvitationStatus.REJECTED)
                .map(GroupInvitation::getRespondedAt)
                .filter(respondedAt -> respondedAt != null
                        && respondedAt.plusDays(REINVITE_AFTER_REJECTION_DAYS).isAfter(now))
                .isPresent();
        return recentlyRejected ? Availability.RECENTLY_REJECTED : Availability.AVAILABLE;
    }

    /**
     * Primero la coincidencia exacta. Si no hay, se acepta la que coincide sin
     * mirar mayusculas, pero solo si es una: los usuarios distinguen mayusculas
     * y podrian existir «Ana» y «ana», y adivinar entre las dos seria invitar a
     * la persona equivocada.
     */
    private Optional<User> findUser(String rawUsername) {
        if (rawUsername == null) {
            return Optional.empty();
        }
        var username = rawUsername.trim();
        if (username.startsWith("@")) {
            username = username.substring(1).trim();
        }
        if (username.isEmpty()) {
            return Optional.empty();
        }
        var exact = userRepository.findByUsername(username);
        if (exact.isPresent()) {
            return exact;
        }
        var matches = userRepository.findAllByUsernameIgnoreCase(username);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    /** Solo la persona invitada responde su invitacion. */
    private GroupInvitation ownInvitation(Long invitationId, User user) {
        return invitationRepository.findById(invitationId)
                .filter(invitation -> invitation.getInvitedUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("La invitacion no existe"));
    }

    private boolean isMember(Long groupId, Long userId) {
        return groupMemberRepository.findByGroupIdAndUser_Id(groupId, userId).isPresent();
    }

    private String displayName(User user) {
        var fullName = fullNameOf(user);
        return fullName.isBlank() ? "@" + user.getUsername() : fullName;
    }

    private void push(Long userId, String title, String body, GroupInvitation invitation, String type) {
        try {
            fcmNotificationService.sendToUser(userId, title, body, Map.of(
                    "type", type,
                    "invitationId", String.valueOf(invitation.getId()),
                    "groupId", String.valueOf(invitation.getGroup().getId())));
        } catch (RuntimeException exc) {
            // El push es un extra: la invitacion ya quedo guardada y la app la
            // muestra igual en la pestana Grupos.
            LOGGER.warn("Could not push invitation notification. invitationId={}", invitation.getId(), exc);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** El usuario a invitar no existe. El controlador lo traduce a 404. */
    public static class InvitedUserNotFoundException extends RuntimeException {
        public InvitedUserNotFoundException() {
            super("No se encontro el usuario");
        }
    }
}
