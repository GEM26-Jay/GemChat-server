package com.zcj.servicefile.interceptor;

import com.zcj.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class TokenInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            //1、从请求头中获取令牌
            String userId = request.getHeader("X-User-Id");
            UserContext.setId(Long.parseLong(userId));
            return true;
        }catch (Exception ex) {
            //4、不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        /**
         * 连接结束之后清理ThreadLocal的内容，避免内存泄露
         */
        if (UserContext.getId() != null) {
            UserContext.clearId();
        }
    }

}
