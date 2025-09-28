package com.zcj.servicegateway;

import com.zcj.common.config.JWTProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class JWTTest {

    @Autowired
    JWTProperties jwtProperties;
//    @Autowired
//    JWTUtil jwtUtil;

    @Test
    void testJWTProperity() {
        System.out.println(jwtProperties.getSecretKey());
    }
}
