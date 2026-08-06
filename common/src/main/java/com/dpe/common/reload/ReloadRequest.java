package com.dpe.common.reload;

/**
 * 重载请求，不可变。playerId 为发起玩家标识，namespace 为要重载的数据包命名空间。
 */
public record ReloadRequest(String playerId, String namespace) {
}
