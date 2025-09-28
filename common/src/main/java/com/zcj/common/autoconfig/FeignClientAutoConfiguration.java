package com.zcj.common.autoconfig;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {"com.zcj.common.feign"})
public class FeignClientAutoConfiguration {

}
