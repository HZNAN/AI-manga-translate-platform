package com.hznan.mamgareader.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznan.mamgareader.exception.BusinessException;
import com.hznan.mamgareader.model.entity.Manga;
import com.hznan.mamgareader.model.entity.MangaPage;
import com.hznan.mamgareader.mapper.MangaMapper;
import com.hznan.mamgareader.mapper.MangaPageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final MangaMapper mangaMapper;
    private final MangaPageMapper mangaPageMapper;
    private final FileStorageService fileStorageService;

    public byte[] exportZip(Long mangaId, Long userId, boolean onlyTranslated) throws IOException {
        Manga manga = mangaMapper.selectById(mangaId);
        if (manga == null) throw new BusinessException("漫画不存在");
        if (!manga.getUserId().equals(userId)) {
            throw new BusinessException("无权导出此漫画");
        }

        List<MangaPage> pages = mangaPageMapper.selectList(
                new LambdaQueryWrapper<MangaPage>()
                        .eq(MangaPage::getMangaId, mangaId)
                        .orderByAsc(MangaPage::getPageNumber));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (MangaPage page : pages) {
                String imagePath = selectImagePath(page, onlyTranslated);
                if (imagePath == null) continue;

                byte[] data = fileStorageService.readFile(imagePath);
                if (data == null) continue;

                String ext = imagePath.substring(imagePath.lastIndexOf('.'));
                String entryName = String.format("%s/page_%04d%s", manga.getTitle(), page.getPageNumber(), ext);

                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(data);
                zos.closeEntry();
            }
        }

        return baos.toByteArray();
    }

    public byte[] exportPdf(Long mangaId, Long userId, boolean onlyTranslated) throws IOException {
        Manga manga = mangaMapper.selectById(mangaId);
        if (manga == null) throw new BusinessException("漫画不存在");
        if (!manga.getUserId().equals(userId)) {
            throw new BusinessException("无权导出此漫画");
        }

        List<MangaPage> pages = mangaPageMapper.selectList(
                new LambdaQueryWrapper<MangaPage>()
                        .eq(MangaPage::getMangaId, mangaId)
                        .orderByAsc(MangaPage::getPageNumber));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            for (MangaPage page : pages) {
                String imagePath = selectImagePath(page, onlyTranslated);
                if (imagePath == null) continue;

                Path absPath = fileStorageService.getAbsolutePath(imagePath);
                if (!absPath.toFile().exists()) continue;

                PDImageXObject image = PDImageXObject.createFromFileByContent(absPath.toFile(), doc);
                float imgWidth = image.getWidth();
                float imgHeight = image.getHeight();

                PDRectangle pageSize = new PDRectangle(imgWidth, imgHeight);
                PDPage pdfPage = new PDPage(pageSize);
                doc.addPage(pdfPage);

                try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
                    cs.drawImage(image, 0, 0, imgWidth, imgHeight);
                }
            }
            doc.save(baos);
        }

        return baos.toByteArray();
    }

    private String selectImagePath(MangaPage page, boolean onlyTranslated) {
        if (Boolean.TRUE.equals(page.getIsTranslated()) && page.getTranslatedImagePath() != null) {
            return page.getTranslatedImagePath();
        }
        if (onlyTranslated) {
            return null;
        }
        return page.getImagePath();
    }
}
