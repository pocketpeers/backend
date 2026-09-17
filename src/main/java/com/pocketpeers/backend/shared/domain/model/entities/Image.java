package com.pocketpeers.backend.shared.domain.model.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Setter
@Getter
public class Image {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private String contentType;

    /**
     * Huella de los bytes almacenados, calculada al subir.
     *
     * <p>Vive aqui y no en el comprobante porque es una propiedad de la imagen,
     * y porque calcularla al subir aprovecha que {@code ImageServiceImpl} ya
     * decodifico el archivo: obtenerla despues costaria una segunda lectura y
     * una segunda decodificacion.</p>
     *
     * <p><b>No lleva restriccion de unicidad.</b> Estas columnas las comparten
     * fotos de grupo, de perfil y evidencias de pago, donde repetir una imagen
     * es legitimo. La unicidad se impone sobre {@code ExpenseReceipt}, que es
     * donde repetir una imagen significa cobrar dos veces el mismo gasto.</p>
     */
    @Column(length = 64)
    private String sha256;

    private Long perceptualHash;

    @Lob
    private byte[] data;
}
