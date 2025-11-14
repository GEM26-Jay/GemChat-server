package com.zcj.servicefile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zcj.common.context.UserContext;
import com.zcj.common.dto.FileUploadDTO;
import com.zcj.common.entity.AvatarBox;
import com.zcj.common.entity.FileBox;
import com.zcj.common.utils.FileUtil;
import com.zcj.common.utils.SnowflakeIdGenerator;
import com.zcj.common.vo.FileTokenVO;
import com.zcj.servicefile.mapper.AvatarBoxMapper;
import com.zcj.servicefile.mapper.FileBoxMapper;
import com.zcj.servicefile.service.FileService;
import com.zcj.servicefile.service.OssService;
import com.zcj.servicefile.service.StsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {

    final private StsService stsService;
    final private OssService ossService;
    final private FileBoxMapper fileBoxMapper;
    final private SnowflakeIdGenerator idGenerator;

    private static String OSS_DIR = "userFile";

    @Override
    public FileTokenVO upload(FileUploadDTO fileUploadDTO) {
        // 查询已有头像
        FileBox fileMap = selectByFingerprint(
                fileUploadDTO.getFingerprint(),
                fileUploadDTO.getMimeType(),
                fileUploadDTO.getSize()
        );
        Long currentUserId = UserContext.getId();

        // 已存在且上传成功的情况
        if (fileMap != null && fileMap.getStatus() == FileBox.STATUS_SUCCESS) {
            return buildExistFileToken(fileMap);
        }

        // 需要处理上传的情况（不存在/已删除/上传失败/上传中）
        boolean isNewAvatar = (fileMap == null);
        String fileName = isNewAvatar
                ? FileUtil.generateNameByFingerprint(fileUploadDTO.getName(), fileUploadDTO.getFingerprint())
                : fileMap.getName();

        // 处理上传中但实际已完成的特殊情况
        if (fileMap != null && fileMap.getStatus() == FileBox.STATUS_UPLOADING) {
            if (ossService.isExist(OSS_DIR, fileName)) {
                fileMap.setFromId(currentUserId);   // 降级为当前用户上传的文件
                fileMap.setStatus(AvatarBox.STATUS_SUCCESS);
                fileMap.setUpdatedAt(System.currentTimeMillis());
                fileBoxMapper.updateById(fileMap);
                return buildExistFileToken(fileMap);
            }
        }

        // 生成上传凭证
        FileTokenVO uploadToken = stsService.getUploadToken(OSS_DIR, fileName);
        uploadToken.setName(fileName);
        uploadToken.setSize(fileUploadDTO.getSize());
        uploadToken.setExist(false);

        // 更新文件信息
        fileUploadDTO.setName(fileName);
        fileUploadDTO.setPath(uploadToken.getPath());

        // 处理头像记录（新增或更新）
        if (isNewAvatar) {
            fileMap = insertNewFile(fileUploadDTO);
        } else {
            fileMap.setFromId(currentUserId);
            fileMap.setStatus(AvatarBox.STATUS_UPLOADING);
            fileMap.setUpdatedAt(System.currentTimeMillis());
            fileBoxMapper.updateById(fileMap);
        }

        return uploadToken;
    }


    @Override
    public FileTokenVO download(String fileName) {
        FileBox fileBox = selectByFileName(fileName);
        if (fileBox != null && fileBox.getStatus()==FileBox.STATUS_SUCCESS) {
            // 文件存在
            FileTokenVO token = stsService.getDownloadToken(OSS_DIR, fileName);
            token.setExist(true);
            token.setSize(fileBox.getSize());
            token.setName(fileBox.getName());
            return token;
        } else {
            // 文件不存在
            return buildNotExistFileToken(fileName);
        }
    }

    @Override
    public void successUpload(String fileName) {
        Long id = UserContext.getId();
        FileBox file = selectByFileName(fileName);
        if (file == null) throw new RuntimeException("文件不存在");
        if (file.getFromId().equals(id)) {
            file.setStatus(AvatarBox.STATUS_SUCCESS);
            file.setUpdatedAt(System.currentTimeMillis());
            file.setReferCount(1);
            fileBoxMapper.updateById(file);
        } else {
            throw new RuntimeException("上传用户错误！");
        }
    }

    @Override
    public void failUpload(String fileName) {
        Long id = UserContext.getId();
        FileBox file = selectByFileName(fileName);
        if (file == null) throw new RuntimeException("文件不存在");
        if (file.getFromId().equals(id)) {
            file.setStatus(AvatarBox.STATUS_FAILED);
            file.setUpdatedAt(System.currentTimeMillis());
            fileBoxMapper.updateById(file);
        } else {
            throw new RuntimeException("上传用户错误！");
        }
    }

    /**
     * 插入新文件记录
     */
    public FileBox insertNewFile(FileUploadDTO fileUploadDTO) {
        Long id = UserContext.getId();
        FileBox fileMap = new FileBox();
        fileMap.setName(fileUploadDTO.getName());
        fileMap.setLocation(fileUploadDTO.getPath());
        fileMap.setSize(fileUploadDTO.getSize());
        fileMap.setMimeType(fileUploadDTO.getMimeType());
        fileMap.setFingerprint(fileUploadDTO.getFingerprint());
        fileMap.setReferCount(0);
        fileMap.setFromId(id);
        fileMap.setFromType(fileUploadDTO.getFromType());
        fileMap.setFromSession(fileUploadDTO.getFromSession());
        long timeMillis = System.currentTimeMillis();
        fileMap.setCreatedAt(timeMillis);
        fileMap.setUpdatedAt(timeMillis);
        fileMap.setStatus(FileBox.STATUS_UPLOADING);
        fileMap.setId(idGenerator.nextId());
        fileBoxMapper.insert(fileMap);
        return fileMap;
    }

    /**
     * 构建已存在文件的令牌信息
     */
    private FileTokenVO buildExistFileToken(FileBox fileMap) {
        FileTokenVO tokenVO = new FileTokenVO();
        tokenVO.setExist(true);
        tokenVO.setName(fileMap.getName());
        tokenVO.setPath(fileMap.getLocation());
        return tokenVO;
    }

    /**
     * 构建不存在文件的令牌信息
     */
    private FileTokenVO buildNotExistFileToken(String fileName) {
        FileTokenVO tokenVO = new FileTokenVO();
        tokenVO.setExist(false);
        tokenVO.setName(fileName);
        return tokenVO;
    }

    /**
     * 根据指纹查询文件
     */
    private FileBox selectByFingerprint(String fingerprint, String mineType, Long size) {
        LambdaQueryWrapper<FileBox> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileBox::getFingerprint, fingerprint);
        queryWrapper.eq(FileBox::getMimeType, mineType);
        queryWrapper.eq(FileBox::getSize, size);
        return fileBoxMapper.selectOne(queryWrapper);
    }
    /**
     * 根据指纹查询文件
     */
    private FileBox selectByFileName(String fileName) {
        LambdaQueryWrapper<FileBox> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileBox::getName, fileName);
        return fileBoxMapper.selectOne(queryWrapper);
    }
}