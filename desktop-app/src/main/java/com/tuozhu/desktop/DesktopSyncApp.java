package com.tuozhu.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

public final class DesktopSyncApp extends JFrame {
    private static final Color PAGE = new Color(0x0B1118);
    private static final Color PANEL = new Color(0x131C26);
    private static final Color PANEL_SOFT = new Color(0x1A2431);
    private static final Color PANEL_SOFT_ALT = new Color(0x202C3A);
    private static final Color LINE = new Color(0x2A3644);
    private static final Color TEXT = new Color(0xF4F7FA);
    private static final Color MUTED = new Color(0x9EADBF);
    private static final Color ACCENT = new Color(0xB96223);
    private static final Color SUCCESS = new Color(0x1F8F74);
    private static final Color WARN = new Color(0xC99722);
    private static final Color ERROR = new Color(0xB54A53);
    private static final Color BUTTON_SECONDARY = new Color(0x1D2834);
    private static final Color BUTTON_SECONDARY_BORDER = new Color(0x314152);

    private static final String APP_TITLE = "\u62d3\u7af9\u684c\u9762\u540c\u6b65";
    private static final String STATUS_STOPPED = "\u5df2\u505c\u6b62";
    private static final String STATUS_STARTING = "\u542f\u52a8\u4e2d";
    private static final String STATUS_RUNNING = "\u8fd0\u884c\u4e2d";
    private static final String STATUS_STOPPING = "\u505c\u6b62\u4e2d";
    private static final String STATUS_ERROR = "\u9519\u8bef";
    private static final String BUTTON_SCAN = "\u626b\u63cf\u4e00\u6b21";
    private static final String BUTTON_SCANNING = "\u626b\u63cf\u4e2d...";
    private static final String FONT_UI = resolveFontName(
        "Microsoft YaHei UI",
        "Microsoft YaHei",
        "PingFang SC",
        "Noto Sans CJK SC",
        "Source Han Sans SC",
        "SimHei",
        "Dialog"
    );

    private final Path workspaceRoot;
    private final JTextField agentRootField;
    private final JTextArea gcodeRootsArea;
    private final JTextField portField;
    private final JTextField maxAgeField;
    private final JLabel serviceStatusValue;
    private final JLabel endpointValue;
    private final JLabel pendingDraftsValue;
    private final JLabel warningsValue;
    private final JLabel lastSyncValue;
    private final JTextArea previewArea;
    private final JTextArea warningsArea;
    private final JTextArea logArea;
    private final JButton scanButton;
    private final JButton startServiceButton;
    private final JButton stopServiceButton;
    private final JRadioButton sampleModeButton;
    private final JRadioButton bambuModeButton;

    private volatile Process serverProcess;
    private final Timer snapshotTimer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            useSystemLookAndFeel();
            DesktopSyncApp app = new DesktopSyncApp();
            app.setVisible(true);
        });
    }

    private DesktopSyncApp() {
        this.workspaceRoot = locateWorkspaceRoot();
        this.agentRootField = new JTextField(resolveDefaultAgentRoot().toString());
        this.gcodeRootsArea = new JTextArea(defaultSearchRoots());
        this.portField = new JTextField("8823");
        this.maxAgeField = new JTextField("7");
        this.serviceStatusValue = statValue(STATUS_STOPPED);
        this.endpointValue = statValue(buildEndpointHint());
        this.pendingDraftsValue = statValue("--");
        this.warningsValue = statValue("--");
        this.lastSyncValue = statValue("\u5c1a\u672a\u540c\u6b65");
        this.previewArea = buildReadOnlyArea();
        this.warningsArea = buildReadOnlyArea();
        this.logArea = buildReadOnlyArea();
        this.scanButton = actionButton(BUTTON_SCAN);
        this.startServiceButton = actionButton("\u542f\u52a8\u670d\u52a1");
        this.stopServiceButton = actionButton("\u505c\u6b62\u670d\u52a1");
        this.sampleModeButton = modeButton("\u793a\u4f8b\u4efb\u52a1");
        this.bambuModeButton = modeButton("\u771f\u5b9e\u5207\u7247 G-code");

        tonePrimaryButton(scanButton, ACCENT);
        tonePrimaryButton(startServiceButton, SUCCESS);
        tonePrimaryButton(stopServiceButton, ERROR);

        setTitle(APP_TITLE);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1280, 900));
        setLocationByPlatform(true);
        setContentPane(buildContent());
        installActions();
        refreshSnapshot();

        this.snapshotTimer = new Timer(2500, event -> refreshSnapshot());
        this.snapshotTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdown();
            }
        });
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(18, 18));
        root.setBackground(PAGE);
        root.setBorder(new EmptyBorder(22, 22, 22, 22));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildMainSplit(), BorderLayout.CENTER);
        root.add(buildLogPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(20, 12));
        panel.setOpaque(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(APP_TITLE);
        title.setForeground(TEXT);
        title.setFont(uiFont(Font.BOLD, 30));

        JLabel subtitle = new JLabel(
            "\u684c\u9762\u7aef\u7528\u4e8e\u626b\u63cf Bambu \u5207\u7247\u3001\u5f00\u542f\u5c40\u57df\u7f51\u540c\u6b65\uff0c\u5e76\u56de\u5199\u624b\u673a\u786e\u8ba4\u7ed3\u679c\u3002"
        );
        subtitle.setForeground(MUTED);
        subtitle.setFont(uiFont(Font.PLAIN, 13));

        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(8));
        titleBlock.add(subtitle);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        stats.setOpaque(false);
        stats.add(statCard("\u670d\u52a1\u72b6\u6001", serviceStatusValue));
        stats.add(statCard("\u624b\u673a\u8bbf\u95ee\u5730\u5740", endpointValue));
        stats.add(statCard("\u5f85\u786e\u8ba4\u6570", pendingDraftsValue));
        stats.add(statCard("\u8b66\u544a\u6570", warningsValue));
        stats.add(statCard("\u6700\u8fd1\u540c\u6b65", lastSyncValue));

        panel.add(titleBlock, BorderLayout.WEST);
        panel.add(stats, BorderLayout.EAST);
        return panel;
    }

    private JSplitPane buildMainSplit() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildControlPanel(), buildPreviewPanel());
        splitPane.setResizeWeight(0.43d);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setBackground(PAGE);
        splitPane.setDividerSize(8);
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this);
                divider.setBackground(PAGE);
                divider.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
                return divider;
            }
        });
        return splitPane;
    }

    private JPanel buildControlPanel() {
        JPanel panel = cardPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(sectionTitle("\u540c\u6b65\u63a7\u5236"));
        panel.add(sectionHint("\u5355\u6b21\u626b\u63cf\u9002\u5408\u7acb\u5373\u66f4\u65b0\uff0c\u6301\u7eed\u8fd0\u884c\u670d\u52a1\u9002\u5408\u624b\u673a\u7aef\u968f\u65f6\u62c9\u53d6\u3002"));
        panel.add(Box.createVerticalStrut(16));
        panel.add(formRow("\u684c\u9762\u540c\u6b65\u76ee\u5f55", agentRootField, textButton("\u6d4f\u89c8", event -> chooseDirectory(agentRootField))));
        panel.add(Box.createVerticalStrut(12));
        panel.add(modeSelector());
        panel.add(Box.createVerticalStrut(12));
        panel.add(multilineRow("G-code \u641c\u7d22\u76ee\u5f55", gcodeRootsArea, textButton("\u6dfb\u52a0\u76ee\u5f55", event -> appendSearchRoot())));
        panel.add(Box.createVerticalStrut(12));
        panel.add(twoFieldRow("\u7aef\u53e3", portField, "\u6700\u5927\u6587\u4ef6\u5929\u6570", maxAgeField));
        panel.add(Box.createVerticalStrut(18));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(scanButton);
        actions.add(startServiceButton);
        actions.add(stopServiceButton);
        actions.add(textButton("\u6253\u5f00\u8f93\u51fa\u76ee\u5f55", event -> openPath(agentRoot().resolve("outbox"))));
        actions.add(textButton("\u6253\u5f00\u72b6\u6001\u76ee\u5f55", event -> openPath(agentRoot().resolve("state"))));
        panel.add(actions);

        panel.add(Box.createVerticalStrut(16));
        JSeparator separator = new JSeparator();
        separator.setForeground(LINE);
        separator.setBackground(LINE);
        panel.add(separator);
        panel.add(Box.createVerticalStrut(16));
        panel.add(sectionTitle("\u4f7f\u7528\u63d0\u793a"));
        panel.add(sectionHint("\u5efa\u8bae\u6d41\u7a0b\uff1a\u5148\u542f\u52a8\u670d\u52a1\uff0c\u518d\u628a\u624b\u673a\u8bbf\u95ee\u5730\u5740\u586b\u5165 Android \u5e94\u7528\uff0c\u7136\u540e\u7531\u624b\u673a\u7aef\u62c9\u53d6\u5e76\u786e\u8ba4\u4efb\u52a1\u3002"));
        panel.add(Box.createVerticalStrut(8));
        panel.add(sectionHint("\u5f53\u524d GUI \u662f\u684c\u9762\u5165\u53e3\uff0c\u5e95\u5c42\u540c\u6b65\u5f15\u64ce\u4ecd\u517c\u5bb9\u73b0\u6709 JSON \u6587\u4ef6\u548c PowerShell \u811a\u672c\u3002"));
        return panel;
    }

    private JPanel buildPreviewPanel() {
        JPanel panel = cardPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(sectionTitle("\u5f85\u786e\u8ba4\u9884\u89c8"));
        panel.add(sectionHint("\u53f3\u4fa7\u4f1a\u76f4\u63a5\u5c55\u793a outbox \u548c state \u63a5\u4e0b\u6765\u4f1a\u88ab\u624b\u673a\u7aef\u770b\u5230\u7684\u5185\u5bb9\u3002"));
        panel.add(Box.createVerticalStrut(12));
        panel.add(scrollPane(previewArea, 288));
        panel.add(Box.createVerticalStrut(16));
        panel.add(sectionTitle("\u8b66\u544a\u4e0e\u5f02\u5e38"));
        panel.add(Box.createVerticalStrut(8));
        panel.add(scrollPane(warningsArea, 200));
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = cardPanel();
        panel.setLayout(new BorderLayout(0, 10));
        panel.add(sectionTitle("\u5b9e\u65f6\u65e5\u5fd7"), BorderLayout.NORTH);
        panel.add(scrollPane(logArea, 180), BorderLayout.CENTER);
        return panel;
    }

    private void installActions() {
        ButtonGroup group = new ButtonGroup();
        group.add(sampleModeButton);
        group.add(bambuModeButton);
        bambuModeButton.setSelected(true);

        scanButton.addActionListener(event -> runOneShotSync());
        startServiceButton.addActionListener(event -> startService());
        stopServiceButton.addActionListener(event -> stopService());
    }

    private JPanel modeSelector() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        panel.setOpaque(false);
        panel.add(sampleModeButton);
        panel.add(bambuModeButton);
        return panel;
    }

    private void runOneShotSync() {
        setBusy(scanButton, true);
        appendLog("\u5f00\u59cb\u6267\u884c\u5355\u6b21\u626b\u63cf...");
        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                Process process = buildProcess(false).start();
                return pumpProcess(process, "[\u626b\u63cf]");
            }

            @Override
            protected void done() {
                setBusy(scanButton, false);
                try {
                    int exitCode = get();
                    appendLog("\u5355\u6b21\u626b\u63cf\u5b8c\u6210\uff0c\u9000\u51fa\u7801 " + exitCode + "\u3002");
                } catch (Exception exception) {
                    appendLog("\u5355\u6b21\u626b\u63cf\u5931\u8d25\uff1a" + exception.getMessage());
                    showError(exception.getMessage());
                }
                refreshSnapshot();
            }
        }.execute();
    }

    private void startService() {
        if (serverProcess != null && serverProcess.isAlive()) {
            appendLog("\u540c\u6b65\u670d\u52a1\u5df2\u5728\u8fd0\u884c\u3002");
            return;
        }
        try {
            serverProcess = buildProcess(true).start();
            serviceStatusValue.setText(STATUS_STARTING);
            serviceStatusValue.setForeground(WARN);
            appendLog("\u684c\u9762\u540c\u6b65\u670d\u52a1\u542f\u52a8\u4e2d...");
            appendLog("\u5efa\u8bae\u5728\u624b\u673a\u7aef\u586b\u5199\uff1a" + buildEndpointHint());
            Thread pumpThread = new Thread(() -> {
                try {
                    int exitCode = pumpProcess(serverProcess, "[\u670d\u52a1]");
                    SwingUtilities.invokeLater(() -> {
                        appendLog("\u684c\u9762\u540c\u6b65\u670d\u52a1\u5df2\u505c\u6b62\uff0c\u9000\u51fa\u7801 " + exitCode + "\u3002");
                        serviceStatusValue.setText(STATUS_STOPPED);
                        serviceStatusValue.setForeground(MUTED);
                        serverProcess = null;
                        refreshSnapshot();
                    });
                } catch (Exception exception) {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("\u684c\u9762\u540c\u6b65\u670d\u52a1\u5f02\u5e38\uff1a" + exception.getMessage());
                        serviceStatusValue.setText(STATUS_ERROR);
                        serviceStatusValue.setForeground(ERROR);
                        serverProcess = null;
                    });
                }
            }, "desktop-sync-service-pump");
            pumpThread.setDaemon(true);
            pumpThread.start();
            serviceStatusValue.setText(STATUS_RUNNING);
            serviceStatusValue.setForeground(SUCCESS);
        } catch (IOException exception) {
            appendLog("\u542f\u52a8\u684c\u9762\u540c\u6b65\u670d\u52a1\u5931\u8d25\uff1a" + exception.getMessage());
            serviceStatusValue.setText(STATUS_ERROR);
            serviceStatusValue.setForeground(ERROR);
            serverProcess = null;
            showError(exception.getMessage());
        }
    }

    private void stopService() {
        if (serverProcess == null || !serverProcess.isAlive()) {
            appendLog("\u540c\u6b65\u670d\u52a1\u672a\u5728\u8fd0\u884c\u3002");
            return;
        }
        appendLog("\u6b63\u5728\u505c\u6b62\u684c\u9762\u540c\u6b65\u670d\u52a1...");
        serverProcess.destroy();
        serviceStatusValue.setText(STATUS_STOPPING);
        serviceStatusValue.setForeground(WARN);
    }

    private int pumpProcess(Process process, String prefix) throws IOException, InterruptedException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLog(prefix + " " + line);
            }
        }
        return process.waitFor();
    }

    private ProcessBuilder buildProcess(boolean serverMode) {
        Path scriptPath = serverMode
            ? agentRoot().resolve("start-sync-server.ps1")
            : agentRoot().resolve("run-sync-agent.ps1");
        List<String> command = new ArrayList<>();
        command.add(resolvePowerShellExecutable());
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(scriptPath.toString());

        if (serverMode) {
            command.add("-ListenHost");
            command.add("+");
            command.add("-Port");
            command.add(safeText(portField.getText(), "8823"));
        }

        command.add("-MaxFileAgeDays");
        command.add(safeText(maxAgeField.getText(), "7"));

        if (sampleModeButton.isSelected()) {
            command.add("-UseSample");
        } else {
            command.add("-UseBambuGcode");
            List<String> roots = parseSearchRoots();
            if (!roots.isEmpty()) {
                command.add("-GcodeSearchRoots");
                command.addAll(roots);
            }
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workspaceRoot.toFile());
        builder.redirectErrorStream(true);
        return builder;
    }

    private void refreshSnapshot() {
        Snapshot snapshot = Snapshot.read(agentRoot(), safeText(portField.getText(), "8823"));
        pendingDraftsValue.setText(Integer.toString(snapshot.pendingDrafts));
        warningsValue.setText(Integer.toString(snapshot.warningCount));
        lastSyncValue.setText(snapshot.lastSyncLabel);
        endpointValue.setText(snapshot.endpointLabel);
        previewArea.setText(snapshot.previewText);
        warningsArea.setText(snapshot.warningText);
        if ((serverProcess == null || !serverProcess.isAlive()) && !STATUS_STOPPED.equals(serviceStatusValue.getText())) {
            serviceStatusValue.setText(STATUS_STOPPED);
            serviceStatusValue.setForeground(MUTED);
        }
    }

    private void chooseDirectory(JTextField targetField) {
        JFileChooser chooser = new JFileChooser(targetField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().toPath().toString());
            refreshSnapshot();
        }
    }

    private void appendSearchRoot() {
        JFileChooser chooser = new JFileChooser(agentRoot().toString());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String current = gcodeRootsArea.getText().trim();
            String next = chooser.getSelectedFile().toPath().toString();
            gcodeRootsArea.setText(current.isBlank() ? next : current + System.lineSeparator() + next);
        }
    }

    private void openPath(Path path) {
        try {
            Files.createDirectories(path);
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception exception) {
            showError("\u65e0\u6cd5\u6253\u5f00\u76ee\u5f55\uff1a" + exception.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, APP_TITLE, JOptionPane.ERROR_MESSAGE);
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new java.util.Date());
            logArea.append("[" + timestamp + "] " + line + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void shutdown() {
        if (serverProcess != null && serverProcess.isAlive()) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "\u684c\u9762\u540c\u6b65\u670d\u52a1\u4ecd\u5728\u8fd0\u884c\uff0c\u662f\u5426\u5148\u505c\u6b62\u670d\u52a1\u518d\u5173\u95ed\u5e94\u7528\uff1f",
                APP_TITLE,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
            stopService();
        }
        snapshotTimer.stop();
        dispose();
    }

    private Path agentRoot() {
        return Paths.get(safeText(agentRootField.getText(), resolveDefaultAgentRoot().toString()));
    }

    private List<String> parseSearchRoots() {
        String[] lines = gcodeRootsArea.getText().split("\\R");
        List<String> roots = new ArrayList<>();
        for (String line : lines) {
            String value = line.trim();
            if (!value.isEmpty()) {
                roots.add(value);
            }
        }
        return roots;
    }

    private static void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private static JTextArea buildReadOnlyArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(PANEL_SOFT);
        area.setForeground(TEXT);
        area.setCaretColor(TEXT);
        area.setBorder(new EmptyBorder(14, 14, 14, 14));
        area.setFont(uiFont(Font.PLAIN, 13));
        area.setOpaque(true);
        return area;
    }

    private static JScrollPane scrollPane(JTextArea area, int preferredHeight) {
        JScrollPane pane = new JScrollPane(area);
        pane.setBorder(BorderFactory.createLineBorder(LINE));
        pane.getViewport().setBackground(PANEL_SOFT);
        pane.setPreferredSize(new Dimension(480, preferredHeight));
        return pane;
    }

    private static JLabel statValue(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(uiFont(Font.BOLD, 15));
        return label;
    }

    private static JPanel statCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), new EmptyBorder(12, 14, 12, 14)));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel heading = new JLabel(title);
        heading.setForeground(MUTED);
        heading.setFont(uiFont(Font.PLAIN, 11));

        card.add(heading);
        card.add(Box.createVerticalStrut(6));
        card.add(valueLabel);
        return card;
    }

    private static JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), new EmptyBorder(20, 20, 20, 20)));
        return panel;
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(uiFont(Font.BOLD, 19));
        return label;
    }

    private static JLabel sectionHint(String text) {
        JLabel label = new JLabel("<html><div style='width:420px;color:#9EADBF;line-height:1.4;'>" + text + "</div></html>");
        label.setForeground(MUTED);
        label.setFont(uiFont(Font.PLAIN, 12));
        return label;
    }

    private JPanel formRow(String labelText, JTextField field, JButton actionButton) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 8, 0);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel label = new JLabel(labelText);
        label.setForeground(MUTED);
        label.setFont(uiFont(Font.PLAIN, 12));
        panel.add(label, gbc);

        gbc.gridy = 1;
        gbc.weightx = 1d;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        styleField(field);
        panel.add(field, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0d;
        gbc.insets = new Insets(0, 8, 0, 0);
        panel.add(actionButton, gbc);
        return panel;
    }

    private JPanel multilineRow(String labelText, JTextArea area, JButton actionButton) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setForeground(MUTED);
        label.setFont(uiFont(Font.PLAIN, 12));

        area.setRows(5);
        area.setBackground(PANEL_SOFT_ALT);
        area.setForeground(TEXT);
        area.setCaretColor(TEXT);
        area.setBorder(new EmptyBorder(12, 12, 12, 12));
        area.setFont(uiFont(Font.PLAIN, 13));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(label, BorderLayout.WEST);
        top.add(actionButton, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);
        panel.add(scrollPane(area, 132), BorderLayout.CENTER);
        return panel;
    }

    private JPanel twoFieldRow(String leftLabel, JTextField leftField, String rightLabel, JTextField rightField) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1d;
        gbc.gridx = 0;
        gbc.gridy = 0;

        JLabel left = new JLabel(leftLabel);
        left.setForeground(MUTED);
        left.setFont(uiFont(Font.PLAIN, 12));
        panel.add(left, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 0, 8, 0);
        JLabel right = new JLabel(rightLabel);
        right.setForeground(MUTED);
        right.setFont(uiFont(Font.PLAIN, 12));
        panel.add(right, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.insets = new Insets(0, 0, 0, 10);
        styleField(leftField);
        panel.add(leftField, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        styleField(rightField);
        panel.add(rightField, gbc);
        return panel;
    }

    private static JButton textButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        styleFlatButton(button);
        button.setForeground(TEXT);
        button.setBackground(BUTTON_SECONDARY);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BUTTON_SECONDARY_BORDER),
            new EmptyBorder(8, 14, 8, 14)
        ));
        return button;
    }

    private static JButton actionButton(String text) {
        JButton button = new JButton(text);
        styleFlatButton(button);
        button.setForeground(Color.WHITE);
        return button;
    }

    private static JRadioButton modeButton(String text) {
        JRadioButton button = new JRadioButton(text);
        button.setOpaque(false);
        button.setForeground(TEXT);
        button.setFont(uiFont(Font.PLAIN, 13));
        button.setFocusable(false);
        return button;
    }

    private static void styleField(JTextField field) {
        field.setBackground(PANEL_SOFT_ALT);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setFont(uiFont(Font.PLAIN, 13));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE),
            new EmptyBorder(11, 12, 11, 12)
        ));
    }

    private static void setBusy(JButton button, boolean busy) {
        button.setEnabled(!busy);
        button.setText(busy ? BUTTON_SCANNING : BUTTON_SCAN);
    }

    private static String safeText(String text, String fallback) {
        String value = text == null ? "" : text.trim();
        return value.isEmpty() ? fallback : value;
    }

    private String buildEndpointHint() {
        return "http://" + resolveLocalIp() + ":" + safeText(portField.getText(), "8823");
    }

    private String defaultSearchRoots() {
        List<String> roots = new ArrayList<>();
        String userProfile = System.getenv("USERPROFILE");
        String localAppData = System.getenv("LOCALAPPDATA");
        if (userProfile != null) {
            roots.add(Paths.get(userProfile, "Desktop").toString());
        }
        if (localAppData != null) {
            roots.add(Paths.get(localAppData, "Temp", "bamboo_model").toString());
        }
        return String.join(System.lineSeparator(), roots);
    }

    private static String resolvePowerShellExecutable() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null) {
            Path path = Paths.get(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.exists(path)) {
                return path.toString();
            }
        }
        return "powershell.exe";
    }

    private static Path locateWorkspaceRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        Path resolved = searchForWorkspace(current);
        if (resolved != null) {
            return resolved;
        }
        try {
            Path codeSource = Paths.get(DesktopSyncApp.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            resolved = searchForWorkspace(codeSource);
            if (resolved != null) {
                return resolved;
            }
        } catch (Exception ignored) {
        }
        return current;
    }

    private static Path searchForWorkspace(Path start) {
        Path cursor = start;
        for (int depth = 0; depth < 8 && cursor != null; depth++) {
            if (Files.isDirectory(cursor.resolve("desktop-agent"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private Path resolveDefaultAgentRoot() {
        return workspaceRoot.resolve("desktop-agent");
    }

    private static String resolveLocalIp() {
        try {
            List<IpCandidate> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    candidates.add(new IpCandidate(address.getHostAddress(), scoreInterface(network, address)));
                }
            }
            candidates.sort(Comparator.comparingInt(IpCandidate::score).reversed());
            if (!candidates.isEmpty()) {
                return candidates.get(0).ip();
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    private static int scoreInterface(NetworkInterface network, InetAddress address) throws Exception {
        String name = (network.getName() + " " + network.getDisplayName()).toLowerCase(Locale.ROOT);
        String ip = address.getHostAddress();
        int score = 0;

        if (address.isSiteLocalAddress()) {
            score += 200;
        }
        if (name.contains("wlan") || name.contains("wi-fi") || name.contains("wifi") || name.contains("wireless")) {
            score += 150;
        }
        if (name.contains("ethernet") || name.contains("lan")) {
            score += 120;
        }
        if (ip.startsWith("192.168.")) {
            score += 80;
        } else if (ip.startsWith("10.")) {
            score += 40;
        } else if (ip.startsWith("172.")) {
            score += 20;
        }
        if (network.isVirtual()) {
            score -= 200;
        }
        if (
            name.contains("virtual") ||
            name.contains("vmware") ||
            name.contains("hyper-v") ||
            name.contains("vethernet") ||
            name.contains("wsl") ||
            name.contains("tap") ||
            name.contains("tun") ||
            name.contains("vpn") ||
            name.contains("bluetooth") ||
            name.contains("sectap") ||
            name.contains("atrust")
        ) {
            score -= 250;
        }
        return score;
    }

    private record IpCandidate(String ip, int score) {
    }

    private static String resolveFontName(String... candidates) {
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String candidate : candidates) {
            for (String font : available) {
                if (font.equalsIgnoreCase(candidate)) {
                    return font;
                }
            }
        }
        return "Dialog";
    }

    private static Font uiFont(int style, int size) {
        return new Font(FONT_UI, style, size);
    }

    private static void styleFlatButton(JButton button) {
        button.setUI(new BasicButtonUI());
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(uiFont(Font.BOLD, 13));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setRolloverEnabled(false);
        button.setMargin(new Insets(9, 16, 9, 16));
    }

    private static void tonePrimaryButton(JButton button, Color color) {
        button.setBackground(color);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.darker()),
            new EmptyBorder(9, 16, 9, 16)
        ));
    }

    private static final class Snapshot {
        private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        private static final Pattern JOB_OBJECT =
            Pattern.compile("\\{[^{}]*\"externalJobId\"[^{}]*}", Pattern.DOTALL);

        final int pendingDrafts;
        final int warningCount;
        final String lastSyncLabel;
        final String endpointLabel;
        final String previewText;
        final String warningText;

        private Snapshot(
            int pendingDrafts,
            int warningCount,
            String lastSyncLabel,
            String endpointLabel,
            String previewText,
            String warningText
        ) {
            this.pendingDrafts = pendingDrafts;
            this.warningCount = warningCount;
            this.lastSyncLabel = lastSyncLabel;
            this.endpointLabel = endpointLabel;
            this.previewText = previewText;
            this.warningText = warningText;
        }

        static Snapshot read(Path agentRoot, String port) {
            Path outboxPath = agentRoot.resolve("outbox").resolve("desktop-outbox.json");
            Path statePath = agentRoot.resolve("state").resolve("state.json");
            String outbox = readText(outboxPath);
            String state = readText(statePath);

            List<String> jobs = extractObjects(outbox);
            List<String> warnings = extractStringArray(state, "warnings");
            String preview = buildPreview(jobs);
            String warningText = warnings.isEmpty()
                ? "\u6682\u65e0\u8b66\u544a\u3002"
                : String.join(System.lineSeparator(), warnings);
            Long updatedAt = extractLong(state, "updatedAt");
            String lastSync = updatedAt == null
                ? "\u5c1a\u672a\u540c\u6b65"
                : TIME_FORMAT.format(Instant.ofEpochMilli(updatedAt));

            return new Snapshot(
                jobs.size(),
                warnings.size(),
                lastSync,
                "http://" + resolveLocalIp() + ":" + port,
                preview,
                warningText
            );
        }

        private static String readText(Path path) {
            try {
                if (Files.exists(path)) {
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {
            }
            return "";
        }

        private static List<String> extractObjects(String json) {
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            List<String> objects = new ArrayList<>();
            Matcher matcher = JOB_OBJECT.matcher(json);
            while (matcher.find()) {
                objects.add(matcher.group());
            }
            return objects;
        }

        private static List<String> extractStringArray(String json, String fieldName) {
            String content = arrayContent(json, fieldName);
            if (content == null || content.isBlank()) {
                return Collections.emptyList();
            }
            Pattern pattern = Pattern.compile("\"((?:\\\\.|[^\"])*)\"");
            Matcher matcher = pattern.matcher(content);
            List<String> values = new ArrayList<>();
            while (matcher.find()) {
                values.add(unescape(matcher.group(1)));
            }
            return values;
        }

        private static Long extractLong(String json, String fieldName) {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?\\d+)");
            Matcher matcher = pattern.matcher(json);
            return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
        }

        private static String extractString(String json, String fieldName) {
            Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"",
                Pattern.DOTALL
            );
            Matcher matcher = pattern.matcher(json);
            return matcher.find() ? unescape(matcher.group(1)) : null;
        }

        private static String buildPreview(List<String> jobs) {
            if (jobs.isEmpty()) {
                return "\u6682\u65e0\u5f85\u5904\u7406\u8349\u7a3f\u3002\u670d\u52a1\u8fd0\u884c\u540e\uff0c\u624b\u673a\u53ef\u62c9\u53d6\u7684\u7ed3\u679c\u4f1a\u663e\u793a\u5728\u8fd9\u91cc\u3002";
            }
            StringBuilder builder = new StringBuilder();
            for (String job : jobs) {
                builder.append("\u6a21\u578b\uff1a")
                    .append(Objects.toString(extractString(job, "modelName"), "-"))
                    .append(System.lineSeparator());
                builder.append("\u7528\u91cf\uff1a")
                    .append(Objects.toString(extractLong(job, "estimatedUsageGrams"), "0"))
                    .append("g")
                    .append(System.lineSeparator());
                builder.append("\u6750\u6599\uff1a")
                    .append(Objects.toString(extractString(job, "targetMaterial"), "\u672a\u6307\u5b9a"))
                    .append(System.lineSeparator());
                builder.append("\u4efb\u52a1 ID\uff1a")
                    .append(Objects.toString(extractString(job, "externalJobId"), "-"))
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
            }
            return builder.toString().trim();
        }

        private static String arrayContent(String json, String fieldName) {
            int fieldIndex = json.indexOf("\"" + fieldName + "\"");
            if (fieldIndex < 0) {
                return null;
            }
            int start = json.indexOf('[', fieldIndex);
            if (start < 0) {
                return null;
            }
            int depth = 0;
            for (int i = start; i < json.length(); i++) {
                char current = json.charAt(i);
                if (current == '[') {
                    depth++;
                } else if (current == ']') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(start + 1, i);
                    }
                }
            }
            return null;
        }

        private static String unescape(String value) {
            StringBuilder builder = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                if (current != '\\' || i == value.length() - 1) {
                    builder.append(current);
                    continue;
                }
                char next = value.charAt(++i);
                switch (next) {
                    case '"':
                        builder.append('"');
                        break;
                    case '\\':
                        builder.append('\\');
                        break;
                    case '/':
                        builder.append('/');
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        if (i + 4 <= value.length() - 1) {
                            String hex = value.substring(i + 1, i + 5);
                            try {
                                builder.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException exception) {
                                builder.append("\\u").append(hex);
                                i += 4;
                            }
                        } else {
                            builder.append("\\u");
                        }
                        break;
                    default:
                        builder.append(next);
                        break;
                }
            }
            return builder.toString();
        }
    }
}
