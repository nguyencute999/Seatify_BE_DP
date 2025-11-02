package com.seatify.controller.admin;

import com.seatify.dto.admin.request.AdminNewsRequestDTO;
import com.seatify.dto.admin.response.AdminNewsResponseDTO;
import com.seatify.service.admin.AdminNewsService;
import com.seatify.util.FileUploadUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 
 */
@RestController
@RequestMapping("/api/v1/admin/news")
@RequiredArgsConstructor
public class AdminNewsController {

    private final AdminNewsService newsService;
    private final FileUploadUtil fileUploadUtil;

   /**
    * 
    * @param dto
    * @return
    */
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminNewsResponseDTO> create(@Valid @RequestBody AdminNewsRequestDTO dto) {
        return ResponseEntity.ok(newsService.create(dto));
    }

 /**
  * 
  * @param id
  * @param dto
  * @return
  */
    @PutMapping(value = "/update/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminNewsResponseDTO> update(@PathVariable Long id, @Valid @RequestBody AdminNewsRequestDTO dto) {
        return ResponseEntity.ok(newsService.update(id, dto));
    }

    /**
     * 
     * @param title
     * @param page
     * @param size
     * @param sortBy
     * @param desc
     * @return
     */
    @GetMapping
    public ResponseEntity<Page<AdminNewsResponseDTO>> getAll(
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "newsId") String sortBy,
            @RequestParam(defaultValue = "false") boolean desc
    ) {
        return ResponseEntity.ok(newsService.getAll(title, page, size, sortBy, desc));
    }

    /**
     * Lấy tất cả tin tức không phân trang
     */
    @GetMapping("/all")
    public ResponseEntity<List<AdminNewsResponseDTO>> getAllNews() {
        return ResponseEntity.ok(newsService.getAllNews());
    }

    /**
     * Upload ảnh thumbnail cho tin tức
     *
     * @param file File ảnh cần upload
     * @return URL của ảnh sau khi upload thành công hoặc thông báo lỗi
     */
    @PostMapping(value = "/upload-thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadThumbnail(@RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = fileUploadUtil.uploadFile(file, "news-thumbnails");
            return ResponseEntity.ok(imageUrl);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to upload image: " + e.getMessage());
        }
    }

    /**
     * Publish tin tức - đưa tin tức lên trang tin tức public
     * Sau khi publish, tin tức sẽ hiển thị ở GET /api/v1/news
     */
    @PutMapping("/{id}/publish")
    public ResponseEntity<AdminNewsResponseDTO> publish(@PathVariable Long id) {
        return ResponseEntity.ok(newsService.publish(id));
    }

    /**
     * Unpublish tin tức - ẩn tin tức khỏi trang tin tức public
     * Sau khi unpublish, tin tức sẽ không hiển thị ở GET /api/v1/news nữa
     */
    @PutMapping("/{id}/unpublish")
    public ResponseEntity<AdminNewsResponseDTO> unpublish(@PathVariable Long id) {
        return ResponseEntity.ok(newsService.unpublish(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminNewsResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(newsService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        newsService.delete(id);
        return ResponseEntity.ok("Xóa tin tức thành công.");
    }
}


