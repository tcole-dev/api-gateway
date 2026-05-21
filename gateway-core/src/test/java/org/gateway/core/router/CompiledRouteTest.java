package org.gateway.core.router;

import static org.junit.jupiter.api.Assertions.*;

import org.gateway.common.model.RouteDefinition;
import org.junit.jupiter.api.Test;

class CompiledRouteTest {

    private CompiledRoute compile(String path) {
        RouteDefinition def = new RouteDefinition();
        def.setPath(path);
        def.setRouteId("test");
        return CompiledRoute.compile(def);
    }

    // ==================== 精确匹配 ====================

    @Test
    void exactMatch() {
        CompiledRoute route = compile("/api/user");
        assertTrue(route.match(split("/api/user")));
        assertFalse(route.match(split("/api/order")));
        assertFalse(route.match(split("/api/user/123")));
    }

    @Test
    void rootPath() {
        CompiledRoute route = compile("/");
        assertTrue(route.match(split("/")));
        assertFalse(route.match(split("/api")));
    }

    // ==================== 单段通配 * ====================

    @Test
    void singleWildcard() {
        CompiledRoute route = compile("/api/*/profile");
        assertTrue(route.match(split("/api/user/profile")));
        assertTrue(route.match(split("/api/order/profile")));
        assertFalse(route.match(split("/api/profile")));          // 少一段
        assertFalse(route.match(split("/api/user/123/profile"))); // 多一段
        assertFalse(route.match(split("/other/user/profile")));   // 前缀不匹配
    }

    @Test
    void singleWildcardAtEnd() {
        CompiledRoute route = compile("/api/user/*");
        assertTrue(route.match(split("/api/user/123")));
        assertTrue(route.match(split("/api/user/abc")));
        assertFalse(route.match(split("/api/user")));
        assertFalse(route.match(split("/api/user/123/456")));
    }

    // ==================== 末尾 ** ====================

    @Test
    void multiWildcardAtEnd() {
        CompiledRoute route = compile("/api/user/**");
        assertTrue(route.match(split("/api/user")));
        assertTrue(route.match(split("/api/user/123")));
        assertTrue(route.match(split("/api/user/123/456")));
        assertFalse(route.match(split("/api/order")));
        assertFalse(route.match(split("/api")));
    }

    @Test
    void multiWildcardOnly() {
        CompiledRoute route = compile("/**");
        assertTrue(route.match(split("/")));
        assertTrue(route.match(split("/api")));
        assertTrue(route.match(split("/api/user/123")));
    }

    // ==================== 中间 ** ====================

    @Test
    void multiWildcardInMiddle() {
        CompiledRoute route = compile("/api/**/detail");
        assertTrue(route.match(split("/api/detail")));             // ** 匹配 0 段
        assertTrue(route.match(split("/api/user/detail")));        // ** 匹配 1 段
        assertTrue(route.match(split("/api/user/123/detail")));    // ** 匹配 2 段
        assertTrue(route.match(split("/api/a/b/c/detail")));       // ** 匹配 3 段
        assertFalse(route.match(split("/api/user")));              // 缺 detail
        assertFalse(route.match(split("/api/detail/extra")));      // detail 后多了段
        assertFalse(route.match(split("/other/detail")));          // 前缀不匹配
    }

    @Test
    void multiWildcardInMiddleWithPrefix() {
        CompiledRoute route = compile("/api/**/v2/users");
        assertTrue(route.match(split("/api/v2/users")));           // ** 匹配 0 段
        assertTrue(route.match(split("/api/service/v2/users")));   // ** 匹配 1 段
        assertTrue(route.match(split("/api/a/b/v2/users")));       // ** 匹配 2 段
        assertFalse(route.match(split("/api/v2/orders")));         // 后缀不匹配
        assertFalse(route.match(split("/api/v2/users/extra")));    // 多了段
    }

    // ==================== * 与 ** 混合 ====================

    @Test
    void mixedWildcards() {
        CompiledRoute route = compile("/api/*/logs/**");
        assertTrue(route.match(split("/api/user/logs")));
        assertTrue(route.match(split("/api/user/logs/2024")));
        assertTrue(route.match(split("/api/user/logs/2024/01")));
        assertTrue(route.match(split("/api/order/logs/error")));
        assertFalse(route.match(split("/api/user/orders")));
        assertFalse(route.match(split("/api/logs/2024")));
    }

    // ==================== 编译 ====================

    @Test
    void compileSegments() {
        CompiledRoute route = compile("/api/user/123");
        CompiledRoute.Segment[] segs = route.getSegments();
        assertEquals(4, segs.length);
        assertEquals(CompiledRoute.SegmentType.LITERAL, segs[0].getType());
        assertEquals("", segs[0].getLiteral());
        assertEquals(CompiledRoute.SegmentType.LITERAL, segs[1].getType());
        assertEquals("api", segs[1].getLiteral());
        assertEquals(CompiledRoute.SegmentType.LITERAL, segs[2].getType());
        assertEquals("user", segs[2].getLiteral());
        assertEquals(CompiledRoute.SegmentType.LITERAL, segs[3].getType());
        assertEquals("123", segs[3].getLiteral());
    }

    @Test
    void compileWithWildcards() {
        CompiledRoute route = compile("/api/*/detail/**");
        CompiledRoute.Segment[] segs = route.getSegments();
        assertEquals(5, segs.length);
        assertEquals(CompiledRoute.SegmentType.LITERAL, segs[1].getType());
        assertEquals(CompiledRoute.SegmentType.SINGLE, segs[2].getType());
        assertEquals(CompiledRoute.SegmentType.LITERAL, segs[3].getType());
        assertEquals(CompiledRoute.SegmentType.MULTI, segs[4].getType());
    }

    // ==================== 边界 ====================

    @Test
    void emptyPath() {
        CompiledRoute route = compile("");
        assertTrue(route.match(split("")));
        assertFalse(route.match(split("/")));
    }

    @Test
    void pathSegmentCountMismatch() {
        CompiledRoute route = compile("/a/b/c");
        assertFalse(route.match(split("/a/b")));
        assertFalse(route.match(split("/a/b/c/d")));
    }

    private String[] split(String path) {
        return path.split("/");
    }
}
