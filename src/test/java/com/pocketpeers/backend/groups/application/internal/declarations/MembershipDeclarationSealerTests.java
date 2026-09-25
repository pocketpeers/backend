package com.pocketpeers.backend.groups.application.internal.declarations;

import static org.assertj.core.api.Assertions.assertThat;

import com.pocketpeers.backend.groups.domain.model.entities.GroupMembershipDeclaration;
import com.pocketpeers.backend.groups.domain.model.valueobjects.MembershipDeclarationTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class MembershipDeclarationSealerTests {

    private final MembershipDeclarationSealer sealer = new MembershipDeclarationSealer("clave-de-prueba");

    @Test
    void intactChainVerifies() {
        var chain = chainOf(3);

        var result = sealer.verify(chain);

        assertThat(result.intact()).isTrue();
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.firstBrokenId()).isNull();
    }

    @Test
    void editedDocumentNumberIsDetected() {
        var chain = chainOf(3);
        ReflectionTestUtils.setField(chain.get(1), "documentNumber", "99999999");

        var result = sealer.verify(chain);

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenId()).isEqualTo(2L);
    }

    @Test
    void editedTextIsDetectedEvenIfDocumentHashIsRecomputedWithoutTheKey() {
        var chain = chainOf(2);
        var tampered = chain.get(0).getDeclarationText().replace("reales", "ficticios");
        ReflectionTestUtils.setField(chain.get(0), "declarationText", tampered);
        ReflectionTestUtils.setField(chain.get(0), "textHash", sealer.sha256(tampered));

        var result = sealer.verify(chain);

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenId()).isEqualTo(1L);
    }

    @Test
    void replacedPdfIsDetected() {
        var chain = chainOf(2);
        var forged = "otro pdf".getBytes();
        ReflectionTestUtils.setField(chain.get(1), "signedPdf", forged);
        ReflectionTestUtils.setField(chain.get(1), "pdfHash", sealer.sha256(forged));

        var result = sealer.verify(chain);

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenId()).isEqualTo(2L);
    }

    @Test
    void deletedMiddleRowBreaksTheLink() {
        var chain = new ArrayList<>(chainOf(3));
        chain.remove(1);

        var result = sealer.verify(chain);

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenId()).isEqualTo(3L);
    }

    @Test
    void anotherKeyCannotProduceValidSeals() {
        var forger = new MembershipDeclarationSealer("otra-clave");
        var chain = chainOf(1, forger);

        assertThat(sealer.verify(chain).intact()).isFalse();
    }

    private List<GroupMembershipDeclaration> chainOf(int size) {
        return chainOf(size, sealer);
    }

    private static List<GroupMembershipDeclaration> chainOf(int size, MembershipDeclarationSealer signer) {
        var chain = new ArrayList<GroupMembershipDeclaration>();
        var previous = MembershipDeclarationSealer.GENESIS_HASH;
        for (long i = 1; i <= size; i++) {
            var text = MembershipDeclarationTemplate.render("Ana Perez", "DNI", "1234567" + i, "Viaje");
            var textHash = signer.sha256(text);
            var pdf = ("pdf-" + i).getBytes();
            var pdfHash = signer.sha256(pdf);
            var acceptedAt = Instant.ofEpochMilli(1_790_000_000_000L + i);
            var recordHash = signer.recordHash(previous, 10L, "Viaje", i, "Ana Perez", "DNI", "1234567" + i,
                    MembershipDeclarationTemplate.CURRENT_VERSION, textHash, pdfHash, acceptedAt);
            var declaration = new GroupMembershipDeclaration(10L, "Viaje", i, "Ana Perez", "DNI", "1234567" + i,
                    MembershipDeclarationTemplate.CURRENT_VERSION, text, textHash, pdf, pdfHash,
                    acceptedAt, previous, recordHash);
            ReflectionTestUtils.setField(declaration, "id", i);
            chain.add(declaration);
            previous = recordHash;
        }
        return chain;
    }
}
