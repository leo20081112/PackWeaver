package com.dpe.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minecraft Wiki 摘要抓取（Task 6）。
 * 使用 JDK 内置 {@link HttpClient}，超时 5s，失败/离线返回 null。
 * 通过 MediaWiki API 获取页面 wikitext 摘要。
 */
public final class WikiFetcher {

    /** 默认 wiki API 端点。 */
    private static final String API_BASE = "https://minecraft.wiki/api.php";
    /** 请求超时（5 秒）。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private WikiFetcher() {
    }

    /**
     * 在 Minecraft Wiki 查询指定词条的摘要。
     * @param term 词条名（中文/英文均可，会自动 URL 编码）
     * @return wikitext 摘要文本；失败/离线返回 null
     */
    public static String fetch(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String encoded = URLEncoder.encode(term.trim(), StandardCharsets.UTF_8);
        String url = API_BASE
                + "?action=parse&page=" + encoded
                + "&format=json&prop=wikitext&redirects=1&formatversion=2";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "InGameDatapackEditor/1.0 (Fabric mod)")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                return null;
            }
            String body = resp.body();
            if (body == null || body.isEmpty()) {
                return null;
            }
            return extractWikitext(body);
        } catch (Exception e) {
            // 离线/超时/解析失败一律视为不可用
            return null;
        }
    }

    /** 从 MediaWiki API JSON 响应中粗略提取 wikitext 字段内容。 */
    private static String extractWikitext(String json) {
        // 简单字段提取，避免引入完整 JSON 依赖
        String key = "\"wikitext\"";
        int idx = json.indexOf(key);
        if (idx < 0) {
            // formatversion=2 时字段可能直接是 "wikitext":"..."
            // 也可能在 parse 对象内
            return null;
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        // 跳过空白
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != '"') {
            return null;
        }
        // 解析带转义的字符串
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                switch (n) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'u' -> {
                        if (i + 5 < json.length()) {
                            try {
                                int cp = Integer.parseInt(json.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 4;
                            } catch (NumberFormatException ex) {
                                sb.append(n);
                            }
                        } else {
                            sb.append(n);
                        }
                    }
                    default -> sb.append(n);
                }
                i += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
            i++;
        }
        String text = sb.toString();
        if (text.isBlank()) {
            return null;
        }
        // 截断过长内容，仅返回摘要部分（前 800 字符）
        return text.length() > 800 ? text.substring(0, 800) + "..." : text;
    }
}
