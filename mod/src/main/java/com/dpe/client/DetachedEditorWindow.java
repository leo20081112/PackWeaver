package com.dpe.client;

import com.dpe.common.complete.CompletionCandidate;
import com.dpe.common.complete.CompletionContext;
import com.dpe.common.complete.CompletionService;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 脱离游戏的独立编辑器窗口（Task 5）：Swing JFrame。
 * 左侧文件树 + 右侧编辑区 + 顶部保存/关闭 + 底部状态栏。
 * Ctrl+Space 弹出补全候选（调用 {@link CompletionService}）。
 * 与游戏内编辑器通过共享 files + onSave 回调同步。
 */
public final class DetachedEditorWindow {

    /** 在 AWT 事件线程创建并显示窗口。 */
    public static void create(String namespace, Map<String, String> files, Consumer<Map<String, String>> onSave) {
        // Fabric mod 默认可能 headless，强制关闭以允许 Swing
        System.setProperty("java.awt.headless", "false");
        SwingUtilities.invokeLater(() -> {
            DetachedEditorWindow w = new DetachedEditorWindow(
                    namespace, new LinkedHashMap<>(files), onSave);
            w.show();
        });
    }

    private final JFrame frame;
    private final JTree tree;
    private final JEditorPane editor;
    private final JLabel status;
    private final Map<String, String> files;
    private final Consumer<Map<String, String>> onSave;
    private final CompletionService completionService = new CompletionService();
    private String currentPath;

    public DetachedEditorWindow(String namespace, Map<String, String> files, Consumer<Map<String, String>> onSave) {
        this.files = files;
        this.onSave = onSave;

        frame = new JFrame("PackWeaver - 独立编辑器 - " + namespace);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);

        // 文件树
        tree = new JTree(buildTree(files));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        // 编辑区
        editor = new JEditorPane();
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane editorScroll = new JScrollPane(editor);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), editorScroll);
        split.setDividerLocation(220);
        split.setResizeWeight(0.0);

        // 顶部菜单条（保存/关闭）
        JMenuBar bar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");
        JMenuItem saveItem = new JMenuItem("保存");
        JMenuItem closeItem = new JMenuItem("关闭");
        fileMenu.add(saveItem);
        fileMenu.add(closeItem);
        bar.add(fileMenu);
        frame.setJMenuBar(bar);

        // 顶部按钮条
        JPanel top = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        javax.swing.JButton saveBtn = new javax.swing.JButton("保存");
        javax.swing.JButton closeBtn = new javax.swing.JButton("关闭");
        top.add(saveBtn);
        top.add(closeBtn);
        top.setPreferredSize(new Dimension(100, 30));

        status = new JLabel("就绪");

        frame.add(top, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);

        // 事件
        tree.addTreeSelectionListener(e -> onTreeSelect());
        saveBtn.addActionListener(e -> doSave());
        closeBtn.addActionListener(e -> frame.dispose());
        saveItem.addActionListener(e -> doSave());
        closeItem.addActionListener(e -> frame.dispose());

        // 编辑即时回写内存 map
        editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateCurrent();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateCurrent();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateCurrent();
            }
        });

        // Ctrl+Space 补全
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_SPACE) {
                    e.consume();
                    showCompletion();
                }
            }
        });
    }

    public void show() {
        frame.setVisible(true);
    }

    private void onTreeSelect() {
        Object node = tree.getLastSelectedPathComponent();
        if (!(node instanceof DefaultMutableTreeNode dmtn)) {
            return;
        }
        String path = pathOf(dmtn);
        if (path == null || !files.containsKey(path)) {
            return;
        }
        currentPath = path;
        editor.setText(files.get(path));
        editor.setCaretPosition(0);
        status.setText("已打开: " + path);
    }

    private void updateCurrent() {
        if (currentPath != null) {
            files.put(currentPath, editor.getText());
        }
    }

    private void doSave() {
        updateCurrent();
        if (onSave != null) {
            onSave.accept(files);
        }
        status.setText("已保存 (" + files.size() + " 个文件)");
    }

    /** 弹出补全候选菜单。 */
    private void showCompletion() {
        try {
            String text = editor.getText();
            int cursor = Math.min(editor.getCaretPosition(), text.length());
            int ls = lineStart(text, cursor);
            int le = cursor;
            while (le < text.length() && text.charAt(le) != '\n') {
                le++;
            }
            String line = text.substring(ls, le);
            int lineCursor = cursor - ls;
            String kind = (currentPath != null && currentPath.endsWith(".json")) ? "text_component" : "function";
            CompletionContext ctx = new CompletionContext(line, lineCursor, "dpe", null, kind);
            java.util.List<CompletionCandidate> cands = completionService.complete(ctx);
            if (cands.isEmpty()) {
                status.setText("无补全候选");
                return;
            }
            JPopupMenu popup = new JPopupMenu();
            for (CompletionCandidate c : cands) {
                String label = c.label() + (c.detail() == null ? "" : "  " + c.detail());
                JMenuItem item = new JMenuItem(label);
                item.addActionListener(e -> {
                    String ins = c.insertText() == null ? c.label() : c.insertText();
                    editor.replaceSelection(ins);
                });
                popup.add(item);
            }
            Point p;
            try {
                Rectangle r = editor.modelToView(editor.getCaretPosition());
                p = new Point(r.x, r.y + r.height);
            } catch (Exception ex) {
                p = new Point(0, 0);
            }
            popup.show(editor, p.x, p.y);
            status.setText("补全: " + cands.size() + " 项");
        } catch (Exception e) {
            status.setText("补全失败: " + e.getMessage());
        }
    }

    private static int lineStart(String text, int cursor) {
        int i = Math.min(cursor, text.length()) - 1;
        while (i >= 0 && text.charAt(i) != '\n') {
            i--;
        }
        return i + 1;
    }

    // ---------- 文件树构建 ----------

    private static final class NodeData {
        final String name;
        final String fullPath; // 叶子节点=文件路径；文件夹=null

        NodeData(String name, String fullPath) {
            this.name = name;
            this.fullPath = fullPath;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static DefaultMutableTreeNode buildTree(Map<String, String> files) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new NodeData("files", null));
        for (String path : files.keySet()) {
            String[] parts = path.split("/");
            DefaultMutableTreeNode cur = root;
            for (int i = 0; i < parts.length; i++) {
                boolean leaf = i == parts.length - 1;
                DefaultMutableTreeNode child = findChild(cur, parts[i]);
                if (child == null) {
                    child = new DefaultMutableTreeNode(new NodeData(parts[i], leaf ? path : null));
                    cur.add(child);
                }
                cur = child;
            }
        }
        return root;
    }

    private static DefaultMutableTreeNode findChild(DefaultMutableTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            Object c = parent.getChildAt(i);
            if (c instanceof DefaultMutableTreeNode dmtn) {
                Object u = dmtn.getUserObject();
                if (u instanceof NodeData nd && nd.name.equals(name)) {
                    return dmtn;
                }
            }
        }
        return null;
    }

    private static String pathOf(DefaultMutableTreeNode node) {
        Object u = node.getUserObject();
        if (u instanceof NodeData nd && nd.fullPath != null) {
            return nd.fullPath;
        }
        return null;
    }
}
