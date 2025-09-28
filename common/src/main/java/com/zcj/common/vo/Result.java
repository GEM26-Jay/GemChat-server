package com.zcj.common.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    public static final Integer SUCCESS = 200;
    public static final Integer FAILURE = 500;

    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = SUCCESS;
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<T>();
        result.code = SUCCESS;
        result.data = data;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<T>();
        result.code = FAILURE;
        result.msg = msg;
        return result;
    }

}
