package com.linpj.novel.create.utils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.lang.reflect.Type;

/**
 * JSON工具类，基于Gson实现
 * @author HL
 */
public class JsonUtil {

    private static final Gson GSON = new Gson();

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 待转换的对象
     * @return JSON字符串
     */
    public static String toJson(Object obj) {
        try {
            return GSON.toJson(obj);
        } catch (Exception e) {
            throw new RuntimeException("对象转JSON失败", e);
        }
    }

    /**
     * 将JSON字符串转换为指定类型的对象
     *
     * @param json JSON字符串
     * @param clazz 目标类
     * @param <T> 泛型类型
     * @return 转换后的对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return GSON.fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("JSON转对象失败", e);
        }
    }

    /**
     * 将JSON字符串转换为复杂类型的对象（如List、Map等）
     *
     * @param json JSON字符串
     * @param typeOfT 目标类型
     * @param <T> 泛型类型
     * @return 转换后的对象
     */
    public static <T> T fromJson(String json, Type typeOfT) {
        try {
            return GSON.fromJson(json, typeOfT);
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("JSON转对象失败", e);
        }
    }

    /**
     * 将JSON字符串转换为指定类型的对象（实例方法）
     *
     * @param json JSON字符串
     * @param clazz 目标类
     * @param <T> 泛型类型
     * @return 转换后的对象
     */
    public <T> T parseJson(String json, Class<T> clazz) {
        return fromJson(json, clazz);
    }

    /**
     * 将JSON字符串转换为复杂类型的对象（实例方法）
     * @param json JSON字符串
     * @param typeOfT 目标类型
     * @param <T> 泛型类型
     * @return 转换后的对象
     */
    public <T> T parseJson(String json, Type typeOfT) {
        return fromJson(json, typeOfT);
    }
}
