package com.pocketpeers.backend.groups.application.internal.declarations;

import com.pocketpeers.backend.groups.domain.model.entities.GroupMembershipDeclaration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Calcula y comprueba los sellos de las declaraciones.
 *
 * <p>Por que HMAC y no un SHA-256 simple: un hash sin clave lo puede recalcular
 * cualquiera. Quien tuviera acceso a la base podria cambiar el DNI de una fila,
 * recalcular el hash y dejarla "valida". Con HMAC hace falta ademas la clave,
 * que esta en la configuracion del servidor y no en la base.</p>
 *
 * <p>La linea sellada es fija y cada campo va en su propia posicion. Cambiar el
 * orden o el formato de un campo invalida todas las firmas anteriores, asi que
 * si algun dia hace falta, se sube {@link #FORMAT} y se conserva el formato
 * viejo para verificar las filas viejas.</p>
 */
public class MembershipDeclarationSealer {

    public static final String GENESIS_HASH = "0".repeat(64);

    private static final String FORMAT = "pp-decl-v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public MembershipDeclarationSealer(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("La clave para sellar declaraciones no esta configurada");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    public String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public String recordHash(String previousHash, Long groupId, String groupName, Long userId, String fullName,
                             String documentType, String documentNumber, String declarationVersion,
                             String textHash, String pdfHash, Instant acceptedAt) {
        var line = String.join("\n",
                FORMAT,
                previousHash,
                String.valueOf(groupId),
                groupName,
                String.valueOf(userId),
                fullName,
                Objects.toString(documentType, ""),
                Objects.toString(documentNumber, ""),
                declarationVersion,
                textHash,
                pdfHash,
                String.valueOf(acceptedAt.toEpochMilli()));
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(line.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo sellar la declaracion", e);
        }
    }

    /**
     * Recorre la cadena en orden y devuelve la primera fila que no cuadra.
     *
     * <p>Lo que detecta: una fila editada (su sello ya no coincide), un texto o
     * un PDF cambiados (su hash ya no coincide, y el sello tampoco) y una fila borrada o
     * intercalada en medio (el enlace con la anterior se rompe). Lo que no
     * detecta por si sola: que se borren las ultimas filas, porque la cadena que
     * queda sigue siendo coherente. Para eso sirve el trigger de solo insercion.</p>
     */
    public ChainVerification verify(List<GroupMembershipDeclaration> chainInOrder) {
        var expectedPrevious = GENESIS_HASH;
        for (var declaration : chainInOrder) {
            var intact = declaration.getPreviousHash().equals(expectedPrevious)
                    && declaration.getTextHash().equals(sha256(declaration.getDeclarationText()))
                    && declaration.getPdfHash().equals(sha256(declaration.getSignedPdf()))
                    && declaration.getRecordHash().equals(recordHash(
                            declaration.getPreviousHash(),
                            declaration.getGroupId(),
                            declaration.getGroupName(),
                            declaration.getUserId(),
                            declaration.getFullName(),
                            declaration.getDocumentType(),
                            declaration.getDocumentNumber(),
                            declaration.getDeclarationVersion(),
                            declaration.getTextHash(),
                            declaration.getPdfHash(),
                            declaration.getAcceptedAt()));
            if (!intact) {
                return new ChainVerification(chainInOrder.size(), false, declaration.getId());
            }
            expectedPrevious = declaration.getRecordHash();
        }
        return new ChainVerification(chainInOrder.size(), true, null);
    }

    public record ChainVerification(int total, boolean intact, Long firstBrokenId) {
    }
}
