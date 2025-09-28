package com.zcj.servicefile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zcj.common.dto.FileUploadDTO;
import com.zcj.common.entity.FileBox;
import com.zcj.common.utils.SnowflakeIdGenerator;
import com.zcj.common.vo.FileTokenVO;
import com.zcj.servicefile.mapper.FileBoxMapper;
import com.zcj.servicefile.service.FileService;
import com.zcj.servicefile.service.StsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {

    private final StsService stsService;
    private final FileBoxMapper fileBoxMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 统一处理文件上传令牌逻辑，减少重复代码
     */
    @Override
    public FileTokenVO doUserFileUpload(FileUploadDTO fileUploadDTO) {
        return handleFileUpload(fileUploadDTO, "userFile");
    }

    @Override
    public FileTokenVO doAvatarUpload(FileUploadDTO fileUploadDTO) {
        return handleFileUpload(fileUploadDTO, "avatars");
    }

    @Override
    public void insertDB(FileUploadDTO fileUploadDTO) {
        // 检查文件是否已存在
        FileBox fileMap = selectByFingerprint(fileUploadDTO.getFingerprint());
        if (fileMap != null) {
            return;
        }
        fileMap = new FileBox();
        fileMap.setName(fileUploadDTO.getName());
        fileMap.setLocation(fileUploadDTO.getPath());
        fileMap.setSize(fileUploadDTO.getSize());
        fileMap.setMimeType(fileUploadDTO.getMimeType());
        fileMap.setFingerprint(fileUploadDTO.getFingerprint());
        fileMap.setReferCount(1);
        long timeMillis = System.currentTimeMillis();
        fileMap.setCreatedAt(timeMillis);
        fileMap.setUpdatedAt(timeMillis);
        fileMap.setStatus(FileBox.STATUS_NORMAL);
        fileMap.setId(snowflakeIdGenerator.nextId());
        fileBoxMapper.insert(fileMap);
    }

    @Override
    public FileTokenVO getDownloadToken(String dirPath, String fileName) {
        FileBox fileMap = selectByFileName(fileName);
        if (fileMap == null) {
            FileTokenVO token = new FileTokenVO();
            token.setExist(false);
            return token;
        }
        FileTokenVO token = stsService.getDownloadToken(dirPath, fileName);
        token.setExist(true);
        token.setSize(fileMap.getSize());
        return token;
    }

    /**
     * 通用文件上传处理逻辑
     * @param fileUploadDTO 上传请求参数
     * @param dirName 存储目录
     * @return 文件令牌信息
     */
    private FileTokenVO handleFileUpload(FileUploadDTO fileUploadDTO, String dirName) {
        // 入参校验
        Assert.notNull(fileUploadDTO, "上传参数不能为空");
        Assert.hasText(fileUploadDTO.getFingerprint(), "文件指纹不能为空");
        Assert.hasText(fileUploadDTO.getName(), "文件名不能为空");
        Assert.hasText(dirName, "存储目录不能为空");

        log.info("处理文件上传，目录:{}，文件名:{}，指纹:{}",
                dirName, fileUploadDTO.getName(), fileUploadDTO.getFingerprint());

        // 检查文件是否已存在
        FileBox fileMap = selectByFingerprint(fileUploadDTO.getFingerprint());
        if (fileMap != null) {
            log.info("文件已存在，指纹:{}，路径:{}",
                    fileUploadDTO.getFingerprint(), fileMap.getLocation());
            return buildExistFileToken(fileMap);
        }

        return stsService.getUploadToken(dirName, fileUploadDTO.getName());
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
     * 根据指纹查询文件
     */
    private FileBox selectByFingerprint(String fingerprint) {
        LambdaQueryWrapper<FileBox> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileBox::getFingerprint, fingerprint);
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