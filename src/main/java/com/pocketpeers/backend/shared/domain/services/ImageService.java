package com.pocketpeers.backend.shared.domain.services;

import com.pocketpeers.backend.shared.domain.model.entities.Image;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface ImageService {
    public UUID uploadImage(MultipartFile imageFile) throws IOException;
    public Image getImageById(UUID imageId);
    public void deleteImage(UUID imageId);
}
