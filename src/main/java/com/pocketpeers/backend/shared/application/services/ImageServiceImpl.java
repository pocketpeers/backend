package com.pocketpeers.backend.shared.application.services;

import com.pocketpeers.backend.shared.domain.model.entities.Image;
import com.pocketpeers.backend.shared.domain.services.ImageService;
import com.pocketpeers.backend.shared.infrastructure.persistence.jpa.repositories.ImageRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ImageServiceImpl implements ImageService {

    private static final int MAX_IMAGE_SIDE = 1600;
    private static final float JPEG_QUALITY = 0.75f;
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    private final ImageRepository imageRepository;

    @Override
    public UUID uploadImage(MultipartFile imageFile) throws IOException {
        var processedImage = compressImage(imageFile);

        Image image = new Image();
        image.setName(processedImage.name());
        image.setData(processedImage.data());
        image.setContentType(processedImage.contentType());

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

    private ProcessedImage compressImage(MultipartFile imageFile) throws IOException {
        byte[] originalData = imageFile.getBytes();
        String originalContentType = imageFile.getContentType();

        if (originalContentType == null || !originalContentType.startsWith("image/")) {
            return new ProcessedImage(
                    originalData,
                    originalContentType,
                    imageFile.getOriginalFilename()
            );
        }

        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalData));
        if (originalImage == null) {
            return new ProcessedImage(
                    originalData,
                    originalContentType,
                    imageFile.getOriginalFilename()
            );
        }

        BufferedImage resizedImage = resizeIfNeeded(originalImage);
        BufferedImage jpegImage = toJpegCompatibleImage(resizedImage);
        byte[] compressedData = writeJpeg(jpegImage);

        if (compressedData.length >= originalData.length) {
            return new ProcessedImage(
                    originalData,
                    originalContentType,
                    imageFile.getOriginalFilename()
            );
        }

        return new ProcessedImage(
                compressedData,
                JPEG_CONTENT_TYPE,
                withJpegExtension(imageFile.getOriginalFilename())
        );
    }

    private BufferedImage resizeIfNeeded(BufferedImage originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        int largestSide = Math.max(width, height);

        if (largestSide <= MAX_IMAGE_SIDE) {
            return originalImage;
        }

        double scale = (double) MAX_IMAGE_SIDE / largestSide;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        return resizedImage;
    }

    private BufferedImage toJpegCompatibleImage(BufferedImage sourceImage) {
        BufferedImage jpegImage = new BufferedImage(
                sourceImage.getWidth(),
                sourceImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = jpegImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, jpegImage.getWidth(), jpegImage.getHeight());
            graphics.drawImage(sourceImage, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return jpegImage;
    }

    private byte[] writeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG image writer available");
        }

        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }

        return output.toByteArray();
    }

    private String withJpegExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "image.jpg";
        }

        int extensionIndex = originalFilename.lastIndexOf('.');
        if (extensionIndex <= 0) {
            return originalFilename + ".jpg";
        }

        return originalFilename.substring(0, extensionIndex) + ".jpg";
    }

    private record ProcessedImage(byte[] data, String contentType, String name) {
    }
}
