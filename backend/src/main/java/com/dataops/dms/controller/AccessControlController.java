package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.dto.AccessControlDTO;
import com.dataops.dms.entity.MetadataAccessControl;
import com.dataops.dms.service.MetadataAccessControlService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/v1/access-control")
public class AccessControlController {

    @Resource
    private MetadataAccessControlService accessControlService;

    @GetMapping
    @Operation(summary = "获取所有访问控制规则")
    public Result<List<MetadataAccessControl>> listAll() {
        return Result.success(accessControlService.list());
    }

    @GetMapping("/check")
    @Operation(summary = "检查用户是否可以访问指定资源")
    public Result<Boolean> canUserAccess(
            @RequestParam("type") String resourceType,
            @RequestParam("id") String resourceId,
            @RequestParam("userId") String userId) {
        return Result.success(accessControlService.canUserAccess(userId, resourceType, resourceId));
    }

    @PostMapping("/enable")
    @Operation(summary = "开启资源访问控制")
    public Result<MetadataAccessControl> enableControl(@RequestBody AccessControlDTO dto, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        MetadataAccessControl control = accessControlService.enableControl(
                dto.getResourceType(), dto.getResourceId(), dto.getResourceName(), userId);
        return Result.success("访问控制已开启", control);
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "关闭资源访问控制")
    public Result<Boolean> disableControl(@PathVariable String id, @RequestBody(required = false) AccessControlDTO dto,
                                           HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        String parentResourceId = dto != null ? dto.getParentResourceId() : null;
        boolean result = accessControlService.disableControl(id, userId, parentResourceId);
        return Result.success("访问控制已关闭", result);
    }
}
