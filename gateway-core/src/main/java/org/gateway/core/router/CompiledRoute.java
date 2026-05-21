package org.gateway.core.router;

import org.gateway.common.model.RouteDefinition;

import lombok.Data;
import lombok.Getter;

/**
 * 预编译路由：加载时将路径模式拆分为类型化段数组，匹配时纯数组操作。
 *
 * 段类型：LITERAL（精确）、SINGLE（*）、MULTI（**，任意位置）
 */
@Getter
public class CompiledRoute {

    public enum SegmentType { LITERAL, SINGLE, MULTI }

    @Data
    public static class Segment {
        private final SegmentType type;
        private final String literal;
    }

    private final RouteDefinition definition;
    private final Segment[] segments;

    public CompiledRoute(RouteDefinition definition, Segment[] segments) {
        this.definition = definition;
        this.segments = segments;
    }

    /**
     * 预编译路径模式：将路径拆为类型化段数组。
     * 例："/api/user/detail" → [LIT("api"), LIT("user"), LIT("detail")]
     *     含通配符的路径 → 对应段编译为 SINGLE 或 MULTI
     */
    public static CompiledRoute compile(RouteDefinition definition) {
        String path = definition.getPath();
        // 全通配快捷路径，无需拆分
        if (path.equals("*") || path.equals("/**")) {
            return new CompiledRoute(definition, new Segment[]{new Segment(SegmentType.MULTI, null)});
        }

        String[] parts = path.split("/");
        Segment[] segments = new Segment[parts.length];
        for (int i = 0; i < parts.length; i++) {
            segments[i] = switch (parts[i]) {
                case "*"  -> new Segment(SegmentType.SINGLE, null);  // 单段通配
                case "**" -> new Segment(SegmentType.MULTI, null);   // 多段通配
                default   -> new Segment(SegmentType.LITERAL, parts[i]); // 精确匹配
            };
        }
        return new CompiledRoute(definition, segments);
    }

    /**
     * 贪心双指针匹配，支持 ** 出现在任意位置。
     * pi 只增不减；遇到 ** 后匹配失败时，回退 si 到 ** 后一位，pi++ 让 ** 多吃一段。
     */
    public boolean match(String[] path) {
        int pi = 0;       // path 段指针，只增不减
        int si = 0;       // pattern 段指针，遇到 ** 回溯时会回退
        int starSi = -1;  // 最近一个 ** 的位置，-1 表示无 ** 可回溯

        while (pi < path.length) {
            Segment seg = si < segments.length ? segments[si] : null;

            // LITERAL 精确命中 或 SINGLE 单段通配 → 双指针同步前进
            if (seg != null && (seg.getType() == SegmentType.SINGLE
                    || (seg.getType() == SegmentType.LITERAL && seg.getLiteral().equals(path[pi])))) {
                si++;
                pi++;
            }
            // 遇到 ** → 记录位置，si 前进（先尝试让 ** 匹配 0 段）
            else if (seg != null && seg.getType() == SegmentType.MULTI) {
                starSi = si++;
            }
            // 当前段不匹配，但有 ** 可回溯 → si 回退到 ** 后一位（其实就是固定为 ** 之后1位）
            else if (starSi != -1) {
                si = starSi + 1;
                pi++;
            }
            else {
                return false;
            }
        }

        // path 耗尽后，跳过 pattern 尾部剩余的 **（理论上应该只有1段）
        while (si < segments.length && segments[si].getType() == SegmentType.MULTI) si++;
        // pattern 也耗尽才算真正匹配
        return si == segments.length;
    }
}
