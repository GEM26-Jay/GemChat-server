package com.zcj.servicefile.service;

import com.zcj.common.dto.FileUploadDTO;
import com.zcj.common.vo.FileTokenVO;

public interface FileService {
    FileTokenVO doUserFileUpload(FileUploadDTO fileUploadDTO);

    FileTokenVO doAvatarUpload(FileUploadDTO fileUploadDTO);

    FileTokenVO getDownloadToken(String dirPath, String fileName);

    void insertDB(FileUploadDTO fileUploadDTO);
}
