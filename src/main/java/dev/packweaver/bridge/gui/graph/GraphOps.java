package dev.packweaver.bridge.gui.graph;

import dev.packweaver.bridge.pack.BlockNode;

import java.util.List;

/**
 * 蓝图（节点图）编辑的树操作（Blender 式锚点交互的数据层）。
 * 数据源仍是积木树；「连线」= 从旧位置摘下子树再插入新位置。
 */
public final class GraphOps {

    /** 在事件树中递归查找 node 所在的兄弟列表。 */
    public static List<BlockNode> findList(List<BlockNode> stack, BlockNode node) {
        if (stack.contains(node)) {
            return stack;
        }
        for (BlockNode n : stack) {
            List<BlockNode> found = findList(n.children, node);
            if (found != null) {
                return found;
            }
            found = findList(n.elseChildren, node);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 把 node（连同子树）从树中摘下。 */
    public static boolean detach(List<BlockNode> roots, BlockNode node) {
        List<BlockNode> list = findList(roots, node);
        if (list != null) {
            list.remove(node);
            return true;
        }
        for (BlockNode n : roots) {
            if (detach(n.children, node) || detach(n.elseChildren, node)) {
                return true;
            }
        }
        return false;
    }

    /** ancestor 是否为 node 自身或其祖先（防自连成环）。 */
    public static boolean isAncestor(BlockNode ancestor, BlockNode node) {
        if (ancestor == node) {
            return true;
        }
        for (BlockNode c : ancestor.children) {
            if (isAncestor(c, node)) {
                return true;
            }
        }
        for (BlockNode c : ancestor.elseChildren) {
            if (isAncestor(c, node)) {
                return true;
            }
        }
        return false;
    }

    /** 把 node 插到 ref 之后（同层）。 */
    public static boolean insertAfter(List<BlockNode> roots, BlockNode ref, BlockNode node) {
        List<BlockNode> list = findList(roots, ref);
        if (list == null) {
            return false;
        }
        list.add(list.indexOf(ref) + 1, node);
        return true;
    }

    /** 删除节点：容器连同子树删除；普通节点只删自身。 */
    public static void delete(List<BlockNode> roots, BlockNode node) {
        List<BlockNode> list = findList(roots, node);
        if (list == null) {
            return;
        }
        if (!node.children.isEmpty() || !node.elseChildren.isEmpty()) {
            // 容器：连同子树整体删除
            list.remove(node);
        } else {
            list.remove(node);
        }
    }

    private GraphOps() {
    }
}
