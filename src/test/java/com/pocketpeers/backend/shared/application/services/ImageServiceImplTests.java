package com.pocketpeers.backend.shared.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import com.pocketpeers.backend.shared.domain.model.entities.Image;
import com.pocketpeers.backend.shared.infrastructure.persistence.jpa.repositories.ImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ImageServiceImplTests {

    @Mock
    private ImageRepository imageRepository;

    @InjectMocks
    private ImageServiceImpl imageService;

    @Test
    void uploadImageStoresNonImageFileWithoutCompression() throws IOException {
        UUID imageId = UUID.randomUUID();
        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image image = invocation.getArgument(0);
            image.setId(imageId);
            return image;
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "contenido".getBytes()
        );

        UUID returnedId = imageService.uploadImage(file);

        ArgumentCaptor<Image> imageCaptor = ArgumentCaptor.forClass(Image.class);
        verify(imageRepository).save(imageCaptor.capture());
        Image savedImage = imageCaptor.getValue();
        assertThat(returnedId).isEqualTo(imageId);
        assertThat(savedImage.getName()).isEqualTo("document.txt");
        assertThat(savedImage.getContentType()).isEqualTo("text/plain");
        assertThat(savedImage.getData()).isEqualTo("contenido".getBytes());
    }

    @Test
    void getImageByIdReturnsImageOrThrows() {
        UUID imageId = UUID.randomUUID();
        Image image = new Image();
        image.setId(imageId);
        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));

        assertThat(imageService.getImageById(imageId)).isSameAs(image);

        UUID missingId = UUID.randomUUID();
        when(imageRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.getImageById(missingId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Image not found with id:" + missingId);
    }

    @Test
    void deleteImageDeletesLoadedImage() {
        UUID imageId = UUID.randomUUID();
        Image image = new Image();
        image.setId(imageId);
        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));

        imageService.deleteImage(imageId);

        verify(imageRepository).delete(image);
    }
}
