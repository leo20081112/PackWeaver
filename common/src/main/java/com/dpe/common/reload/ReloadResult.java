package com.dpe.common.reload;

/**
 * 重载结果，不可变。reloadCount 为本次实际触发的重载次数。
 */
public record ReloadResult(boolean success, String message, long reloadCount) {
}
