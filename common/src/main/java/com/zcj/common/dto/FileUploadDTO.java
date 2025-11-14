package com.zcj.common.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class FileUploadDTO {
    private String name;
    private Long size;
    private String mimeType;
    private String fingerprint;
    private String path;
    private Integer fromType;
    private Long fromSession;
}
