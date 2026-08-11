package com.sdu.kgplatform.service.impl;

import com.sdu.kgplatform.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    @Value("${app.upload.base-path:uploads}")
    private String uploadBasePath;

    private Path rootLocation;

    @PostConstruct
    @Override
    public void init() {
        try {
            this.rootLocation = Paths.get(uploadBasePath).toAbsolutePath().normalize();
            if (!Files.exists(rootLocation)) {
                Files.createDirectories(rootLocation);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String subDir) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file.");
            }

            String safeSubDir = normalizeRelativePath(subDir);
            Path destinationDir = this.rootLocation.resolve(safeSubDir).normalize();
            ensureWithinRoot(destinationDir);
            if (!Files.exists(destinationDir)) {
                Files.createDirectories(destinationDir);
            }

            String extension = extractExtension(file.getOriginalFilename());
            String newFilename = UUID.randomUUID().toString() + extension;

            Path destinationFile = destinationDir.resolve(newFilename).normalize();
            ensureWithinRoot(destinationFile);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return "/uploads/" + safeSubDir.replace('\\', '/') + "/" + newFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file.", e);
        }
    }

    @Override
    public Path getUploadPath() {
        return this.rootLocation;
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        // expected url format: /uploads/subDir/filename
        if (fileUrl.startsWith("/uploads/")) {
            String relativePath = fileUrl.substring("/uploads/".length());
            try {
                Path filePath = this.rootLocation.resolve(normalizeRelativePath(relativePath)).normalize();
                ensureWithinRoot(filePath);
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Could not delete uploaded file: {}", fileUrl, e);
            } catch (IllegalArgumentException | SecurityException e) {
                log.warn("Rejected unsafe uploaded file path for deletion: {}", fileUrl, e);
            }
        }
    }

    private String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Upload relative path must not be blank.");
        }

        String normalizedSeparators = relativePath.trim().replace('\\', '/');
        Path normalizedPath = Paths.get(normalizedSeparators).normalize();
        if (normalizedPath.isAbsolute()
                || normalizedPath.startsWith("..")
                || normalizedPath.toString().contains("..")) {
            throw new SecurityException("Unsafe upload relative path: " + relativePath);
        }
        return normalizedPath.toString().replace('\\', '/');
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }

        String fileName = Paths.get(originalFilename).getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }

        String extension = fileName.substring(lastDot);
        return extension.length() <= 20 && extension.matches("\\.[A-Za-z0-9]+") ? extension : "";
    }

    private void ensureWithinRoot(Path path) {
        if (!path.normalize().startsWith(rootLocation)) {
            throw new SecurityException("Resolved upload path escapes root directory.");
        }
    }
}
