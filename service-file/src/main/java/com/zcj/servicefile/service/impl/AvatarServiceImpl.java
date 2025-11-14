package com.zcj.servicefile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zcj.common.context.UserContext;
import com.zcj.common.dto.FileUploadDTO;
import com.zcj.common.entity.AvatarBox;
import com.zcj.common.utils.FileUtil;
import com.zcj.common.utils.RedisDistributedLock;
import com.zcj.common.utils.SnowflakeIdGenerator;
import com.zcj.common.vo.FileTokenVO;
import com.zcj.servicefile.mapper.AvatarBoxMapper;
import com.zcj.servicefile.service.AvatarService;
import com.zcj.servicefile.service.OssService;
import com.zcj.servicefile.service.StsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {

    final private StsService stsService;
    final private OssService ossService;
    final private AvatarBoxMapper avatarBoxMapper;
    final private SnowflakeIdGenerator idGenerator;
    final private StringRedisTemplate redisTemplate;

    private static String OSS_DIR = "avatars";

    @Override
    @Transactional
    public FileTokenVO upload(FileUploadDTO fileUploadDTO) {
        // 查询已有头像
        AvatarBox avatar = selectByFingerprint(
                fileUploadDTO.getFingerprint(),
                fileUploadDTO.getMimeType(),
                fileUploadDTO.getSize()
        );
        Long currentUserId = UserContext.getId();

        // 已存在且上传成功的情况
        if (avatar != null && avatar.getStatus() == AvatarBox.STATUS_SUCCESS) {
            return buildExistFileToken(avatar);
        }

        // 需要处理上传的情况（不存在/已删除/上传失败/上传中）
        boolean isNewAvatar = (avatar == null);
        String fileName = isNewAvatar
                ? FileUtil.generateNameByFingerprint(fileUploadDTO.getName(), fileUploadDTO.getFingerprint())
                : avatar.getName();

        // 处理上传中但实际已完成的特殊情况
        if (avatar != null && avatar.getStatus() == AvatarBox.STATUS_UPLOADING) {
            if (ossService.isExist(OSS_DIR, fileName)) {
                avatar.setStatus(AvatarBox.STATUS_SUCCESS);
                avatar.setUpdatedAt(System.currentTimeMillis());
                avatarBoxMapper.updateById(avatar);
                return buildExistFileToken(avatar);
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
            avatar = insertNewAvatar(fileUploadDTO);
        } else {
            avatar.setFromId(currentUserId);
            avatar.setStatus(AvatarBox.STATUS_UPLOADING);
            avatar.setUpdatedAt(System.currentTimeMillis());
            avatarBoxMapper.updateById(avatar);
        }

        return uploadToken;
    }

    @Override
    public FileTokenVO download(String fileName) {
        AvatarBox avatar = selectByFileName(fileName);
        if (avatar != null && avatar.getStatus()==AvatarBox.STATUS_SUCCESS) {
            // 头像文件存在
            FileTokenVO token = stsService.getDownloadToken(OSS_DIR, fileName);
            token.setExist(true);
            token.setSize(avatar.getSize());
            token.setName(avatar.getName());
            return token;
        } else {
            // 头像文件不存在
            return buildNotExistFileToken(fileName);
        }
    }

    @Override
    @Transactional
    public void deleteRef(String fileName) {
        String key = "avatar:update:" + fileName;
        // 简单分布式锁实现，因为这里的操作速度很快，一般原小于10s
        RedisDistributedLock lock = new RedisDistributedLock(redisTemplate, key);
        try {
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                AvatarBox avatar = selectByFileName(fileName);
                if (avatar == null || avatar.getReferCount() <= 0) throw new RuntimeException("头像文件不存在");
                avatarBoxMapper.decrementReferCount(fileName);
            } else {
                throw new RuntimeException("业务超时");
            }
        }catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void changeRef(String delFilaName, String addFileName) {
        String key1 = "avatar:update:" + delFilaName;
        String key2 = "avatar:update:" + addFileName;

        // 简单分布式锁实现，因为这里的操作速度很快，一般原小于10s
        RedisDistributedLock lock1 = new RedisDistributedLock(redisTemplate, key1);
        RedisDistributedLock lock2 = new RedisDistributedLock(redisTemplate, key2);
        try {
            boolean b1 = lock1.tryLock(10, TimeUnit.SECONDS);
            boolean b2 = false;
            if (b1) {
                b2 = lock2.tryLock(10, TimeUnit.SECONDS);
            }
            if (!b1 || !b2) {
                throw new RuntimeException("业务等待超时");
            }
            AvatarBox delAvatar = selectByFileName(delFilaName);
            AvatarBox addAvatar = selectByFileName(addFileName);
            if (delAvatar == null || addAvatar == null) {
                throw new RuntimeException("用户头像文件不存在");
            }
            if (addAvatar.getStatus() == AvatarBox.STATUS_UPLOADING) {
                addAvatar.setStatus(AvatarBox.STATUS_SUCCESS);
                addAvatar.setFromId(UserContext.getId());
                addAvatar.setUpdatedAt(System.currentTimeMillis());
                addAvatar.setReferCount(1);
                avatarBoxMapper.updateById(addAvatar);
            } else {
                avatarBoxMapper.incrementReferCount(addFileName);
            }
            avatarBoxMapper.decrementReferCount(delFilaName);
        }catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            lock1.unlock();
            lock2.unlock();
        }
    }

    @Override
    @Transactional
    public void addRef(String fileName, Long userId) {
        if (userId == null) {
            userId = UserContext.getId();
        }
        String key = "avatar:update:" + fileName;
        RedisDistributedLock lock = new RedisDistributedLock(redisTemplate, key);
        try {
            boolean b = lock.tryLock(10, TimeUnit.SECONDS);
            if (b) {
                AvatarBox avatar = selectByFileName(fileName);
                if (avatar == null) throw new RuntimeException("头像文件不存在");
                if (avatar.getStatus() == AvatarBox.STATUS_UPLOADING) {
                    avatar.setStatus(AvatarBox.STATUS_SUCCESS);
                    avatar.setFromId(userId);
                    avatar.setReferCount(1);
                    avatar.setUpdatedAt(System.currentTimeMillis());
                    avatarBoxMapper.updateById(avatar);
                } else {
                    avatarBoxMapper.incrementReferCount(fileName);
                }
            } else {
                throw new RuntimeException("业务等待超时");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public AvatarBox insertNewAvatar(FileUploadDTO fileUploadDTO) {
        AvatarBox avatarMap = new AvatarBox();
        avatarMap.setName(fileUploadDTO.getName());
        avatarMap.setLocation(fileUploadDTO.getPath());
        avatarMap.setSize(fileUploadDTO.getSize());
        avatarMap.setMimeType(fileUploadDTO.getMimeType());
        avatarMap.setFingerprint(fileUploadDTO.getFingerprint());
        avatarMap.setReferCount(0);
        long timeMillis = System.currentTimeMillis();
        avatarMap.setCreatedAt(timeMillis);
        avatarMap.setUpdatedAt(timeMillis);
        avatarMap.setStatus(AvatarBox.STATUS_UPLOADING);
        avatarMap.setId(idGenerator.nextId());
        avatarBoxMapper.insert(avatarMap);
        return avatarMap;
    }

    /**
     * 根据指纹查询文件
     */
    private AvatarBox selectByFingerprint(String fingerprint, String miniType, Long size) {
        LambdaQueryWrapper<AvatarBox> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AvatarBox::getFingerprint, fingerprint);
        queryWrapper.eq(AvatarBox::getMimeType, miniType);
        queryWrapper.eq(AvatarBox::getSize, size);
        return avatarBoxMapper.selectOne(queryWrapper);
    }
    /**
     * 根据文件名称查询文件
     */
    private AvatarBox selectByFileName(String fileName) {
        LambdaQueryWrapper<AvatarBox> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AvatarBox::getName, fileName);
        return avatarBoxMapper.selectOne(queryWrapper);
    }

    /**
     * 构建已存在文件的令牌信息
     */
    private FileTokenVO buildExistFileToken(AvatarBox avatarBox) {
        FileTokenVO tokenVO = new FileTokenVO();
        tokenVO.setExist(true);
        tokenVO.setName(avatarBox.getName());
        tokenVO.setPath(avatarBox.getLocation());
        tokenVO.setSize(avatarBox.getSize());
        return tokenVO;
    }

    private FileTokenVO buildNotExistFileToken(String fileName) {
        FileTokenVO tokenVO = new FileTokenVO();
        tokenVO.setExist(false);
        tokenVO.setName(fileName);
        return tokenVO;
    }

}
