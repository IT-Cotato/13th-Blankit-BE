package com.cotato.blankit.domain.category.controller;

import com.cotato.blankit.domain.category.dto.request.CategoryCreateRequest;
import com.cotato.blankit.domain.category.dto.request.CategoryUpdateRequest;
import com.cotato.blankit.domain.category.dto.response.CategoryResponse;
import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import com.cotato.blankit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@NotImplementedYet
@Tag(name = "카테고리", description = "카테고리 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController("mockCategoryController")
@RequestMapping("/api/v1/categories")
public class CategoryController {

    @Operation(summary = "카테고리 목록 조회",
            description = "로그인한 사용자의 카테고리 목록을 sort_order 오름차순으로 반환합니다. " +
                    "기본 카테고리(학업·일상·기념일)는 회원 가입 시 자동 생성됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(List.of(
                new CategoryResponse(1L, "학업", "#FF5C5C", 0, true),
                new CategoryResponse(2L, "일상", "#5C9EFF", 1, true),
                new CategoryResponse(3L, "기념일", "#5CFF8A", 2, true),
                new CategoryResponse(4L, "자격증 준비", "#FFB85C", 3, false)
        ));
    }

    @Operation(summary = "사용자 정의 카테고리 추가",
            description = "새 카테고리를 추가합니다. 동일 사용자 내 색상 중복은 허용되지 않습니다(애플리케이션 검증). " +
                    "기본 카테고리(학업·일상·기념일)는 이 API가 아닌 회원 가입 시 자동 삽입됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류 또는 색상 중복"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(@RequestBody @Valid CategoryCreateRequest request) {
        return ApiResponse.success(new CategoryResponse(5L, request.name(), request.color(), request.sortOrder(), false));
    }

    @Operation(summary = "카테고리 수정",
            description = "카테고리 이름, 색상, 정렬 순서를 수정합니다. null 필드는 변경하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류 또는 색상 중복"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카테고리 없음")
    })
    @PatchMapping("/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long categoryId,
            @RequestBody @Valid CategoryUpdateRequest request) {
        return ApiResponse.success(new CategoryResponse(categoryId,
                request.name() != null ? request.name() : "학업",
                request.color() != null ? request.color() : "#FF5C5C",
                request.sortOrder() != null ? request.sortOrder() : 0,
                false));
    }

    @Operation(summary = "카테고리 삭제",
            description = "카테고리를 삭제합니다. " +
                    "해당 카테고리에 과업이 하나라도 존재하면 삭제할 수 없습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카테고리 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "과업이 존재하는 카테고리는 삭제 불가")
    })
    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId) {
        return ApiResponse.success();
    }
}
