package com.hznan.mamgareader.controller;

import com.hznan.mamgareader.model.dto.RegionBox;
import com.hznan.mamgareader.model.vo.ApiResponse;
import com.hznan.mamgareader.model.vo.OcrRegionResult;
import com.hznan.mamgareader.service.ImageEditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/edit")
@RequiredArgsConstructor
public class ImageEditController {

    private final ImageEditService imageEditService;
    private final ObjectMapper objectMapper;

    @GetMapping({"/inpaint", "/inpaint-all", "/ocr-region", "/restore-regions", "/save"})
    public ResponseEntity<Void> rejectGet() {
        return ResponseEntity.status(405).build();
    }

    @PostMapping(value = "/inpaint", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> inpaint(
            @RequestParam("image") MultipartFile image,
            @RequestParam("regions") String regionsJson) throws IOException {
        List<RegionBox> regions = objectMapper.readValue(
                regionsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, RegionBox.class));
        byte[] result = imageEditService.inpaint(image.getBytes(), regions);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(result);
    }

    @PostMapping(value = "/inpaint-all", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> inpaintAll(@RequestParam("image") MultipartFile image) throws IOException {
        byte[] result = imageEditService.inpaintAll(image.getBytes());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(result);
    }

    @PostMapping(value = "/ocr-region", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OcrRegionResult> ocrRegion(
            @RequestParam("image") MultipartFile image,
            @RequestParam("region") String regionJson) throws IOException {
        RegionBox region = objectMapper.readValue(regionJson, RegionBox.class);
        return ApiResponse.ok(imageEditService.ocrRegion(image.getBytes(), region));
    }

    @PostMapping(value = "/restore-regions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> restoreRegions(
            @RequestParam("image") MultipartFile image,
            @RequestParam("pageId") Long pageId,
            @RequestParam("regions") String regionsJson) throws IOException {
        List<RegionBox> regions = objectMapper.readValue(
                regionsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, RegionBox.class));
        byte[] result = imageEditService.restoreRegions(image.getBytes(), pageId, regions);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(result);
    }

    @PostMapping("/save")
    public ApiResponse<Void> save(
            @RequestParam("pageId") Long pageId,
            @RequestParam("image") MultipartFile image) throws IOException {
        imageEditService.saveEditedImage(pageId, image);
        return ApiResponse.ok();
    }
}
