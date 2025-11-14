package com.zcj.servicefile.service;

import com.zcj.common.dto.FileUploadDTO;
import com.zcj.common.vo.FileTokenVO;

public interface AvatarService {
    FileTokenVO upload(FileUploadDTO fileUploadDTO);

    FileTokenVO download(String fileName);

    void deleteRef(String fileName);

    void changeRef(String delFilaName, String addFileName);

    void addRef(String fileName, Long userId);
}
