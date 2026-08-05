package com.dpe.common.model;

import java.util.regex.Pattern;

/**
 * Minecraft 资源定位符 (namespace:path)，不可变 record。
 * namespace 合法字符：[a-z0-9_.-]+，path 合法字符：[a-z0-9_/.-]+
 */
public record ResourceLocation(String namespace, String path) {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");
    private static final Pattern PATH_PATTERN = Pattern.compile("^[a-z0-9_/.-]+$");
    private static final String DEFAULT_NAMESPACE = "minecraft";

    public ResourceLocation {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace 不能为空");
        }
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("非法 namespace: " + namespace
                    + "（仅允许小写字母、数字、下划线、点、连字符）");
        }
        if (!PATH_PATTERN.matcher(path).matches()) {
            throw new IllegalArgumentException("非法 path: " + path
                    + "（仅允许小写字母、数字、下划线、斜杠、点、连字符）");
        }
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    /**
     * 解析字符串为 ResourceLocation。
     * 支持 "ns:path" 与 "path"（默认 namespace 为 minecraft）。
     */
    public static ResourceLocation parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("ResourceLocation 不能为空");
        }
        int idx = raw.indexOf(':');
        if (idx < 0) {
            return new ResourceLocation(DEFAULT_NAMESPACE, raw);
        }
        String ns = raw.substring(0, idx);
        String p = raw.substring(idx + 1);
        if (ns.isEmpty()) {
            ns = DEFAULT_NAMESPACE;
        }
        return new ResourceLocation(ns, p);
    }

    /**
     * 尝试解析，失败返回 null（不抛异常）。
     */
    public static ResourceLocation tryParse(String raw) {
        try {
            return parse(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
