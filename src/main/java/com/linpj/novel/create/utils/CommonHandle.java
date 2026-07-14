package com.linpj.novel.create.utils;

/**
 * @author HL
 */
public class CommonHandle {
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    public static boolean isReptile(int pageSize) {
        return pageSize > 50;
    }
}