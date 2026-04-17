package com.hznan.mamgareader.service;

import com.hznan.mamgareader.exception.BusinessException;
import com.hznan.mamgareader.model.dto.RegionBox;
import com.hznan.mamgareader.model.entity.Manga;
import com.hznan.mamgareader.model.entity.MangaPage;
import com.hznan.mamgareader.model.entity.TranslationRecord;
import com.hznan.mamgareader.model.vo.OcrRegionResult;
import com.hznan.mamgareader.mapper.MangaPageMapper;
import com.hznan.mamgareader.mapper.MangaMapper;
import com.hznan.mamgareader.mapper.TranslationRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageEditService {

    private final MangaPageMapper mangaPageMapper;
    private final MangaMapper mangaMapper;
    private final TranslationRecordMapper recordMapper;
    private final FileStorageService fileStorageService;
    private final TranslatorApiClient translatorApiClient;

    public byte[] inpaint(byte[] imageData, List<RegionBox> regions) throws IOException {
        byte[] fullInpainted = translatorApiClient.inpaintImage(imageData, "full_inpaint.png", null);
        if (fullInpainted == null) throw new BusinessException("抠字处理失败");

        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageData));
        BufferedImage inpainted = ImageIO.read(new ByteArrayInputStream(fullInpainted));
        if (original == null || inpainted == null) throw new BusinessException("无法读取图片");

        var g2d = original.createGraphics();

        for (RegionBox r : regions) {
            int x = Math.max(0, r.x());
            int y = Math.max(0, r.y());
            int w = Math.min(r.width(), original.getWidth() - x);
            int h = Math.min(r.height(), original.getHeight() - y);
            if (w <= 0 || h <= 0) continue;

            BufferedImage patch = inpainted.getSubimage(x, y, w, h);
            g2d.drawImage(patch, x, y, null);
        }

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(original, "png", baos);
        return baos.toByteArray();
    }

    public byte[] inpaintAll(byte[] imageData) throws IOException {
        byte[] result = translatorApiClient.inpaintImage(imageData, "full_inpaint.png", null);
        if (result == null) throw new BusinessException("全局抠字失败");
        return result;
    }

    public byte[] restoreRegions(byte[] currentImageData, Long pageId, List<RegionBox> regions) throws IOException {
        MangaPage page = mangaPageMapper.selectById(pageId);
        if (page == null) throw new BusinessException("页面不存在");

        byte[] originalData = fileStorageService.readFile(page.getImagePath());
        if (originalData == null) throw new BusinessException("原图文件不存在");

        BufferedImage current = ImageIO.read(new ByteArrayInputStream(currentImageData));
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalData));
        if (current == null || original == null) throw new BusinessException("无法读取图片");

        var g2d = current.createGraphics();

        for (RegionBox r : regions) {
            int x = Math.max(0, r.x());
            int y = Math.max(0, r.y());
            int w = Math.min(r.width(), Math.min(current.getWidth(), original.getWidth()) - x);
            int h = Math.min(r.height(), Math.min(current.getHeight(), original.getHeight()) - y);
            if (w <= 0 || h <= 0) continue;

            BufferedImage patch = original.getSubimage(x, y, w, h);
            g2d.drawImage(patch, x, y, null);
        }

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(current, "png", baos);
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    public OcrRegionResult ocrRegion(byte[] imageData, RegionBox region) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        if (image == null) throw new BusinessException("无法读取图片");

        int x = Math.max(0, region.x());
        int y = Math.max(0, region.y());
        int w = Math.min(region.width(), image.getWidth() - x);
        int h = Math.min(region.height(), image.getHeight() - y);
        BufferedImage cropped = image.getSubimage(x, y, w, h);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(cropped, "png", baos);
        byte[] croppedData = baos.toByteArray();

        Map<String, Object> jsonResult = translatorApiClient.ocrImage(croppedData, "crop.png", null);

        StringBuilder textBuilder = new StringBuilder();
        double totalProb = 0;
        int count = 0;

        List<Map<String, Object>> translations = (List<Map<String, Object>>) jsonResult.get("translations");
        if (translations != null) {
            for (Map<String, Object> t : translations) {
                Map<String, String> textMap = (Map<String, String>) t.get("text");
                if (textMap != null) {
                    String firstLang = textMap.values().stream().findFirst().orElse("");
                    if (!firstLang.isEmpty()) {
                        if (!textBuilder.isEmpty()) textBuilder.append("\n");
                        textBuilder.append(firstLang);
                    }
                }
                Object prob = t.get("prob");
                if (prob instanceof Number) {
                    totalProb += ((Number) prob).doubleValue();
                    count++;
                }
            }
        }

        double confidence = count > 0 ? totalProb / count : 0;
        return new OcrRegionResult(textBuilder.toString(), confidence);
    }

    @Transactional
    public void saveEditedImage(Long pageId, MultipartFile imageFile) throws IOException {
        MangaPage page = mangaPageMapper.selectById(pageId);
        if (page == null) throw new BusinessException("页面不存在");

        Long mangaId = page.getMangaId();
        Manga manga = mangaMapper.selectById(mangaId);
        if (manga == null) throw new BusinessException("漫画不存在");

        String mangaDir = fileStorageService.getMangaDir(manga.getUserId(), mangaId);
        String filename = "edited_page_" + page.getPageNumber() + "_" + System.currentTimeMillis() + ".png";
        String savedPath = fileStorageService.storeFile(mangaDir + "/translations", filename, imageFile.getInputStream());

        TranslationRecord record = TranslationRecord.builder()
                .userId(manga.getUserId())
                .mangaId(mangaId)
                .pageId(pageId)
                .chapterId(page.getChapterId())
                .pageNumber(page.getPageNumber())
                .status("manual_corrected")
                .translatedImagePath(savedPath)
                .completedAt(LocalDateTime.now())
                .build();
        recordMapper.insert(record);

        page.setIsTranslated(true);
        page.setTranslatedImagePath(savedPath);
        mangaPageMapper.updateById(page);
    }

}
