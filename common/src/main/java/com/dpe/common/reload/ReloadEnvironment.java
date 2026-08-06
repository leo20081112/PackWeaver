package com.dpe.common.reload;

/**
 * 重载环境：决定重载动作的路由依据。
 */
public enum ReloadEnvironment {
    /** 单人世界：客户端可直接写入并本地重载。 */
    SINGLEPLAYER,
    /** 专用服务端且已安装 PackWeaver 插件：把数据发送给服务端处理。 */
    DEDICATED_WITH_PLUGIN,
    /** 专用服务端但未安装插件：拒绝远程重载。 */
    DEDICATED_NO_PLUGIN
}
