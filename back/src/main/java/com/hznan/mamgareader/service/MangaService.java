package com.hznan.mamgareader.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hznan.mamgareader.exception.BusinessException;
import com.hznan.mamgareader.mapper.ChapterMapper;
import com.hznan.mamgareader.model.dto.CreateMangaRequest;
import com.hznan.mamgareader.model.dto.ReadingProgressRequest;
import com.hznan.mamgareader.model.dto.UpdateMangaRequest;
import com.hznan.mamgareader.model.entity.Chapter;
import com.hznan.mamgareader.model.entity.Manga;
import com.hznan.mamgareader.model.entity.MangaPage;
import com.hznan.mamgareader.model.vo.PageResult;
import com.hznan.mamgareader.mapper.MangaMapper;
import com.hznan.mamgareader.mapper.MangaPageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MangaService {

    private final MangaMapper mangaMapper;
    private final MangaPageMapper mangaPageMapper;
    private final ChapterMapper chapterMapper;
    private final FileStorageService fileStorageService;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "bmp"
    );

    public PageResult<Manga> list(Long userId, int page, int size, String keyword, String sort) {
        Page<Manga> mpPage = new Page<>(page, size);

        LambdaQueryWrapper<Manga> wrapper = new LambdaQueryWrapper<Manga>()
                .eq(Manga::getUserId, userId);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(Manga::getTitle, keyword.trim())
                    .or()
                    .like(Manga::getTags, keyword.trim()));
        }

        switch (sort != null ? sort : "createdAt") {
            case "lastReadAt" -> wrapper.orderByDesc(Manga::getLastReadAt);
            case "title" -> wrapper.orderByAsc(Manga::getTitle);
            default -> wrapper.orderByDesc(Manga::getCreatedAt);
        }

        Page<Manga> result = mangaMapper.selectPage(mpPage, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public Manga getById(Long id, Long userId) {
        Manga manga = mangaMapper.selectById(id);
        if (manga == null) throw new BusinessException("漫画不存在");
        if (!manga.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此漫画");
        }
        return manga;
    }

    @Transactional
    public Manga create(Long userId, CreateMangaRequest req) {
        Manga manga = Manga.builder()
                .userId(userId)
                .title(req.title())
                .author(req.author())
                .description(req.description())
                .build();
        mangaMapper.insert(manga);
        return manga;
    }

    @Transactional
    public Manga update(Long id, Long userId, UpdateMangaRequest req) {
        Manga manga = getById(id, userId);

        if (req.title() != null) manga.setTitle(req.title());
        if (req.author() != null) manga.setAuthor(req.author());
        if (req.description() != null) manga.setDescription(req.description());
        if (req.tags() != null) manga.setTags(req.tags());
        if (req.readingDirection() != null) manga.setReadingDirection(req.readingDirection());
        if (req.activeConfigId() != null) manga.setActiveConfigId(req.activeConfigId());

        mangaMapper.updateById(manga);
        return manga;
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Manga manga = getById(id, userId);
        mangaPageMapper.delete(
                new LambdaQueryWrapper<MangaPage>().eq(MangaPage::getMangaId, manga.getId()));
        chapterMapper.delete(
                new LambdaQueryWrapper<Chapter>().eq(Chapter::getMangaId, manga.getId()));
        mangaMapper.deleteById(manga.getId());
        fileStorageService.deleteDirectory(fileStorageService.getMangaDir(userId, id));
    }

    public List<MangaPage> getPages(Long mangaId, Long userId) {
        getById(mangaId, userId);
        return mangaPageMapper.selectList(
                new LambdaQueryWrapper<MangaPage>()
                        .eq(MangaPage::getMangaId, mangaId)
                        .orderByAsc(MangaPage::getChapterId)
                        .orderByAsc(MangaPage::getPageNumber));
    }

    @Transactional
    public List<MangaPage> uploadPages(Long mangaId, Long chapterId, Long userId,
                                        MultipartFile[] files) throws IOException {
        Manga manga = getById(mangaId, userId);
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getMangaId().equals(mangaId)) {
            throw new BusinessException("章节不存在");
        }

        String mangaDir = fileStorageService.getMangaDir(userId, mangaId);

        Long count = mangaPageMapper.selectCount(
                new LambdaQueryWrapper<MangaPage>().eq(MangaPage::getChapterId, chapterId));
        int startPage = count.intValue() + 1;

        List<MultipartFile> sortedFiles = Arrays.stream(files)
                .sorted(Comparator.comparing(MultipartFile::getOriginalFilename, MangaService::naturalCompare))
                .toList();

        List<MangaPage> pages = new ArrayList<>();
        for (int i = 0; i < sortedFiles.size(); i++) {
            MultipartFile file = sortedFiles.get(i);
            int pageNum = startPage + i;
            String ext = getExtension(file.getOriginalFilename());
            String filename = String.format("ch%d_page_%04d.%s", chapter.getChapterNumber(), pageNum, ext);

            String imagePath = fileStorageService.storeFile(mangaDir + "/originals", filename, file.getInputStream());
            String thumbnailPath = fileStorageService.generateThumbnail(
                    imagePath, mangaDir + "/thumbnails", filename);
            int[] dims = fileStorageService.getImageDimensions(imagePath);

            MangaPage page = MangaPage.builder()
                    .mangaId(mangaId)
                    .chapterId(chapterId)
                    .pageNumber(pageNum)
                    .originalFilename(file.getOriginalFilename())
                    .imagePath(imagePath)
                    .thumbnailPath(thumbnailPath)
                    .width(dims != null ? dims[0] : null)
                    .height(dims != null ? dims[1] : null)
                    .fileSize(file.getSize())
                    .build();
            mangaPageMapper.insert(page);
            pages.add(page);
        }

        updateCountsAfterUpload(manga, chapter);
        return pages;
    }

    @Transactional
    public Manga uploadArchive(Long userId, MultipartFile file, String title, String author) throws IOException {
        String archiveName = file.getOriginalFilename();
        String mangaTitle = (title != null && !title.isBlank()) ? title :
                (archiveName != null ? archiveName.replaceAll("\\.(zip|rar|cbz|cbr)$", "") : "未命名漫画");

        Manga manga = Manga.builder()
                .userId(userId)
                .title(mangaTitle)
                .author(author)
                .build();
        mangaMapper.insert(manga);

        String mangaDir = fileStorageService.getMangaDir(userId, manga.getId());

        Map<String, byte[]> imageFiles = extractImagesFromArchive(file.getInputStream(), archiveName);

        Map<String, List<Map.Entry<String, byte[]>>> chaptersMap = groupByChapter(imageFiles);

        List<String> chapterDirs = new ArrayList<>(chaptersMap.keySet());
        chapterDirs.sort(MangaService::naturalCompare);

        int totalPages = 0;
        int chapterNum = 1;
        for (String chapterDir : chapterDirs) {
            String chapterTitle = chapterDir.isEmpty() ? "第" + chapterNum + "话" : chapterDir;

            Chapter chapter = Chapter.builder()
                    .mangaId(manga.getId())
                    .title(chapterTitle)
                    .chapterNumber(chapterNum)
                    .build();
            chapterMapper.insert(chapter);

            List<Map.Entry<String, byte[]>> chapterImages = chaptersMap.get(chapterDir);
            chapterImages.sort(Comparator.comparing(Map.Entry::getKey, MangaService::naturalCompare));

            int pageNum = 1;
            for (Map.Entry<String, byte[]> entry : chapterImages) {
                byte[] data = entry.getValue();
                String ext = getExtension(entry.getKey());
                String filename = String.format("ch%d_page_%04d.%s", chapterNum, pageNum, ext);

                String imagePath = fileStorageService.storeFile(mangaDir + "/originals", filename, data);
                String thumbnailPath = fileStorageService.generateThumbnail(
                        imagePath, mangaDir + "/thumbnails", filename);
                int[] dims = fileStorageService.getImageDimensions(imagePath);

                MangaPage page = MangaPage.builder()
                        .mangaId(manga.getId())
                        .chapterId(chapter.getId())
                        .pageNumber(pageNum)
                        .originalFilename(entry.getKey())
                        .imagePath(imagePath)
                        .thumbnailPath(thumbnailPath)
                        .width(dims != null ? dims[0] : null)
                        .height(dims != null ? dims[1] : null)
                        .fileSize((long) data.length)
                        .build();
                mangaPageMapper.insert(page);
                pageNum++;
                totalPages++;
            }

            chapter.setPageCount(pageNum - 1);
            chapterMapper.updateById(chapter);
            chapterNum++;
        }

        manga.setPageCount(totalPages);
        if (totalPages > 0) {
            manga.setCoverUrl("/api/mangas/" + manga.getId() + "/pages/1/thumbnail");
        }
        mangaMapper.updateById(manga);

        return manga;
    }

    public byte[] getPageImage(Long mangaId, int pageNumber, Long userId) throws IOException {
        getById(mangaId, userId);
        return getPageImagePublic(mangaId, pageNumber);
    }

    public byte[] getPageImagePublic(Long mangaId, int pageNumber) throws IOException {
        MangaPage page = findPageByMangaAndNumber(mangaId, pageNumber);
        byte[] data = fileStorageService.readFile(page.getImagePath());
        if (data == null) throw new BusinessException("图片文件不存在");
        return data;
    }

    public byte[] getPageThumbnail(Long mangaId, int pageNumber, Long userId) throws IOException {
        getById(mangaId, userId);
        return getPageThumbnailPublic(mangaId, pageNumber);
    }

    public byte[] getPageTranslatedImagePublic(Long mangaId, int pageNumber) throws IOException {
        MangaPage page = findPageByMangaAndNumber(mangaId, pageNumber);
        if (page.getTranslatedImagePath() == null) {
            throw new BusinessException("该页面尚未翻译");
        }
        byte[] data = fileStorageService.readFile(page.getTranslatedImagePath());
        if (data == null) throw new BusinessException("翻译图片文件不存在");
        return data;
    }

    public byte[] getPageThumbnailPublic(Long mangaId, int pageNumber) throws IOException {
        MangaPage page = findPageByMangaAndNumber(mangaId, pageNumber);
        if (page.getThumbnailPath() == null) {
            return getPageImagePublic(mangaId, pageNumber);
        }
        byte[] data = fileStorageService.readFile(page.getThumbnailPath());
        if (data == null) return getPageImagePublic(mangaId, pageNumber);
        return data;
    }

    public byte[] getPageImageByIdPublic(Long pageId) throws IOException {
        MangaPage page = mangaPageMapper.selectById(pageId);
        if (page == null) throw new BusinessException("页面不存在");
        byte[] data = fileStorageService.readFile(page.getImagePath());
        if (data == null) throw new BusinessException("图片文件不存在");
        return data;
    }

    public byte[] getPageThumbnailByIdPublic(Long pageId) throws IOException {
        MangaPage page = mangaPageMapper.selectById(pageId);
        if (page == null) throw new BusinessException("页面不存在");
        if (page.getThumbnailPath() == null) return getPageImageByIdPublic(pageId);
        byte[] data = fileStorageService.readFile(page.getThumbnailPath());
        if (data == null) return getPageImageByIdPublic(pageId);
        return data;
    }

    public byte[] getPageTranslatedImageByIdPublic(Long pageId) throws IOException {
        MangaPage page = mangaPageMapper.selectById(pageId);
        if (page == null) throw new BusinessException("页面不存在");
        if (page.getTranslatedImagePath() == null) throw new BusinessException("该页面尚未翻译");
        byte[] data = fileStorageService.readFile(page.getTranslatedImagePath());
        if (data == null) throw new BusinessException("翻译图片文件不存在");
        return data;
    }

    @Transactional
    public Manga setActiveConfig(Long mangaId, Long userId, Long configId) {
        Manga manga = getById(mangaId, userId);
        manga.setActiveConfigId(configId);
        mangaMapper.updateById(manga);
        return manga;
    }

    @Transactional
    public Manga clearActiveConfig(Long mangaId, Long userId) {
        Manga manga = getById(mangaId, userId);
        manga.setActiveConfigId(null);
        mangaMapper.updateById(manga);
        return manga;
    }

    @Transactional
    public void updateReadingProgress(Long mangaId, Long userId, ReadingProgressRequest req) {
        Manga manga = getById(mangaId, userId);
        manga.setLastReadPage(req.page());
        manga.setLastReadAt(LocalDateTime.now());
        mangaMapper.updateById(manga);
    }

    private MangaPage findPageByMangaAndNumber(Long mangaId, int pageNumber) {
        MangaPage page = mangaPageMapper.selectOne(
                new LambdaQueryWrapper<MangaPage>()
                        .eq(MangaPage::getMangaId, mangaId)
                        .eq(MangaPage::getPageNumber, pageNumber));
        if (page == null) throw new BusinessException("页面不存在");
        return page;
    }

    /**
     * Groups archive entries by chapter directory.
     * If entries have subdirectories matching chapter patterns, groups by those.
     * Otherwise, all files go into a single "" group.
     */
    private Map<String, List<Map.Entry<String, byte[]>>> groupByChapter(Map<String, byte[]> imageFiles) {
        Map<String, List<Map.Entry<String, byte[]>>> result = new LinkedHashMap<>();

        boolean hasSubDirs = imageFiles.keySet().stream().anyMatch(k -> k.contains("/"));

        if (!hasSubDirs) {
            result.put("", new ArrayList<>(imageFiles.entrySet().stream()
                    .map(e -> Map.entry(e.getKey(), e.getValue())).toList()));
            return result;
        }

        for (Map.Entry<String, byte[]> entry : imageFiles.entrySet()) {
            String fullPath = entry.getKey();
            String dir;
            String filename;
            int lastSlash = fullPath.lastIndexOf('/');
            if (lastSlash >= 0) {
                dir = fullPath.substring(0, lastSlash);
                int firstSlash = dir.indexOf('/');
                dir = firstSlash >= 0 ? dir.substring(firstSlash + 1) : dir;
                if (dir.isEmpty()) dir = fullPath.substring(0, lastSlash);
                filename = fullPath.substring(lastSlash + 1);
            } else {
                dir = "";
                filename = fullPath;
            }

            result.computeIfAbsent(dir, k -> new ArrayList<>())
                    .add(Map.entry(filename, entry.getValue()));
        }

        return result;
    }

    private void updateCountsAfterUpload(Manga manga, Chapter chapter) {
        Long chapterPageCount = mangaPageMapper.selectCount(
                new LambdaQueryWrapper<MangaPage>().eq(MangaPage::getChapterId, chapter.getId()));
        chapter.setPageCount(chapterPageCount.intValue());
        chapterMapper.updateById(chapter);

        Long totalCount = mangaPageMapper.selectCount(
                new LambdaQueryWrapper<MangaPage>().eq(MangaPage::getMangaId, manga.getId()));
        manga.setPageCount(totalCount.intValue());
        if (manga.getCoverUrl() == null && totalCount > 0) {
            manga.setCoverUrl("/api/mangas/" + manga.getId() + "/pages/1/thumbnail");
        }
        mangaMapper.updateById(manga);
    }

    private Map<String, byte[]> extractImagesFromArchive(InputStream is, String filename) throws IOException {
        Map<String, byte[]> images = new LinkedHashMap<>();
        String ext = getExtension(filename).toLowerCase();

        if ("rar".equals(ext) || "cbr".equals(ext)) {
            extractRar(is, images);
        } else {
            extractZip(is, images);
        }

        return images;
    }

    private void extractZip(InputStream is, Map<String, byte[]> images) throws IOException {
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new BufferedInputStream(is))) {
            ArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String fullName = entry.getName().replace('\\', '/');
                String fileName = Path.of(fullName).getFileName().toString();
                if (isImage(fileName)) {
                    images.put(fullName, zis.readAllBytes());
                }
            }
        }
    }

    private void extractRar(InputStream is, Map<String, byte[]> images) throws IOException {
        Path tempFile = Files.createTempFile("manga_upload_", ".rar");
        try {
            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            com.github.junrar.Archive archive;
            try {
                archive = new com.github.junrar.Archive(tempFile.toFile());
            } catch (com.github.junrar.exception.RarException e) {
                throw new IOException("RAR 文件解析失败", e);
            }
            for (com.github.junrar.rarfile.FileHeader fh : archive.getFileHeaders()) {
                if (fh.isDirectory()) continue;
                String fullName = fh.getFileName().replace('\\', '/');
                String fileName = Path.of(fullName).getFileName().toString();
                if (isImage(fileName)) {
                    try (InputStream eis = archive.getInputStream(fh)) {
                        images.put(fullName, eis.readAllBytes());
                    }
                }
            }
            archive.close();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private boolean isImage(String filename) {
        String ext = getExtension(filename).toLowerCase();
        return IMAGE_EXTENSIONS.contains(ext);
    }

    private String getExtension(String filename) {
        if (filename == null) return "jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "jpg";
    }

    static int naturalCompare(String a, String b) {
        int ia = 0, ib = 0;
        while (ia < a.length() && ib < b.length()) {
            char ca = a.charAt(ia), cb = b.charAt(ib);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int numStartA = ia, numStartB = ib;
                while (ia < a.length() && Character.isDigit(a.charAt(ia))) ia++;
                while (ib < b.length() && Character.isDigit(b.charAt(ib))) ib++;
                String numA = a.substring(numStartA, ia);
                String numB = b.substring(numStartB, ib);
                int cmp = Integer.compare(Integer.parseInt(numA), Integer.parseInt(numB));
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) return cmp;
                ia++;
                ib++;
            }
        }
        return Integer.compare(a.length(), b.length());
    }
}
