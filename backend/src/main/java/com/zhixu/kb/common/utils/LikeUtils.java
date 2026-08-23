package com.zhixu.kb.common.utils;

/**
 * SQL LIKE 通配符转义：MySQL LIKE 默认以反斜杠为转义符，
 * 用户输入中的 % _ \ 需转义后参与匹配，避免通配符注入改变检索语义。
 */
public final class LikeUtils {

    private LikeUtils() {
    }

    public static String escape(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
