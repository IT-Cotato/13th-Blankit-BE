package com.cotato.blankit.domain.task.controller;

import com.cotato.blankit.domain.task.dto.request.CategoryCreateRequest;
import com.cotato.blankit.domain.task.dto.request.CategoryUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.CategoryResponse;
import com.cotato.blankit.domain.task.entity.CategoryColor;
import com.cotato.blankit.domain.task.service.CategoryService;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Category", description = "과업 카테고리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 목록 조회", description = "현재 로그인한 회원의 활성 카테고리를 생성순으로 조회합니다.")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(categoryService.getCategories(userDetails.getUserId()));
    }

    @Operation(summary = "카테고리 생성", description = "같은 이름은 허용하지만 같은 회원의 활성 카테고리 색상 중복은 허용하지 않습니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CategoryCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(categoryService.createCategory(userDetails.getUserId(), request)));
    }

    @Operation(summary = "카테고리 수정", description = "수정 대상 자신의 현재 색상은 유지할 수 있습니다.")
    @PatchMapping("/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpdateRequest request
    ) {
        return ApiResponse.success(categoryService.updateCategory(userDetails.getUserId(), categoryId, request));
    }

    @Operation(summary = "카테고리 삭제", description = "soft delete 처리하며 기존 과업은 삭제하지 않습니다.")
    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> deleteCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long categoryId
    ) {
        categoryService.deleteCategory(userDetails.getUserId(), categoryId);
        return ApiResponse.success();
    }

    @Operation(summary = "사용 가능한 카테고리 색상 조회", description = "수정 화면에서는 editingCategoryId의 현재 색상을 포함합니다.")
    @GetMapping("/available-colors")
    public ApiResponse<List<CategoryColor>> getAvailableColors(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "수정 중인 카테고리 ID", example = "1")
            @RequestParam(required = false) Long editingCategoryId
    ) {
        return ApiResponse.success(categoryService.getAvailableColors(userDetails.getUserId(), editingCategoryId));
    }
}
