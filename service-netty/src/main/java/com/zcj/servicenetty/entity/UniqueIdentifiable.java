package com.zcj.servicenetty.entity;

/**
 * 标记接口：表示实现类具备生成全局唯一标识符的能力
 */
public interface UniqueIdentifiable {
    /**
     * 获取全局唯一的标识符（字符串形式）
     * @return 唯一标识符，确保在系统中不重复
     */
    String getUniqueId();
}
