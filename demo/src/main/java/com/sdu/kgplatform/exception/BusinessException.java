package com.sdu.kgplatform.exception;

/**
 * 业务异常基类
 * 用于封装业务逻辑中的可预期异常
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    /**
     * 未授权异常
     */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message != null ? message : "未登录或登录已过期");
    }

    /**
     * 禁止访问异常
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message != null ? message : "没有权限执行此操作");
    }

    /**
     * 资源不存在异常
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(404, message != null ? message : "资源不存在");
    }

    /**
     * 参数错误异常
     */
    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message != null ? message : "请求参数错误");
    }
}
