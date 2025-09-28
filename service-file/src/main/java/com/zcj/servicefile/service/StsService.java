package com.zcj.servicefile.service;

import com.zcj.common.vo.FileTokenVO;

public interface StsService {
    FileTokenVO getUploadToken(String dirName, String fileName);

    FileTokenVO getDownloadToken(String dirName, String fileName);
}
