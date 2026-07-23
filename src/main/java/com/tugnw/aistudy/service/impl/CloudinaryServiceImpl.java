package com.tugnw.aistudy.service.impl;

import com.cloudinary.Cloudinary;
import com.tugnw.aistudy.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    @Override
    public Map<String, Object> upload(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();

            if (originalFilename == null)
                originalFilename = "file";

            Map<String, Object> options = Map.of(
                    "resource_type", "auto",
                    "use_filename", true,
                    "unique_filename", false,
                    "filename_override", originalFilename
            );
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    options
            );
            return uploadResult;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to Cloudinary", e);
        }
    }

    @Override
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, Map.of());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from Cloudinary", e);
        }
    }
}