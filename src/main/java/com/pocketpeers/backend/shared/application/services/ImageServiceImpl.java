package com.pocketpeers.backend.shared.application.services;

import com.pocketpeers.backend.shared.domain.model.entities.Image;
import com.pocketpeers.backend.shared.domain.services.ImageService;
import com.pocketpeers.backend.shared.infrastructure.persistence.jpa.repositories.ImageRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;

    @Override
    public UUID uploadImage(MultipartFile imageFile) throws IOException {
        Image image = new Image();
        image.setName(imageFile.getOriginalFilename());
        image.setData(imageFile.getBytes());
        image.setContentType(imageFile.getContentType());

        imageRepository.save(image);
        return image.getId();
    }

    @Override
    public Image getImageById(UUID imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(()-> new RuntimeException("Image not found with id:"+imageId));
        return image;
    }

    @Override
    public void deleteImage(UUID imageId) {
        Image image = this.getImageById(imageId);
        imageRepository.delete(image);
    }
}
