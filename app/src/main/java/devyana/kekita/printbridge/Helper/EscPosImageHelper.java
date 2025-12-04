package devyana.kekita.printbridge.Helper;

import android.graphics.Bitmap;
import android.graphics.Color;

public class EscPosImageHelper {

    /**
     * Resize bitmap ke lebar tertentu (misal 160px), tinggi ikut proporsi
     */
    public static Bitmap resizeBitmap(Bitmap bmp, int newWidth) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        float ratio = (float) height / (float) width;
        int newHeight = (int) (newWidth * ratio);
        return Bitmap.createScaledBitmap(bmp, newWidth, newHeight, true);
    }

    /**
     * Convert bitmap ke hitam-putih dengan threshold
     */
    public static Bitmap toMonochrome(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        Bitmap bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bmp.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                int luminance = (r + g + b) / 3;

                if (luminance < 128) {
                    bwBitmap.setPixel(x, y, Color.BLACK);
                } else {
                    bwBitmap.setPixel(x, y, Color.WHITE);
                }
            }
        }
        return bwBitmap;
    }

    /**
     * Encode monochrome bitmap ke ESC/POS raster bit image
     */
    public static byte[] decodeBitmap(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();

        int bytesPerRow = (width + 7) / 8;
        byte[] imageBytes = new byte[8 + (bytesPerRow * height)];

        // GS v 0 = Print raster bit image
        imageBytes[0] = 0x1D;
        imageBytes[1] = 0x76;
        imageBytes[2] = 0x30;
        imageBytes[3] = 0x00;
        imageBytes[4] = (byte) (bytesPerRow & 0xFF);
        imageBytes[5] = (byte) ((bytesPerRow >> 8) & 0xFF);
        imageBytes[6] = (byte) (height & 0xFF);
        imageBytes[7] = (byte) ((height >> 8) & 0xFF);

        int offset = 8;
        for (int y = 0; y < height; y++) {
            int bit = 0;
            byte currentByte = 0;
            for (int x = 0; x < width; x++) {
                int pixel = bmp.getPixel(x, y);
                if (pixel == Color.BLACK) {
                    currentByte |= (1 << (7 - bit));
                }
                bit++;
                if (bit == 8) {
                    imageBytes[offset++] = currentByte;
                    currentByte = 0;
                    bit = 0;
                }
            }
            if (bit != 0) {
                imageBytes[offset++] = currentByte;
            }
        }
        return imageBytes;
    }

}
