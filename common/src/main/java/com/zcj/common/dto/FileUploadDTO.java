package com.zcj.common.dto;

import lombok.Data;

@Data
public class FileUploadDTO {
    private String name;
    private Long size;
    private String mimeType;
    private String fingerprint;
    private String path;
}
