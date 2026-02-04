package com.sdu.kgplatform.service.impl;

import com.sdu.kgplatform.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${app.upload.base-path:uploads}")
    private String uploadBasePath;

    private Path rootLocation;

    @PostConstruct
    @Override
    public void init() {
        try {
            this.rootLocation = Paths.get(uploadBasePath);
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

            Path destinationDir = this.rootLocation.resolve(subDir);
            if (!Files.exists(destinationDir)) {
                Files.createDirectories(destinationDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + extension;

            Path destinationFile = destinationDir.resolve(newFilename);
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + subDir + "/" + newFilename;
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
                Path filePath = this.rootLocation.resolve(relativePath);
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                // Log error but don't throw exception to avoid breaking the main flow
                System.err.println("Could not delete file: " + fileUrl + ". Error: " + e.getMessage());
            }
        }
    }
}
