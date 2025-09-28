package com.zcj.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class DateTimeFormatConfig {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            JavaTimeModule module = new JavaTimeModule();

            // ======= 1. 配置 LocalDateTime ========
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));

            // ======= 2. 新增 LocalDate 配置 ========
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
            module.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));

            // ======= 3. 其他配置 ========
            builder.modules(module);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        };
    }
}
