package com.hznan.mamgareader.service;

import com.hznan.mamgareader.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final AppProperties appProperties;
    private Path basePath;

    @PostConstruct
    public void init() {
        basePath = Path.of(appProperties.getStorage().getBasePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new RuntimeException("无法创建存储目录: " + basePath, e);
        }
    }

    public String getMangaDir(Long userId, Long mangaId) {
        return userId + "/" + mangaId;
    }

    public String storeFile(String relativeDirPath, String filename, InputStream inputStream) throws IOException {
        Path dir = basePath.resolve(relativeDirPath);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return relativeDirPath + "/" + filename;
    }

    public String storeFile(String relativeDirPath, String filename, byte[] data) throws IOException {
        Path dir = basePath.resolve(relativeDirPath);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);
        Files.write(target, data);
        return relativeDirPath + "/" + filename;
    }

    public byte[] readFile(String relativePath) throws IOException {
        Path file = basePath.resolve(relativePath);
        if (!Files.exists(file)) {
            return null;
        }
        return Files.readAllBytes(file);
    }

    public Path getAbsolutePath(String relativePath) {
        return basePath.resolve(relativePath);
    }

    public void deleteDirectory(String relativeDirPath) {
        Path dir = basePath.resolve(relativeDirPath);
        if (!Files.exists(dir)) return;
        try (var walker = Files.walk(dir)) {
            walker.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("删除文件失败: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("清理目录失败: {}", dir, e);
        }
    }

    /**
     * 生成缩略图并返回相对路径
     */
    public String generateThumbnail(String relativeImagePath, String thumbnailDir, String filename) throws IOException {
        Path source = basePath.resolve(relativeImagePath);
        if (!Files.exists(source)) return null;

        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) return null;

        int thumbWidth = 300;
        int thumbHeight = (int) ((double) original.getHeight() / original.getWidth() * thumbWidth);

        BufferedImage thumbnail = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumbnail.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, thumbWidth, thumbHeight, null);
        g.dispose();

        Path dir = basePath.resolve(thumbnailDir);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);

        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        String format = switch (ext) {
            case "png" -> "png";
            case "webp" -> "webp";
            default -> "jpeg";
        };
        ImageIO.write(thumbnail, format, target.toFile());

        return thumbnailDir + "/" + filename;
    }

    /**
     * 读取图片并返回尺寸 [width, height]
     */
    public int[] getImageDimensions(String relativePath) throws IOException {
        Path file = basePath.resolve(relativePath);
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) return null;
        return new int[]{img.getWidth(), img.getHeight()};
    }

    public byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}
