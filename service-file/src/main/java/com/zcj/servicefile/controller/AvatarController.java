package com.zcj.servicefile.controller;


import com.zcj.common.dto.FileUploadDTO;
import com.zcj.common.vo.FileTokenVO;
import com.zcj.common.vo.Result;
import com.zcj.servicefile.service.AvatarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/avatar")
@Tag(name = "用户头像服务接口")
@Slf4j
@RequiredArgsConstructor
public class AvatarController {

    final private AvatarService avatarService;

    @PostMapping("/upload")
    @Operation(summary = "获取上传头像文件的STS令牌", description = "获取用于前端直传OSS的临时令牌")
    public Result<FileTokenVO> upload(@RequestBody FileUploadDTO fileUploadDTO) {
        FileTokenVO token = avatarService.upload(fileUploadDTO);
        return Result.success(token);
    }

    @GetMapping("/download")
    @Operation(summary = "获取下载头像文件的STS令牌", description = "获取用于前端从OSS下载文件的临时令牌")
    public Result<FileTokenVO> download(String fileName) {
        FileTokenVO token = avatarService.download(fileName);
        return Result.success(token);
    }

    @PutMapping("/addRef")
    @Operation(summary = "头像文件引用数量+1", description = "更新数据库文件引用状态")
    public Result<Void> addRef(@RequestParam String fileName,
                               @RequestParam(value = "userId", required = false) Long userId) {
        avatarService.addRef(fileName, userId);
        return Result.success();
    }

    @PutMapping("/deleteRef")
    @Operation(summary = "头像文件引用数量-1", description = "更新数据库文件引用状态")
    public Result<Void> deleteRef(@RequestParam String fileName) {
        avatarService.deleteRef(fileName);
        return Result.success();
    }

    @PutMapping("/changeRef")
    @Operation(summary = "更改头像引用", description = "更新数据库文件引用状态")
    public Result<Void> changeRef(@RequestParam String delFileName, @RequestParam String addFileName) {
        avatarService.changeRef(delFileName, addFileName);
        return Result.success();
    }
}
