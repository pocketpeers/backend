package com.pocketpeers.backend.groups.domain.model.entities;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Declaracion jurada firmada por un usuario al entrar a un grupo.
 *
 * <p>La firma es un trazo dibujado en la aplicacion, que queda incrustado en el
 * PDF del documento firmado ({@link #signedPdf}).</p>
 *
 * <p>Solo se inserta: no hay setters, la entidad es {@link Immutable} para
 * Hibernate y el script {@code db/2026-09-25-declaraciones-solo-insercion.sql}
 * le pone un trigger que rechaza UPDATE y DELETE en la base.</p>
 *
 * <p>Cada fila guarda el sello de la anterior ({@code previousHash}) y su propio
 * sello ({@code recordHash}), que es un HMAC-SHA256 con una clave que vive en la
 * configuracion del servidor y no en la base. Eso da dos garantias:</p>
 * <ul>
 *   <li>Quien edite una fila directamente en la base no puede recalcular el
 *   sello sin la clave, asi que la verificacion lo delata.</li>
 *   <li>Borrar o intercalar una fila rompe el enlace con la siguiente.</li>
 * </ul>
 *
 * <p>Grupo y usuario se guardan como ids sueltos, sin clave foranea, y con el
 * nombre copiado. Es a proposito: la declaracion es historial y tiene que
 * sobrevivir a que el grupo se elimine o el miembro salga de el.</p>
 */
@Getter
@Entity
@Immutable
@Table(uniqueConstraints = @UniqueConstraint(
        // Cada sello solo puede ser el "anterior" de una fila. Si dos firmas
        // simultaneas leyeran el mismo ultimo eslabon, la segunda choca aqui en
        // vez de dejar la cadena bifurcada.
        name = "ux_group_membership_declarations_previous_hash",
        columnNames = "previous_hash"))
public class GroupMembershipDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long groupId;

    @Column(nullable = false, updatable = false)
    private String groupName;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String fullName;

    @Column(length = 16, updatable = false)
    private String documentType;

    @Column(length = 16, updatable = false)
    private String documentNumber;

    @Column(nullable = false, length = 16, updatable = false)
    private String declarationVersion;

    /** El texto exacto que se le mostro al usuario, con sus datos ya puestos. */
    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String declarationText;

    /** SHA-256 del texto, en hexadecimal. */
    @Column(nullable = false, length = 64, updatable = false)
    private String textHash;

    /**
     * El documento firmado: el texto con la firma dibujada y los datos de quien
     * firma. Es lo que se descarga y lo que se le enseña a un tercero.
     *
     * <p>{@code bytea} y no {@code @Lob}: en PostgreSQL un {@code @Lob} se guarda
     * como objeto grande aparte, que el trigger de solo insercion no protege y
     * que un {@code DELETE} de la fila no borra.</p>
     */
    @Column(nullable = false, columnDefinition = "bytea", updatable = false)
    private byte[] signedPdf;

    /** SHA-256 del PDF, en hexadecimal. */
    @Column(nullable = false, length = 64, updatable = false)
    private String pdfHash;

    /** Truncado a milisegundos antes de sellar: es lo que la base conserva sin redondear. */
    @Column(nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(nullable = false, length = 64, updatable = false)
    private String previousHash;

    @Column(nullable = false, length = 64, updatable = false, unique = true)
    private String recordHash;

    protected GroupMembershipDeclaration() {
    }

    public GroupMembershipDeclaration(Long groupId, String groupName, Long userId, String fullName,
                                      String documentType, String documentNumber,
                                      String declarationVersion, String declarationText, String textHash,
                                      byte[] signedPdf, String pdfHash,
                                      Instant acceptedAt, String previousHash, String recordHash) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.userId = userId;
        this.fullName = fullName;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.declarationVersion = declarationVersion;
        this.declarationText = declarationText;
        this.textHash = textHash;
        this.signedPdf = signedPdf;
        this.pdfHash = pdfHash;
        this.acceptedAt = acceptedAt;
        this.previousHash = previousHash;
        this.recordHash = recordHash;
    }
}
