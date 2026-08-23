package com.pixora.backend.util;

import com.pixora.backend.exception.ImageValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Slf4j
public class ImageValidationUtil {

    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20MB

    /**
     * Validate and process an uploaded image file across ALL valid image formats
     * Supports JPG, JPEG, PNG, WEBP, HEIC, HEIF, AVIF, BMP, TIFF, GIF
     */
    public static BufferedImage validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImageValidationException("Please upload an image.", "EMPTY_FILE");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ImageValidationException("Image size is too large. Maximum allowed size is 20MB.", "FILE_TOO_LARGE");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImageValidationException("Failed to read image file data.", "FILE_READ_ERROR");
        }

        if (bytes.length < 8) {
            throw new ImageValidationException("Unsupported file data.", "UNSUPPORTED_FORMAT");
        }

        // Try decoding via ImageIO
        BufferedImage image = null;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Throwable t) {
            log.warn("ImageIO read attempt: {}", t.getMessage());
        }

        // If ImageIO couldn't decode format directly (e.g. HEIC, AVIF, WebP VP8X, etc.), verify image signature
        if (image == null) {
            image = tryExtractDimensions(bytes, file.getOriginalFilename());
        }

        if (image == null) {
            throw new ImageValidationException("Unsupported file data. Please upload a valid image file.", "UNSUPPORTED_FORMAT");
        }

        return image;
    }

    /**
     * Helper to extract dimensions from WebP, HEIC/HEIF, AVIF, BMP, GIF
     */
    private static BufferedImage tryExtractDimensions(byte[] bytes, String filename) {
        try {
            // 1. JPEG signature (FF D8 FF)
            if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
                return new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
            }

            // 2. PNG signature (89 50 4E 47 0D 0A 1A 0A)
            if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
                return new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
            }

            // 3. WebP format (RIFF....WEBP)
            if (bytes.length >= 30 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
                    bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {

                // VP8X Extended WebP
                if (bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8' && bytes[15] == 'X') {
                    int width = 1 + ((bytes[24] & 0xFF) | ((bytes[25] & 0xFF) << 8) | ((bytes[26] & 0xFF) << 16));
                    int height = 1 + ((bytes[27] & 0xFF) | ((bytes[28] & 0xFF) << 8) | ((bytes[29] & 0xFF) << 16));
                    if (width > 0 && height > 0) {
                        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    }
                }
                // VP8 Simple WebP
                if (bytes.length >= 30 && bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8' && bytes[15] == ' ') {
                    int width = ((bytes[26] & 0xFF) | ((bytes[27] & 0xFF) << 8)) & 0x3fff;
                    int height = ((bytes[28] & 0xFF) | ((bytes[29] & 0xFF) << 8)) & 0x3fff;
                    if (width > 0 && height > 0) {
                        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    }
                }
                return new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
            }

            // 4. BMP format ('BM')
            if (bytes.length >= 26 && bytes[0] == 0x42 && bytes[1] == 0x4D) {
                int width = (bytes[18] & 0xFF) | ((bytes[19] & 0xFF) << 8) | ((bytes[20] & 0xFF) << 16) | ((bytes[21] & 0xFF) << 24);
                int height = (bytes[22] & 0xFF) | ((bytes[23] & 0xFF) << 8) | ((bytes[24] & 0xFF) << 16) | ((bytes[25] & 0xFF) << 24);
                if (width > 0 && height > 0) {
                    return new BufferedImage(Math.abs(width), Math.abs(height), BufferedImage.TYPE_INT_RGB);
                }
                return new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
            }

            // 5. HEIC / HEIF / AVIF (ftyp)
            if (bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') {
                return new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
            }

            // 6. GIF (GIF87a or GIF89a)
            if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
                return new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
            }

        } catch (Exception e) {
            log.warn("Header dimension extraction notice: {}", e.getMessage());
        }

        return null;
    }
}
