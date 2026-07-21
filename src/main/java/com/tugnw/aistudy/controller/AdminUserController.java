package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.admin.UserResponse;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin User Management", description = "Endpoints for managing user accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "Get all users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(Pageable pageable) {
        log.debug("[ADMIN_ACTION] Getting all users");
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getAllUsers(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        log.debug("[ADMIN_ACTION] Getting user by ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserById(id)));
    }

    @PatchMapping("/{id}/lock")
    @Operation(summary = "Lock a user account")
    public ResponseEntity<ApiResponse<UserResponse>> lockUser(@PathVariable UUID id) {
        log.debug("[ADMIN_ACTION] Locking user with ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success(adminUserService.lockUser(id)));
    }

    @PatchMapping("/{id}/unlock")
    @Operation(summary = "Unlock a user account")
    public ResponseEntity<ApiResponse<UserResponse>> unlockUser(@PathVariable UUID id) {
        log.debug("[ADMIN_ACTION] Unlocking user with ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success(adminUserService.unlockUser(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a user account")
    public ResponseEntity<ApiResponse<UserResponse>> deleteUser(@PathVariable UUID id) {
        log.debug("[ADMIN_ACTION] Soft deleting user with ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success(adminUserService.softDeleteUser(id)));
    }

    @DeleteMapping("/{id}/hard")
    @Operation(summary = "Permanently delete a user account")
    public ResponseEntity<ApiResponse<Void>> hardDeleteUser(@PathVariable UUID id) {
        log.debug("[ADMIN_ACTION] Hard deleting user with ID: {}", id);
        adminUserService.hardDeleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User permanently deleted", null));
    }

    @GetMapping("/trash")
    @Operation(summary = "Get soft-deleted user accounts for trash view")
    public ResponseEntity<ApiResponse<List<com.tugnw.aistudy.domain.dto.admin.UserResponse>>> getTrashUsers() {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getSoftDeletedAccounts()));
    }

    @PatchMapping("/{id}/restore")
    @Operation(summary = "Restore a soft-deleted user account")
    public ResponseEntity<ApiResponse<UserResponse>> restoreUser(@PathVariable UUID id) {
        log.debug("[ADMIN_ACTION] Restoring user with ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success(adminUserService.restoreUser(id)));
    }

    @PatchMapping("/{id}/toggle-status")
    @Operation(summary = "Toggle user status (lock/unlock)")
    public ResponseEntity<ApiResponse<UserResponse>> toggleStatus(@PathVariable UUID id) {
        log.debug("[ADMIN_ACTION] Toggling status for user with ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success(adminUserService.toggleStatus(id)));
    }
}
