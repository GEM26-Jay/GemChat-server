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
    final private StsService stsService;

    @PostMapping("/upload")
    @Operation(summary = "获取上传文件的STS令牌", description = "获取用于前端直传OSS的临时令牌")
    public Result<FileTokenVO> getUploadToken(@RequestBody FileUploadDTO fileUploadDTO) {
        Long userId = UserContext.getId();
        log.info("用户{}请求上传令牌，原始文件大小:{}", userId, fileUploadDTO.getSize());
        FileTokenVO vo = fileService.doUserFileUpload(fileUploadDTO);
        return Result.success(vo);
    }

    @PostMapping("/insertDB")
    @Operation(summary = "获取上传文件的STS令牌", description = "获取用于前端直传OSS的临时令牌")
    public Result<Void> insertDB(@RequestBody FileUploadDTO fileUploadDTO) {
        Long userId = UserContext.getId();
        fileService.insertDB(fileUploadDTO);
        return Result.success();
    }

    @GetMapping("/download")
    @Operation(summary = "获取下载文件的STS令牌", description = "获取用于前端从OSS下载文件的临时令牌")
    public Result<FileTokenVO> getDownloadToken(String fileName) {
        Long userId = UserContext.getId();
        log.info("用户[{}]请求下载令牌", userId);
        FileTokenVO token = fileService.getDownloadToken("userFile", fileName);
        return Result.success(token);
    }

    @PostMapping("/uploadAvatar")
    @Operation(summary = "获取上传头像文件的STS令牌", description = "获取用于前端直传OSS的临时令牌")
    public Result<FileTokenVO> getAvatarUploadToken(@RequestBody FileUploadDTO fileUploadDTO) {
        Long userId = UserContext.getId();
        log.info("用户{}请求上传令牌，原始大小:{}", userId, fileUploadDTO.getSize());
        FileTokenVO token = fileService.doAvatarUpload(fileUploadDTO);
        return Result.success(token);
    }

    @GetMapping("/downloadAvatar")
    @Operation(summary = "获取下载头像文件的STS令牌", description = "获取用于前端从OSS下载文件的临时令牌")
    public Result<FileTokenVO> getAvatarDownloadToken(String fileName) {
        Long userId = UserContext.getId();
        log.info("用户{}请求下载令牌", userId);
        FileTokenVO token = fileService.getDownloadToken("avatars", fileName);
        return Result.success(token);
    }
}
