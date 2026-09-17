package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.entities.ExpenseReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.Receipt;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ReceiptPerceptualHash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    public Optional<Receipt> findById(Long receiptId);

    /**
     * Comprobante que ya usa exactamente esta imagen.
     *
     * <p>Sin filtro por {@code isActive} a proposito, para decir lo mismo que el
     * indice unico de la base. Ese indice no puede filtrarlo —el predicado de un
     * indice parcial solo alcanza columnas de su propia tabla, y la bandera vive
     * en {@code receipts}—, y si la consulta filtrara de mas, un caso que ella
     * deja pasar chocaria despues contra el indice y saldria como error 500 en
     * vez de como conflicto explicado.</p>
     *
     * <p>Hoy no cambia nada: nada pone {@code isActive} en false y el borrado de
     * comprobantes es fisico.</p>
     */
    @Query("select r from ExpenseReceipt r where r.imageSha256 = :sha256")
    List<ExpenseReceipt> findByImageSha256(@Param("sha256") String sha256);

    /**
     * Llave logica del documento. Atrapa la boleta refotografiada, que la imagen
     * no ve.
     *
     * <p>Compara contra {@code documentNumber} y no contra {@code receiptNumber}:
     * el primero es la forma canonica en mayusculas, y es la columna que lleva el
     * indice unico. Consultar la otra dejaria pasar el duplicado cuando el OCR
     * leyo la misma serie con distinta caja.</p>
     */
    @Query("select r from ExpenseReceipt r "
            + "where r.issuerRuc = :issuerRuc and r.documentNumber = :documentNumber")
    List<ExpenseReceipt> findByIssuerRucAndDocumentNumber(@Param("issuerRuc") String issuerRuc,
                                                          @Param("documentNumber") String documentNumber);

    /**
     * Huellas perceptuales de todos los comprobantes.
     *
     * <p>Es un recorrido lineal. A la escala de esta aplicacion cuesta
     * microsegundos, pero no escala sin limite: si el volumen creciera, lo que
     * corresponde es indexar por bandas (LSH) o un arbol BK sobre la distancia
     * de Hamming, no agrandar esta consulta.</p>
     */
    @Query("select new com.pocketpeers.backend.operations.domain.model.valueobjects.ReceiptPerceptualHash("
            + "r.id, r.expense.id, r.imagePerceptualHash) "
            + "from ExpenseReceipt r "
            + "where r.imagePerceptualHash is not null")
    List<ReceiptPerceptualHash> findPerceptualHashes();
}
