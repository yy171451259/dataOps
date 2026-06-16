package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.dto.AssignOwnerDTO;
import com.dataops.dms.entity.ResourceOwner;
import com.dataops.dms.service.ResourceOwnerService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners")
public class ResourceOwnerController {

    @Resource
    private ResourceOwnerService resourceOwnerService;

    @GetMapping
    @Operation(summary = "获取所有Owner关系")
    public Result<List<ResourceOwner>> listAll() {
        return Result.success(resourceOwnerService.list());
    }

    @GetMapping("/resource")
    @Operation(summary = "按资源查询Owner")
    public Result<List<ResourceOwner>> listByResource(
            @RequestParam("type") String resourceType,
            @RequestParam("id") String resourceId) {
        return Result.success(resourceOwnerService.listByResource(resourceType, resourceId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "按用户查询其Owner资源")
    public Result<List<ResourceOwner>> listByUser(@PathVariable String userId) {
        return Result.success(resourceOwnerService.listByUser(userId));
    }

    @PostMapping("/assign")
    @Operation(summary = "分配资源Owner")
    public Result<ResourceOwner> assignOwner(@RequestBody AssignOwnerDTO dto, HttpServletRequest request) {
        String operatorId = (String) request.getAttribute("userId");
        if (operatorId == null) operatorId = "user_admin";
        ResourceOwner owner = new ResourceOwner();
        owner.setId(UUID.randomUUID().toString().replace("-", ""));
        owner.setResourceType(dto.getResourceType());
        owner.setResourceId(dto.getResourceId());
        owner.setResourceName(dto.getResourceName());
        owner.setOwnerUserId(dto.getOwnerUserId());
        owner.setOwnerUsername(dto.getOwnerUsername());
        owner.setParentResourceType(dto.getParentResourceType());
        owner.setParentResourceId(dto.getParentResourceId());
        owner.setCreatedAt(LocalDateTime.now());
        owner.setCreatedBy(operatorId);
        resourceOwnerService.save(owner);
        return Result.success("Owner分配成功", owner);
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "撤销Owner")
    public Result<Boolean> revokeOwner(@PathVariable String id, HttpServletRequest request) {
        String operatorId = (String) request.getAttribute("userId");
        if (operatorId == null) operatorId = "user_admin";
        boolean result = resourceOwnerService.revokeOwner(id, operatorId);
        return Result.success("Owner已撤销", result);
    }

    @GetMapping("/check")
    @Operation(summary = "检查用户是否为资源Owner")
    public Result<Boolean> checkIsOwner(
            @RequestParam("type") String resourceType,
            @RequestParam("id") String resourceId,
            @RequestParam("userId") String userId) {
        return Result.success(resourceOwnerService.checkIsOwner(userId, resourceType, resourceId));
    }
}
