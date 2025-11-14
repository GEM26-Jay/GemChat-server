package com.zcj.servicefile.interceptor;

import com.zcj.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class LooseTokenInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // 1. 从请求头获取 userId
            String userIdStr = request.getHeader("X-User-Id");
            if (StringUtils.hasText(userIdStr)) {
                // 2. 解析并设置到 ThreadLocal
                Long userId = Long.parseLong(userIdStr);
                UserContext.setId(userId);
                log.debug("解析用户令牌成功，userId: {}", userId);
            } else {
                // 3. 无 userId 头，设置为 null（避免残留值）
                UserContext.setId(null);
                log.debug("请求头未携带 X-User-Id");
            }
            return true; // 宽松模式，无论是否解析成功都放行
        } catch (NumberFormatException e) {
            // 4. 解析失败（格式错误），记录日志并设置为 null
            log.warn("X-User-Id 格式错误: ", e);
            UserContext.setId(null);
            return true;
        } catch (Exception e) {
            // 5. 其他异常（如空指针等）
            log.error("解析用户令牌发生异常", e);
            UserContext.setId(null);
            return true;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 无论 ThreadLocal 中是否有值，强制清理，避免内存泄露
        UserContext.clearId();
        log.debug("请求完成，清理 ThreadLocal 中的用户信息");
    }
}