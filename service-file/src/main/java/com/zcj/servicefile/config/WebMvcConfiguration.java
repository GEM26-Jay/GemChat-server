package com.zcj.servicefile.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcj.servicefile.interceptor.TokenInterceptor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.util.List;

@Configuration
@Slf4j
@AllArgsConstructor
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    private ObjectMapper objectMapper; // 注入你自定义的ObjectMapper
    TokenInterceptor tokenInterceptor;

    protected void addInterceptors(InterceptorRegistry registry) {

        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/file/uploadAvatar");

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
