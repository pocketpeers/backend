package com.pocketpeers.backend.groups.interfaces.rest;

import com.pocketpeers.backend.groups.application.internal.declarations.MembershipDeclarationSealer;
import com.pocketpeers.backend.groups.application.internal.declarations.MembershipDeclarationService;
import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMembershipDeclaration;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Declaracion jurada de ingreso a grupos.
 *
 * <p>Ruta propia y no bajo {@code /groups}: alli {@code /groups/{groupId}}
 * captura cualquier segmento, y una ruta literal nueva quedaria a merced del
 * orden en que Spring resuelve los patrones.</p>
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "api/v1/group-declarations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Group Declarations", description = "Declaracion jurada al unirse a un grupo")
public class MembershipDeclarationController {

    private final MembershipDeclarationService declarationService;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public MembershipDeclarationController(MembershipDeclarationService declarationService,
                                           GroupRepository groupRepository,
                                           GroupMemberRepository groupMemberRepository,
                                           UserRepository userRepository) {
        this.declarationService = declarationService;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    /**
     * Texto que el usuario tiene que leer antes de firmar, ya con su nombre, su
     * DNI y el grupo. Con {@code token} es para unirse con codigo; con
     * {@code groupName}, para el grupo que se esta creando.
     */
    @Operation(summary = "Preview the declaration the authenticated user must sign")
    @GetMapping("/preview")
    public ResponseEntity<MembershipDeclarationService.Preview> preview(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String groupName,
            Authentication authentication) {
        var userId = authenticatedUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String resolvedGroupName;
        if (token != null && !token.isBlank()) {
            var group = groupRepository.findByInvitationToken(token.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token"));
            resolvedGroupName = group.getName();
        } else if (groupName != null && !groupName.isBlank()) {
            resolvedGroupName = groupName.trim();
        } else {
            throw new IllegalArgumentException("Indica el codigo de invitacion o el nombre del grupo");
        }

        return ResponseEntity.ok(declarationService.preview(userId, resolvedGroupName));
    }

    /** Las declaraciones que firmo el usuario autenticado. */
    @Operation(summary = "Declarations signed by the authenticated user")
    @GetMapping("/me")
    public ResponseEntity<List<SignedDeclarationResource>> mine(Authentication authentication) {
        var userId = authenticatedUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(toResources(declarationService.declarationsOf(userId)));
    }

    /**
     * Las declaraciones firmadas en un grupo. Solo para su administrador, y solo
     * las de ESE grupo.
     */
    @Operation(summary = "Declarations signed in a group (group admin only)")
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<List<SignedDeclarationResource>> ofGroup(@PathVariable Long groupId,
                                                                   Authentication authentication) {
        var userId = authenticatedUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!isGroupAdmin(groupId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(toResources(declarationService.declarationsOfGroup(groupId)));
    }

    /** El PDF firmado. Lo descarga quien lo firmo o el administrador de ese grupo. */
    @Operation(summary = "Download the signed declaration PDF")
    @GetMapping(value = "/{declarationId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long declarationId, Authentication authentication) {
        var userId = authenticatedUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return declarationService.declaration(declarationId)
                .filter(declaration -> declaration.getUserId().equals(userId)
                        || isGroupAdmin(declaration.getGroupId(), userId))
                .map(declaration -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                                .filename("declaracion-jurada-%d.pdf".formatted(declaration.getId()))
                                .build().toString())
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(declaration.getSignedPdf()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Recalcula todos los sellos y dice si la cadena esta intacta. No devuelve
     * datos personales, solo el conteo y, si algo no cuadra, el id de la primera
     * fila alterada.
     */
    @Operation(summary = "Verify that no signed declaration was altered")
    @GetMapping("/verify")
    public ResponseEntity<MembershipDeclarationSealer.ChainVerification> verify() {
        return ResponseEntity.ok(declarationService.verifyChain());
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    private boolean isGroupAdmin(Long groupId, Long userId) {
        return groupMemberRepository.existsByGroupIdAndUser_IdAndRole(groupId, userId, GroupRole.ADMIN);
    }

    /**
     * Junta cada declaracion con el nombre ACTUAL de su grupo. El nombre con el
     * que se firmo no cambia —esta sellado y es lo que dice el PDF—, pero si el
     * grupo se renombro, la lista tiene que dejar claro de que grupo se trata.
     */
    private List<SignedDeclarationResource> toResources(List<GroupMembershipDeclaration> declarations) {
        var groupIds = declarations.stream().map(GroupMembershipDeclaration::getGroupId).distinct().toList();
        Map<Long, String> currentNames = groupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(Group::getId, Group::getName, (a, b) -> a));
        return declarations.stream()
                .map(declaration -> SignedDeclarationResource.from(declaration,
                        currentNames.get(declaration.getGroupId())))
                .toList();
    }

    /**
     * @param groupName        nombre del grupo cuando se firmo, el que aparece en el PDF.
     * @param currentGroupName nombre actual; nulo si el grupo ya no existe.
     */
    public record SignedDeclarationResource(Long id, Long groupId, String groupName, String currentGroupName,
                                            Long userId, String fullName, String declarationVersion,
                                            @JsonFormat(shape = JsonFormat.Shape.STRING,
                                                    pattern = "yyyy-MM-dd HH:mm:ss", timezone = "America/Lima")
                                            Instant acceptedAt,
                                            String pdfHash, String recordHash) {
        static SignedDeclarationResource from(GroupMembershipDeclaration declaration, String currentGroupName) {
            return new SignedDeclarationResource(declaration.getId(), declaration.getGroupId(),
                    declaration.getGroupName(), currentGroupName, declaration.getUserId(),
                    declaration.getFullName(), declaration.getDeclarationVersion(),
                    declaration.getAcceptedAt(), declaration.getPdfHash(), declaration.getRecordHash());
        }
    }
}
