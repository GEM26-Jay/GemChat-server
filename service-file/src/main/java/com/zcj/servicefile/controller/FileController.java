package com.zcj.servicefile.controller;


import com.zcj.common.context.UserContext;
import com.zcj.common.dto.FileUploadDTO;
import com.zcj.servicefile.service.FileService;
import com.zcj.servicefile.service.StsService;
import com.zcj.common.vo.Result;
import com.zcj.common.vo.FileTokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/file")
@Tag(name = "文件服务接口")
@Slf4j
@RequiredArgsConstructor
public class FileController {

    final private FileService fileService;

    @PostMapping("/upload")
    @Operation(summary = "获取上传文件的STS令牌", description = "获取用于前端直传OSS的临时令牌")
    public Result<FileTokenVO> upload(@RequestBody FileUploadDTO fileUploadDTO) {
        Long userId = UserContext.getId();
        log.info("用户[{}]请求上传令牌，原始文件名:{}", userId, fileUploadDTO.getName());
        FileTokenVO token = fileService.upload(fileUploadDTO);
        return Result.success(token);
    }

    @GetMapping("/download")
    @Operation(summary = "获取下载文件的STS令牌", description = "获取用于前端从OSS下载文件的临时令牌")
    public Result<FileTokenVO> download(String fileName) {
        Long userId = UserContext.getId();
        log.info("用户[{}]请求用户文件下载令牌, 文件名: {}", userId, fileName);
        FileTokenVO token = fileService.download(fileName);
        return Result.success(token);
    }

    @PutMapping("/successUpload")
    @Operation(summary = "上传成功回调", description = "更新数据库状态")
    public Result<Void> successUpload(@RequestParam String  fileName) {
        Long userId = UserContext.getId();
        log.info("用户[{}], 执行上传成功回调, 文件名: {}", userId, fileName);
        fileService.successUpload(fileName);
        return Result.success();
    }

    @PutMapping("/failUpload")
    @Operation(summary = "上传失败回调", description = "更新数据库状态")
    public Result<Void> failUpload(@RequestParam String fileName) {
        Long userId = UserContext.getId();
        log.info("用户[{}], 执行上传失败回调, 文件名: {}", userId, fileName);
        fileService.failUpload(fileName);
        return Result.success();
    }
}
