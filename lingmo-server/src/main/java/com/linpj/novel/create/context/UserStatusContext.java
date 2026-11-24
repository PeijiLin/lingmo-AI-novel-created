package com.linpj.novel.create.context;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.HashMap;
import java.util.Map;

/**
 * @author HL
 */
public class UserStatusContext {
    private final static TransmittableThreadLocal<Map<String, Object>> THREAD_LOCAL = new TransmittableThreadLocal<>();

    //判断线程map是否为空，为空就添加一个map
    public static Map<String, Object> getLocalMap() {
        Map<String, Object> map = THREAD_LOCAL.get();
        if (map == null) {
            map = new HashMap<>(10);
            THREAD_LOCAL.set(map);
        }
        return map;
    }

    //把用户信息添加到线程 map中
    public static void set(String key, Object value) {
        Map<String, Object> map = getLocalMap();
        map.put(key, value);
    }

    //获得线程map中的数据
    public static Object get(String key) {
        Map<String, Object> map = getLocalMap();
        return map.get(key);
    }

    public static Long getCurrentUserId() {
        Object userIdObj = get("userId");
        if (userIdObj == null) {
            return null;
        }
        
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        } else if (userIdObj instanceof String) {
            try {
                return Long.parseLong((String) userIdObj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }

    public static void clear() {
        THREAD_LOCAL.remove();
    }
}
