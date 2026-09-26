package com.pocketpeers.backend.groups.application.internal.declarations;

import com.pocketpeers.backend.groups.domain.model.entities.GroupMembershipDeclaration;
import com.pocketpeers.backend.groups.domain.model.valueobjects.MembershipDeclarationTemplate;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMembershipDeclarationRepository;
import com.pocketpeers.backend.shared.domain.exceptions.OutdatedClientException;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Declaracion jurada que se firma al entrar a un grupo: se muestra, se firma y
 * se verifica aqui.
 *
 * <p>No se ancla en Solana: queda en la base, sellada con HMAC y encadenada.
 * Prueba que la fila no se altero despues de firmarse; no prueba que lo
 * declarado sea cierto, que es justamente lo que la declaracion le pide al
 * usuario asumir como propio.</p>
 */
@Service
public class MembershipDeclarationService {

    /** Clave arbitraria pero fija del candado consultivo de la cadena. */
    private static final long CHAIN_LOCK_KEY = 0x5050_4445_434CL;

    private final GroupMembershipDeclarationRepository declarationRepository;
    private final UserInformationRepository userInformationRepository;
    private final MembershipDeclarationSealer sealer;
    private final MembershipDeclarationPdfRenderer pdfRenderer = new MembershipDeclarationPdfRenderer();

    /**
     * Si no se configura una clave propia, se usa la del JWT para que la
     * aplicacion arranque sin tocar nada. Ojo: si luego se rota esa clave, las
     * declaraciones anteriores dejan de verificar. Lo recomendable es fijar
     * {@code group-declarations.hmac-secret} y no cambiarla.
     */
    public MembershipDeclarationService(GroupMembershipDeclarationRepository declarationRepository,
                                        UserInformationRepository userInformationRepository,
                                        @Value("${group-declarations.hmac-secret:${authorization.jwt.secret}}")
                                        String hmacSecret) {
        this.declarationRepository = declarationRepository;
        this.userInformationRepository = userInformationRepository;
        this.sealer = new MembershipDeclarationSealer(hmacSecret);
    }

    public Preview preview(Long userId, String groupName) {
        return new Preview(MembershipDeclarationTemplate.CURRENT_VERSION, render(profileOf(userId), groupName));
    }

    /**
     * Guarda la firma: genera el PDF con la firma dibujada incrustada y lo sella.
     * Tiene que correr dentro de la transaccion que agrega al miembro: si el
     * ingreso falla, la firma no queda, y al reves.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupMembershipDeclaration sign(Long userId, Long groupId, String groupName, String acceptedVersion,
                                           String signaturePngBase64) {
        // Sin version ni firma no es alguien que se salto un paso: es una
        // version de la app anterior a la declaracion, que no sabe pedirla.
        if (acceptedVersion == null && signaturePngBase64 == null) {
            throw new OutdatedClientException();
        }
        if (!MembershipDeclarationTemplate.CURRENT_VERSION.equals(acceptedVersion)) {
            throw new IllegalArgumentException(
                    "Debes aceptar la declaracion jurada vigente (version %s) para unirte al grupo"
                            .formatted(MembershipDeclarationTemplate.CURRENT_VERSION));
        }
        var signature = SignatureImage.decode(signaturePngBase64);

        var profile = profileOf(userId);
        var document = profile.getIdentityDocument();
        var documentType = document == null ? null : document.type().name();
        var documentNumber = document == null ? null : document.number();
        var text = render(profile, groupName);
        var textHash = sealer.sha256(text);
        var acceptedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        var pdf = pdfRenderer.render(text, signature, new MembershipDeclarationPdfRenderer.SignatureBlock(
                profile.getFullName(),
                documentNumber == null ? "Documento no registrado" : documentType + " N.° " + documentNumber,
                groupName,
                MembershipDeclarationTemplate.CURRENT_VERSION,
                acceptedAt,
                textHash));
        var pdfHash = sealer.sha256(pdf);

        declarationRepository.acquireChainLock(CHAIN_LOCK_KEY);
        var previousHash = declarationRepository.findTopByOrderByIdDesc()
                .map(GroupMembershipDeclaration::getRecordHash)
                .orElse(MembershipDeclarationSealer.GENESIS_HASH);

        var recordHash = sealer.recordHash(previousHash, groupId, groupName, userId, profile.getFullName(),
                documentType, documentNumber, MembershipDeclarationTemplate.CURRENT_VERSION,
                textHash, pdfHash, acceptedAt);

        return declarationRepository.save(new GroupMembershipDeclaration(
                groupId, groupName, userId, profile.getFullName(), documentType, documentNumber,
                MembershipDeclarationTemplate.CURRENT_VERSION, text, textHash, pdf, pdfHash,
                acceptedAt, previousHash, recordHash));
    }

    /** Quien puede verla lo decide el controlador: su firmante o el administrador del grupo. */
    @Transactional(readOnly = true)
    public Optional<GroupMembershipDeclaration> declaration(Long declarationId) {
        return declarationRepository.findById(declarationId);
    }

    /**
     * Todas las firmas del grupo, incluidas las de quienes ya salieron: la
     * declaracion es historial y no deja de existir porque el miembro se vaya.
     */
    @Transactional(readOnly = true)
    public List<GroupMembershipDeclaration> declarationsOfGroup(Long groupId) {
        return declarationRepository.findAllByGroupIdOrderByIdAsc(groupId);
    }

    @Transactional(readOnly = true)
    public MembershipDeclarationSealer.ChainVerification verifyChain() {
        return sealer.verify(declarationRepository.findAllByOrderByIdAsc());
    }

    @Transactional(readOnly = true)
    public List<GroupMembershipDeclaration> declarationsOf(Long userId) {
        return declarationRepository.findAllByUserIdOrderByIdAsc(userId);
    }

    private UserInformation profileOf(Long userId) {
        return userInformationRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Completa tu perfil antes de firmar la declaracion jurada"));
    }

    private static String render(UserInformation profile, String groupName) {
        var document = profile.getIdentityDocument();
        return MembershipDeclarationTemplate.render(
                profile.getFullName(),
                document == null ? null : document.type().name(),
                document == null ? null : document.number(),
                groupName);
    }

    public record Preview(String version, String text) {
    }
}
