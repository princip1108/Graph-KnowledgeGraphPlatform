package com.sdu.kgplatform.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Service
public class FileValidationService {

    public static final long AVATAR_MAX_SIZE = 2L * 1024 * 1024;
    public static final long IMAGE_MAX_SIZE = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    public void validateImage(MultipartFile file, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片文件");
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("图片大小不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("只允许上传 png、jpg、jpeg、webp 图片");
        }

        if ("webp".equals(extension)) {
            validateWebp(file);
        } else {
            validateDecodableImage(file, extension);
        }
    }

    private void validateDecodableImage(MultipartFile file, String extension) {
        try (InputStream inputStream = file.getInputStream();
             ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
            if (imageInputStream == null) {
                throw new IllegalArgumentException("图片文件无法解码");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("图片文件无法解码");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                reader.getWidth(0);
                reader.getHeight(0);
                String formatName = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!matchesExtension(formatName, extension)) {
                    throw new IllegalArgumentException("图片内容与扩展名不匹配");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("图片文件无法解码", e);
        }
    }

    private boolean matchesExtension(String formatName, String extension) {
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return "jpeg".equals(formatName) || "jpg".equals(formatName);
        }
        return extension.equals(formatName);
    }

    private void validateWebp(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("图片文件无法解码", e);
        }

        if (bytes.length < 30 || !asciiEquals(bytes, 0, "RIFF") || !asciiEquals(bytes, 8, "WEBP")) {
            throw new IllegalArgumentException("图片文件无法解码");
        }

        String chunkType = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8 ".equals(chunkType)) {
            validateVp8(bytes);
            return;
        }
        if ("VP8L".equals(chunkType)) {
            validateVp8Lossless(bytes);
            return;
        }
        if ("VP8X".equals(chunkType)) {
            validateVp8Extended(bytes);
            return;
        }
        throw new IllegalArgumentException("图片文件无法解码");
    }

    private void validateVp8(byte[] bytes) {
        if (bytes.length < 30
                || !Arrays.equals(Arrays.copyOfRange(bytes, 23, 26), new byte[]{(byte) 0x9d, 0x01, 0x2a})) {
            throw new IllegalArgumentException("图片文件无法解码");
        }
        int width = readLittleEndian16(bytes, 26) & 0x3fff;
        int height = readLittleEndian16(bytes, 28) & 0x3fff;
        requirePositiveDimensions(width, height);
    }

    private void validateVp8Lossless(byte[] bytes) {
        if (bytes.length < 25 || (bytes[20] & 0xff) != 0x2f) {
            throw new IllegalArgumentException("图片文件无法解码");
        }
        int width = 1 + ((bytes[21] & 0xff) | ((bytes[22] & 0x3f) << 8));
        int height = 1 + (((bytes[22] & 0xff) >>> 6) | ((bytes[23] & 0xff) << 2) | ((bytes[24] & 0x0f) << 10));
        requirePositiveDimensions(width, height);
    }

    private void validateVp8Extended(byte[] bytes) {
        if (bytes.length < 30) {
            throw new IllegalArgumentException("图片文件无法解码");
        }
        int width = 1 + readLittleEndian24(bytes, 24);
        int height = 1 + readLittleEndian24(bytes, 27);
        requirePositiveDimensions(width, height);
    }

    private void requirePositiveDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("图片文件无法解码");
        }
    }

    private boolean asciiEquals(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (bytes[offset + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private int readLittleEndian16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private int readLittleEndian24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8) | ((bytes[offset + 2] & 0xff) << 16);
    }

    private String getExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        String fileName = Paths.get(originalFilename).getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
