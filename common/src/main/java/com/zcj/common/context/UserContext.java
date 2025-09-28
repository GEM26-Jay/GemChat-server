package com.zcj.common.context;

public class UserContext {
    // 创建一个 ThreadLocal 实例，用于存储 ID
    private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    // 设置 ID 的方法
    public static void setId(Long id) {
        threadLocal.set(id);
    }

    // 获取 ID 的方法
    public static Long getId() {
        return threadLocal.get();
    }

    // 清除 ID 的方法，避免内存泄漏
    public static void clearId() {
        threadLocal.remove();
    }

}
