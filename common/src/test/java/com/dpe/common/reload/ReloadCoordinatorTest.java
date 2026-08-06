package com.dpe.common.reload;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReloadCoordinator 单元测试。
 */
class ReloadCoordinatorTest {

    @Test
    void singleplayerRoutesToLocalWriteAndReload() {
        assertEquals(ReloadAction.LOCAL_WRITE_AND_RELOAD,
                ReloadCoordinator.decide(ReloadEnvironment.SINGLEPLAYER));
    }

    @Test
    void dedicatedWithPluginRoutesToSendToServer() {
        assertEquals(ReloadAction.SEND_TO_SERVER,
                ReloadCoordinator.decide(ReloadEnvironment.DEDICATED_WITH_PLUGIN));
    }

    @Test
    void dedicatedNoPluginRoutesToDeny() {
        assertEquals(ReloadAction.DENY_WITH_MESSAGE,
                ReloadCoordinator.decide(ReloadEnvironment.DEDICATED_NO_PLUGIN));
    }

    @Test
    void nullEnvironmentRoutesToDeny() {
        assertEquals(ReloadAction.DENY_WITH_MESSAGE, ReloadCoordinator.decide(null));
    }

    @Test
    void denyMessageContainsChinese() {
        String msg = ReloadCoordinator.denyMessage();
        assertNotNull(msg);
        assertFalse(msg.isBlank());
        assertTrue(Pattern.compile("[\\u4e00-\\u9fff]").matcher(msg).find(),
                "denyMessage 应含中文: " + msg);
        assertTrue(msg.contains("PackWeaver"), "denyMessage 应提及 PackWeaver 插件: " + msg);
    }
}
