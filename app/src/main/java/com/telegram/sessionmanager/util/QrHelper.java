package com.telegram.sessionmanager.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

public final class QrHelper {

    private QrHelper() {}

    /**
     * Generates a high-contrast, clean QR code bitmap from the given link.
     */
    public static Bitmap generateQrBitmap(String text, int width, int height) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();
            int[] pixels = new int[matrixWidth * matrixHeight];

            // Colors: Pure white modules on dark card background for optimal contrast & aesthetics
            int colorDark = Color.rgb(15, 23, 42);   // Dark slate (#0F172A)
            int colorLight = Color.rgb(255, 255, 255); // Crisp White (#FFFFFF)

            for (int y = 0; y < matrixHeight; y++) {
                int offset = y * matrixWidth;
                for (int x = 0; x < matrixWidth; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? colorDark : colorLight;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extracts or sanitizes the Telegram login link from a scanned QR text.
     */
    public static String sanitizeQrLink(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("tg://login") || trimmed.startsWith("https://t.me/login")) {
            return trimmed;
        }
        return trimmed;
    }
}
