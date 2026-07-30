package com.xianfupu.shop.user;

import com.xianfupu.shop.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class FileUploadController {
    private final Path uploadDir;

    public FileUploadController(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostMapping(value = "/api/uploads/after-sale", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadResult uploadAfterSale(@RequestParam("file") MultipartFile file) {
        return storeImage(file);
    }

    @PostMapping(value = "/api/uploads/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadResult uploadProfile(@RequestParam("file") MultipartFile file) {
        return storeImage(file);
    }

    private UploadResult storeImage(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw BusinessException.badRequest("INVALID_IMAGE", "图片不能为空且不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw BusinessException.badRequest("INVALID_IMAGE", "仅支持图片凭证");
        }
        String extension = extension(file.getOriginalFilename());
        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(uploadDir.resolve(filename));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to store uploaded image", ex);
        }
        return new UploadResult("/api/files/" + filename);
    }

    @GetMapping("/api/files/{filename}")
    ResponseEntity<Resource> file(@PathVariable String filename) {
        if (!filename.matches("[a-zA-Z0-9._-]+")) {
            throw BusinessException.notFound("FILE_NOT_FOUND", "图片不存在");
        }
        try {
            Path target = uploadDir.resolve(filename).normalize();
            if (!target.startsWith(uploadDir) || !Files.isRegularFile(target)) {
                throw BusinessException.notFound("FILE_NOT_FOUND", "图片不存在");
            }
            Resource resource = new UrlResource(target.toUri());
            String contentType = Files.probeContentType(target);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                    .header(HttpHeaders.CONTENT_TYPE,
                            contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                    .body(resource);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.notFound("FILE_NOT_FOUND", "图片不存在");
        }
    }

    private String extension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return ".jpg";
        String value = filename.substring(dot).toLowerCase(Locale.ROOT);
        return value.matches("\\.(jpg|jpeg|png|webp|gif)") ? value : ".jpg";
    }

    public record UploadResult(String url) {
    }
}
