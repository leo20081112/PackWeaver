package com.dpe.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourceLocation 单元测试。
 */
class ResourceLocationTest {

    @Test
    void parseWithNamespace() {
        ResourceLocation rl = ResourceLocation.parse("myns:foo/bar");
        assertEquals("myns", rl.namespace());
        assertEquals("foo/bar", rl.path());
        assertEquals("myns:foo/bar", rl.toString());
    }

    @Test
    void parseDefaultsToMinecraft() {
        ResourceLocation rl = ResourceLocation.parse("stone");
        assertEquals("minecraft", rl.namespace());
        assertEquals("stone", rl.path());
        assertEquals("minecraft:stone", rl.toString());
    }

    @Test
    void parseEmptyNamespaceDefaultsToMinecraft() {
        ResourceLocation rl = ResourceLocation.parse(":foo");
        assertEquals("minecraft", rl.namespace());
        assertEquals("foo", rl.path());
    }

    @Test
    void constructorRejectsInvalidNamespace() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceLocation("BadNS", "path"));
        assertThrows(IllegalArgumentException.class, () -> new ResourceLocation("has space", "path"));
        assertThrows(IllegalArgumentException.class, () -> new ResourceLocation("", "path"));
    }

    @Test
    void constructorRejectsInvalidPath() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceLocation("ns", "BadPath"));
        assertThrows(IllegalArgumentException.class, () -> new ResourceLocation("ns", "has space"));
        assertThrows(IllegalArgumentException.class, () -> new ResourceLocation("ns", ""));
    }

    @Test
    void allowsUnderscoreDotDashInNamespace() {
        ResourceLocation rl = new ResourceLocation("my_ns.1-2", "a/b.c_d-e");
        assertEquals("my_ns.1-2", rl.namespace());
        assertEquals("a/b.c_d-e", rl.path());
    }

    @Test
    void parseRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> ResourceLocation.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ResourceLocation.parse(null));
    }

    @Test
    void tryParseReturnsNullOnInvalid() {
        assertNull(ResourceLocation.tryParse("BadNS:path"));
        assertNotNull(ResourceLocation.tryParse("ns:path"));
    }

    @Test
    void toStringIsNamespaceColonPath() {
        assertEquals("minecraft:dirt", new ResourceLocation("minecraft", "dirt").toString());
    }
}
