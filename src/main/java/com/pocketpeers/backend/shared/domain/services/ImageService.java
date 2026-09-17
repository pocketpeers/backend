package com.pocketpeers.backend.shared.domain.services;

import com.pocketpeers.backend.shared.domain.model.entities.Image;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public interface ImageService {
    public UUID uploadImage(MultipartFile imageFile) throws IOException;
    public Image getImageById(UUID imageId);

    /**
     * Busqueda blanda, para quien puede seguir sin la imagen.
     *
     * <p>{@link #getImageById} lanza excepcion, y el control de duplicados no
     * puede usar eso: un comprobante cuya imagen ya no esta debe poder
     * registrarse igual, solo que sin las senales derivadas de la imagen.</p>
     */
    public Optional<Image> findImageById(UUID imageId);

    public void deleteImage(UUID imageId);
}
