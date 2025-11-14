package com.zcj.common.utils;

public class FileUtil {

    /**
     * 根据文件指纹和原文件名生成新文件名（保留原后缀）
     *
     * @param originName: 原文件名
     * @param fingerprint: Hash256文件指纹（64个十六进制字符）
     * @return 新文件名（64位指纹 + 原后缀，如 "abc123...xyz.txt"）
     */
    public static String generateNameByFingerprint(String originName, String fingerprint) {
        // 校验指纹合法性
        if (fingerprint == null || !fingerprint.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalArgumentException("无效的Hash256指纹，必须是64个十六进制字符");
        }
        // 校验原文件名非空
        if (originName == null || originName.trim().isEmpty()) {
            throw new IllegalArgumentException("原文件名不能为空");
        }

        // 提取原文件后缀（包含 "."，如 ".txt"；无后缀则为空字符串）
        String suffix = "";
        int lastDotIndex = originName.lastIndexOf('.');
        // 确保 "." 不是文件名的第一个字符（避免误判类似 ".gitignore" 的文件）
        if (lastDotIndex > 0 && lastDotIndex < originName.length() - 1) {
            suffix = originName.substring(lastDotIndex);
        }

        // 指纹转为小写 + 后缀（最终长度为 64 + 后缀长度）
        return fingerprint.toLowerCase() + suffix;
    }
}
