package com.pixora.backend.util;

import com.pixora.backend.exception.ImageValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

@Slf4j
public class ImageValidationUtil {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    // Magic bytes signatures
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF_MAGIC = new byte[]{0x47, 0x49, 0x46, 0x38}; // "GIF8"
    private static final byte[] RIFF_MAGIC = new byte[]{0x52, 0x49, 0x46, 0x46}; // "RIFF" for WEBP

    /**
     * Validate an uploaded image file completely (presence, size, magic signature, decodability)
     * Returns decoded BufferedImage if valid
     */
    public static BufferedImage validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImageValidationException("Please upload an image.", "EMPTY_FILE");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ImageValidationException("Image size is too large. Maximum allowed size is 10MB.", "FILE_TOO_LARGE");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImageValidationException("Failed to read image file data.", "FILE_READ_ERROR");
        }

        if (bytes.length < 12) {
            throw new ImageValidationException("Unsupported file format.", "UNSUPPORTED_FORMAT");
        }

        // Verify magic bytes signature
        boolean isJpeg = matchesSignature(bytes, JPEG_MAGIC);
        boolean isPng = matchesSignature(bytes, PNG_MAGIC);
        boolean isWebp = matchesSignature(bytes, RIFF_MAGIC) && bytes.length >= 12 &&
                bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';

        if (!isJpeg && !isPng && !isWebp) {
            throw new ImageValidationException("Unsupported file format. Please upload a JPG or PNG image.", "UNSUPPORTED_FORMAT");
        }

        // Verify raster decodability via ImageIO
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.warn("ImageIO failed to decode image: {}", e.getMessage());
            throw new ImageValidationException("Invalid or corrupted image file.", "INVALID_IMAGE");
        }

        if (image == null) {
            throw new ImageValidationException("Invalid or unreadable image data.", "INVALID_IMAGE");
        }

        if (image.getWidth() < 50 || image.getHeight() < 50) {
            throw new ImageValidationException("Image resolution is too low. Please upload a photo with at least 50x50 pixels.", "RESOLUTION_TOO_LOW");
        }

        return image;
    }

    private static boolean matchesSignature(byte[] data, byte[] signature) {
        if (data.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
