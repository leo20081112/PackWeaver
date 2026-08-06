package com.dpe.common.reload;

/**
 * 重载动作：根据环境决定的重载策略。
 */
public enum ReloadAction {
    /** 单人：本地写入数据包并触发重载。 */
    LOCAL_WRITE_AND_RELOAD,
    /** 专用服务端有插件：把数据发送给服务端处理。 */
    SEND_TO_SERVER,
    /** 专用服务端无插件：拒绝并提示用户。 */
    DENY_WITH_MESSAGE
}
