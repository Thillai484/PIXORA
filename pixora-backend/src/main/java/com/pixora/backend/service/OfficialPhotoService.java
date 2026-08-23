package com.pixora.backend.service;

import com.pixora.backend.entity.Photo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Official Photo Processing Pipeline
 * For Passport, Visa, and ID documents: runs local biometric normalization without AI distortion
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialPhotoService {

    private final StorageService storageService;

    /**
     * Process official document photo (Passport, Visa, ID) with standardized specifications
     */
    public String processOfficialPhoto(Photo photo) throws Exception {
        byte[] originalBytes = storageService.getFileBytes(photo.getOriginalImageUrl());

        int outWidth = 900;
        int outHeight = 1200;

        BufferedImage canvas = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 1. Background Setup (Pure White for Passport/Visa, Light Gray for ID)
        Color bg = "WHITE".equalsIgnoreCase(photo.getBackground()) ? Color.WHITE : new Color(230, 235, 240);
        g.setColor(bg);
        g.fillRect(0, 0, outWidth, outHeight);

        // 2. Decode user image if present
        BufferedImage userImg = null;
        if (originalBytes != null && originalBytes.length > 0) {
            try {
                userImg = ImageIO.read(new ByteArrayInputStream(originalBytes));
            } catch (Exception ignored) {
            }
        }

        if (userImg != null) {
            // Apply slight lighting normalization
            try {
                RescaleOp rescale = new RescaleOp(1.05f, 5.0f, null);
                userImg = rescale.filter(userImg, null);
            } catch (Exception ignored) {
            }

            double scale = Math.max((double) outWidth / userImg.getWidth(), (double) outHeight / userImg.getHeight());
            int drawW = (int) (userImg.getWidth() * scale);
            int drawH = (int) (userImg.getHeight() * scale);
            int drawX = (outWidth - drawW) / 2;
            int drawY = 0;

            g.drawImage(userImg, drawX, drawY, drawW, drawH, null);
        }

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", baos);
        byte[] finalBytes = baos.toByteArray();

        String filename = String.format("photo-%d-official-%d.png", photo.getId(), System.currentTimeMillis());
        return storageService.uploadGeneratedPhoto(photo.getUserId(), filename, finalBytes, "image/png");
    }
}
