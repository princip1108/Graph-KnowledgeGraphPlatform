package com.sdu.kgplatform.common;

import lombok.Data;

/**
 * 统一API响应包装类
 * @param <T> 数据类型
 */
@Data
public class Result<T> {
    
    private boolean success;
    private String message;
    private T data;
    private String error;
    private Integer code;

    private Result() {}

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> ok() {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(200);
        return result;
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(200);
        result.setData(data);
        return result;
    }

    /**
     * 成功响应（带消息和数据）
     */
    public static <T> Result<T> ok(String message, T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 错误响应
     */
    public static <T> Result<T> error(String error) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(500);
        result.setError(error);
        return result;
    }

    /**
     * 错误响应（带状态码）
     */
    public static <T> Result<T> error(Integer code, String error) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(code);
        result.setError(error);
        return result;
    }

    /**
     * 未授权响应
     */
    public static <T> Result<T> unauthorized(String error) {
        return error(401, error != null ? error : "未登录或登录已过期");
    }

    /**
     * 禁止访问响应
     */
    public static <T> Result<T> forbidden(String error) {
        return error(403, error != null ? error : "没有权限执行此操作");
    }

    /**
     * 资源不存在响应
     */
    public static <T> Result<T> notFound(String error) {
        return error(404, error != null ? error : "资源不存在");
    }

    /**
     * 参数错误响应
     */
    public static <T> Result<T> badRequest(String error) {
        return error(400, error != null ? error : "请求参数错误");
    }
}
