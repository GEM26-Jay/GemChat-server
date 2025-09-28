package com.zcj.serviceuser.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcj.serviceuser.interceptor.TokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.util.List;

@Configuration
@Slf4j
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    @Autowired
    TokenInterceptor tokenInterceptor;

    @Autowired
    private ObjectMapper objectMapper; // 注入你自定义的ObjectMapper


    protected void addInterceptors(InterceptorRegistry registry) {

        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/user/login")
                .excludePathPatterns("/api/user/register")
                .excludePathPatterns("/api/sts/uploadAvatar");

    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 1. 先移除默认的Jackson消息转换器
        converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);

        // 2. 创建自定义消息转换器
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper); // 使用你自定义的ObjectMapper

        // 3. 将自定义转换器添加到列表首位
        converters.add(0, jacksonConverter);

    }
}
