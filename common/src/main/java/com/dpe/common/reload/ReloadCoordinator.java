package com.dpe.common.reload;

/**
 * 重载协调器：根据运行环境决定重载动作。
 */
public final class ReloadCoordinator {

    private ReloadCoordinator() {
    }

    /**
     * 根据环境决定重载动作。
     * SINGLEPLAYER → LOCAL_WRITE_AND_RELOAD；
     * DEDICATED_WITH_PLUGIN → SEND_TO_SERVER；
     * DEDICATED_NO_PLUGIN → DENY_WITH_MESSAGE。
     */
    public static ReloadAction decide(ReloadEnvironment env) {
        if (env == null) {
            return ReloadAction.DENY_WITH_MESSAGE;
        }
        return switch (env) {
            case SINGLEPLAYER -> ReloadAction.LOCAL_WRITE_AND_RELOAD;
            case DEDICATED_WITH_PLUGIN -> ReloadAction.SEND_TO_SERVER;
            case DEDICATED_NO_PLUGIN -> ReloadAction.DENY_WITH_MESSAGE;
        };
    }

    /** 无插件时拒绝重载的提示文案（含中文）。 */
    public static String denyMessage() {
        return "服务端未安装 PackWeaver 插件，无法远程重载";
    }
}
