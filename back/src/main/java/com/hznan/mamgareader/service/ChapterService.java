package com.hznan.mamgareader.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznan.mamgareader.exception.BusinessException;
import com.hznan.mamgareader.mapper.ChapterMapper;
import com.hznan.mamgareader.mapper.MangaMapper;
import com.hznan.mamgareader.mapper.MangaPageMapper;
import com.hznan.mamgareader.model.dto.CreateChapterRequest;
import com.hznan.mamgareader.model.dto.UpdateChapterRequest;
import com.hznan.mamgareader.model.entity.Chapter;
import com.hznan.mamgareader.model.entity.Manga;
import com.hznan.mamgareader.model.entity.MangaPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterMapper chapterMapper;
    private final MangaMapper mangaMapper;
    private final MangaPageMapper mangaPageMapper;

    public List<Chapter> listChapters(Long mangaId, Long userId) {
        checkMangaAccess(mangaId, userId);
        return chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getMangaId, mangaId)
                        .orderByAsc(Chapter::getChapterNumber));
    }

    public Chapter getChapter(Long mangaId, Long chapterId, Long userId) {
        checkMangaAccess(mangaId, userId);
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getMangaId().equals(mangaId)) {
            throw new BusinessException("章节不存在");
        }
        return chapter;
    }

    @Transactional
    public Chapter createChapter(Long mangaId, Long userId, CreateChapterRequest req) {
        checkMangaAccess(mangaId, userId);

        Long maxNum = chapterMapper.selectCount(
                new LambdaQueryWrapper<Chapter>().eq(Chapter::getMangaId, mangaId));
        int nextNumber = maxNum.intValue() + 1;

        Chapter chapter = Chapter.builder()
                .mangaId(mangaId)
                .title(req.title())
                .chapterNumber(nextNumber)
                .build();
        chapterMapper.insert(chapter);
        return chapter;
    }

    @Transactional
    public Chapter updateChapter(Long mangaId, Long chapterId, Long userId, UpdateChapterRequest req) {
        Chapter chapter = getChapter(mangaId, chapterId, userId);
        if (req.title() != null) {
            chapter.setTitle(req.title());
        }
        chapterMapper.updateById(chapter);
        return chapter;
    }

    @Transactional
    public void deleteChapter(Long mangaId, Long chapterId, Long userId) {
        getChapter(mangaId, chapterId, userId);

        mangaPageMapper.delete(
                new LambdaQueryWrapper<MangaPage>().eq(MangaPage::getChapterId, chapterId));

        chapterMapper.deleteById(chapterId);

        reindexChapterNumbers(mangaId);
        updateMangaPageCount(mangaId);
    }

    @Transactional
    public void reorderChapters(Long mangaId, Long userId, List<Long> chapterIds) {
        checkMangaAccess(mangaId, userId);

        for (int i = 0; i < chapterIds.size(); i++) {
            Chapter chapter = chapterMapper.selectById(chapterIds.get(i));
            if (chapter == null || !chapter.getMangaId().equals(mangaId)) {
                throw new BusinessException("章节不存在: " + chapterIds.get(i));
            }
            chapter.setChapterNumber(i + 1);
            chapterMapper.updateById(chapter);
        }
    }

    public List<MangaPage> getChapterPages(Long mangaId, Long chapterId, Long userId) {
        getChapter(mangaId, chapterId, userId);
        return mangaPageMapper.selectList(
                new LambdaQueryWrapper<MangaPage>()
                        .eq(MangaPage::getChapterId, chapterId)
                        .orderByAsc(MangaPage::getPageNumber));
    }

    /**
     * One query to get all pages of a manga, grouped by chapterId.
     * Only validates manga access once, then fetches all pages in a single SQL.
     */
    public Map<Long, List<MangaPage>> getAllPagesGrouped(Long mangaId, Long userId) {
        checkMangaAccess(mangaId, userId);
        List<MangaPage> allPages = mangaPageMapper.selectList(
                new LambdaQueryWrapper<MangaPage>()
                        .eq(MangaPage::getMangaId, mangaId)
                        .orderByAsc(MangaPage::getChapterId)
                        .orderByAsc(MangaPage::getPageNumber));
        Map<Long, List<MangaPage>> grouped = new java.util.LinkedHashMap<>();
        for (MangaPage page : allPages) {
            grouped.computeIfAbsent(page.getChapterId(), k -> new java.util.ArrayList<>()).add(page);
        }
        return grouped;
    }

    public void updateChapterPageCount(Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) return;
        Long count = mangaPageMapper.selectCount(
                new LambdaQueryWrapper<MangaPage>().eq(MangaPage::getChapterId, chapterId));
        chapter.setPageCount(count.intValue());
        chapterMapper.updateById(chapter);
    }

    void updateMangaPageCount(Long mangaId) {
        Long count = mangaPageMapper.selectCount(
                new LambdaQueryWrapper<MangaPage>().eq(MangaPage::getMangaId, mangaId));
        Manga manga = mangaMapper.selectById(mangaId);
        if (manga != null) {
            manga.setPageCount(count.intValue());
            mangaMapper.updateById(manga);
        }
    }

    private void reindexChapterNumbers(Long mangaId) {
        List<Chapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getMangaId, mangaId)
                        .orderByAsc(Chapter::getChapterNumber));
        for (int i = 0; i < chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            ch.setChapterNumber(i + 1);
            chapterMapper.updateById(ch);
        }
    }

    private void checkMangaAccess(Long mangaId, Long userId) {
        Manga manga = mangaMapper.selectById(mangaId);
        if (manga == null) throw new BusinessException("漫画不存在");
        if (!manga.getUserId().equals(userId)) throw new BusinessException("无权访问此漫画");
    }
}
