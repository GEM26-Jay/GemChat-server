package com.zcj.common.handler;

import com.zcj.common.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public Result handleAllExceptions(Exception ex) {
        log.error(ex.getMessage());
        ex.printStackTrace();
        return Result.error(ex.getMessage());
    }

}
