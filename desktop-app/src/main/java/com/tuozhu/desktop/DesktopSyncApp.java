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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public final class DesktopSyncApp extends JFrame {
    // Linear-inspired dark palette: charcoal base, neutral grays, warm accents
    private static final Color PAGE = new Color(0x0D0D0D);
    private static final Color PANEL = new Color(0x1A1A1A);
    private static final Color PANEL_SOFT = new Color(0x222222);
    private static final Color PANEL_SOFT_ALT = new Color(0x2A2A2A);
    private static final Color LINE = new Color(0x333333);
    private static final Color TEXT = new Color(0xEDEDED);
    private static final Color MUTED = new Color(0x888888);
    private static final Color ACCENT = new Color(0xE8792B);
    private static final Color SUCCESS = new Color(0x3CB371);
    private static final Color WARN = new Color(0xD4A028);
    private static final Color ERROR = new Color(0xE5484D);
    private static final Color BUTTON_SECONDARY = new Color(0x262626);
    private static final Color BUTTON_SECONDARY_BORDER = new Color(0x3A3A3A);

    private static final String APP_TITLE = "拓竹桌面同步";
    private static final String STATUS_STOPPED = "已停止";
    private static final String STATUS_STARTING = "启动中";
    private static final String STATUS_RUNNING = "运行中";
    private static final String STATUS_STOPPING = "停止中";
    private static final String STATUS_ERROR = "错误";
    private static final String BUTTON_SCAN = "手动扫描近切片";
    private static final String BUTTON_SCANNING = "扫描中...";
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
    private final JLabel lanEndpointValue;
    private final JLabel watchStatusValue;
    private final JLabel pendingDraftsValue;
    private final JLabel warningsValue;
    private final JLabel lastSyncValue;
    private final JLabel pairingQrLabel;
    private final JLabel pairingQrHint;
    private final JTextArea previewArea;
    private final JTextArea warningsArea;
    private final JTextArea logArea;
    private final JButton scanButton;
    private final JButton startServiceButton;
    private final JButton stopServiceButton;
    private final JRadioButton sampleModeButton;
    private final JRadioButton bambuModeButton;

    private boolean logPanelCollapsed = false;
    private JButton logToggleButton;
    private JCheckBox autoScrollCheckbox;
    private JScrollPane previewScrollPane;
    private JScrollPane warningsScrollPane;
    private JScrollPane logScrollPane;
    private JPanel logContentPanel;

    private volatile EmbeddedSyncService embeddedSyncService;
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
        this.lanEndpointValue = statValue("暂无可用地址");
        this.watchStatusValue = statValue("未启用");
        this.pendingDraftsValue = statValue("--");
        this.warningsValue = statValue("--");
        this.lastSyncValue = statValue("尚未同步");
        this.pairingQrLabel = new JLabel();
        this.pairingQrHint = new JLabel("启动服务后会在这里生成手机扫码配对二维码");
        this.previewArea = buildReadOnlyArea();
        this.warningsArea = buildReadOnlyArea();
        this.logArea = buildReadOnlyArea();
        this.scanButton = actionButton(BUTTON_SCAN);
        this.startServiceButton = actionButton("启动服务");
        this.stopServiceButton = actionButton("停止服务");
        this.sampleModeButton = modeButton("示例任务");
        this.bambuModeButton = modeButton("真实切片 G-code");

        tonePrimaryButton(scanButton, ACCENT);
        tonePrimaryButton(startServiceButton, SUCCESS);
        tonePrimaryButton(stopServiceButton, ERROR);

        setTitle(APP_TITLE);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(960, 680));
        setLocationByPlatform(true);
        setContentPane(buildContent());
        installActions();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustResponsiveSizes();
            }
        });
        SwingUtilities.invokeLater(this::adjustResponsiveSizes);

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

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.setOpaque(false);
        northContainer.add(buildHeader(), BorderLayout.CENTER);
        JSeparator headerSeparator = new JSeparator();
        headerSeparator.setForeground(LINE);
        headerSeparator.setBackground(LINE);
        northContainer.add(headerSeparator, BorderLayout.SOUTH);
        root.add(northContainer, BorderLayout.NORTH);

        root.add(buildMainSplit(), BorderLayout.CENTER);
        root.add(buildSouthPanel(), BorderLayout.SOUTH);
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
            "桌面端负责监听 Bambu 切片缓存，自动捕获 G-code，提供内置同步服务，并优先支持 Tailscale 连接。"
        );
        subtitle.setForeground(MUTED);
        subtitle.setFont(uiFont(Font.PLAIN, 13));

        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(8));
        titleBlock.add(subtitle);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        stats.setOpaque(false);
        stats.add(statCard("服务状态", serviceStatusValue));
        stats.add(statCard("主推荐地址", endpointValue));
        stats.add(statCard("局域网备用地址", lanEndpointValue));
        stats.add(statCard("G-code 监听", watchStatusValue));
        stats.add(statCard("待确认数", pendingDraftsValue));
        stats.add(statCard("警告数", warningsValue));
        stats.add(statCard("最近同步", lastSyncValue));

        panel.add(titleBlock, BorderLayout.WEST);
        panel.add(stats, BorderLayout.EAST);
        return panel;
    }

    private JSplitPane buildMainSplit() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildControlTabs(), buildPreviewPanel());
        splitPane.setResizeWeight(0.43d);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setBackground(PAGE);
        splitPane.setDividerSize(10);
        splitPane.setContinuousLayout(true);
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

    private JTabbedPane buildControlTabs() {
        JTabbedPane tabs = new JTabbedPane(SwingConstants.TOP);
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI());
        tabs.setBackground(PANEL);
        tabs.setForeground(TEXT);
        tabs.setFont(uiFont(Font.BOLD, 13));
        tabs.setBorder(null);

        tabs.addTab("服务", buildServiceTab());
        tabs.addTab("路径", buildPathsTab());
        tabs.addTab("帮助", buildHelpTab());

        tabs.setForegroundAt(0, TEXT);
        tabs.setBackgroundAt(0, PANEL_SOFT);
        tabs.setForegroundAt(1, TEXT);
        tabs.setBackgroundAt(1, PANEL_SOFT);
        tabs.setForegroundAt(2, TEXT);
        tabs.setBackgroundAt(2, PANEL_SOFT);

        return tabs;
    }

    private JPanel buildServiceTab() {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        panel.add(sectionTitle("同步控制"));
        panel.add(sectionHint(
            "启动服务后会自动监听 Bambu Studio 缓存目录，发现新切片后会先等文件写入稳定，再自动转入草稿。手动扫描只用于光听网捕偶发漏捕时的兠底。"
        ));
        panel.add(Box.createVerticalStrut(16));
        panel.add(modeSelector());
        panel.add(Box.createVerticalStrut(12));
        panel.add(twoFieldRow("端口", portField, "最大文件天数", maxAgeField));
        panel.add(Box.createVerticalStrut(18));

        JPanel actionRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionRow1.setOpaque(false);
        scanButton.setMinimumSize(new Dimension(110, 32));
        startServiceButton.setMinimumSize(new Dimension(110, 32));
        stopServiceButton.setMinimumSize(new Dimension(110, 32));
        actionRow1.add(scanButton);
        actionRow1.add(startServiceButton);
        actionRow1.add(stopServiceButton);
        panel.add(actionRow1);
        panel.add(Box.createVerticalStrut(10));

        JPanel actionRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionRow2.setOpaque(false);
        JButton copyBtn = textButton("复制推荐地址", event -> copyRecommendedEndpoint());
        JButton refreshBtn = textButton("刷新推荐地址", event -> refreshRecommendedEndpoint());
        JButton outboxBtn = textButton("打开输出目录", event -> openPath(agentRoot().resolve("outbox")));
        JButton stateBtn = textButton("打开状态目录", event -> openPath(agentRoot().resolve("state")));
        Dimension minBtnSize = new Dimension(130, 32);
        copyBtn.setMinimumSize(minBtnSize);
        refreshBtn.setMinimumSize(minBtnSize);
        outboxBtn.setMinimumSize(minBtnSize);
        stateBtn.setMinimumSize(minBtnSize);
        actionRow2.add(copyBtn);
        actionRow2.add(refreshBtn);
        actionRow2.add(outboxBtn);
        actionRow2.add(stateBtn);
        panel.add(actionRow2);

        return panel;
    }

    private JPanel buildPathsTab() {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        panel.add(sectionTitle("路径配置"));
        panel.add(Box.createVerticalStrut(12));
        panel.add(formRow("桌面同步目录", agentRootField,
            textButton("浏览", event -> chooseDirectory(agentRootField))));
        panel.add(Box.createVerticalStrut(12));
        panel.add(multilineRow("G-code 搜索目录", gcodeRootsArea,
            textButton("添加目录", event -> appendSearchRoot())));

        return panel;
    }

    private JPanel buildHelpTab() {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        panel.add(sectionTitle("使用提示"));
        panel.add(Box.createVerticalStrut(12));
        panel.add(sectionHint(
            "建议流程：先启动服务，保持 Bambu Studio 运行，完成切片后由桌面端自动捕获并入草稿，手机端再拉取、确认耗材。"
        ));
        panel.add(Box.createVerticalStrut(8));
        panel.add(sectionHint(
            "当前 GUI 已内置 HTTP 同步服务，底层仍使用 PowerShell 同步引擎生成草稿和状态。"
        ));

        return panel;
    }

    private JPanel buildPreviewPanel() {
        JPanel panel = cardPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(sectionTitle("扫码配对"));
        panel.add(sectionHint(
            "手机端点击“扫码配对”后，直接扫描这个二维码即可写入同步地址。"
        ));
        panel.add(Box.createVerticalStrut(12));
        panel.add(buildPairingQrPanel());
        panel.add(Box.createVerticalStrut(16));
        panel.add(sectionTitle("待确认预览"));
        panel.add(sectionHint(
            "右侧会直接展示 outbox 和 state 接下来会被手机端看到的内容。"
        ));
        panel.add(Box.createVerticalStrut(12));

        previewScrollPane = new JScrollPane(previewArea);
        previewScrollPane.setBorder(BorderFactory.createLineBorder(LINE));
        previewScrollPane.getViewport().setBackground(PANEL_SOFT);
        panel.add(previewScrollPane);

        panel.add(Box.createVerticalStrut(16));
        panel.add(sectionTitle("警告与异常"));
        panel.add(Box.createVerticalStrut(8));

        warningsScrollPane = new JScrollPane(warningsArea);
        warningsScrollPane.setBorder(BorderFactory.createLineBorder(LINE));
        warningsScrollPane.getViewport().setBackground(PANEL_SOFT);
        panel.add(warningsScrollPane);

        return panel;
    }

    private JPanel buildLogPanel() {
        logContentPanel = cardPanel();
        logContentPanel.setLayout(new BorderLayout(0, 10));

        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createLineBorder(LINE));
        logScrollPane.getViewport().setBackground(PANEL_SOFT);
        logContentPanel.add(logScrollPane, BorderLayout.CENTER);

        return logContentPanel;
    }

    private JPanel buildSouthPanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);

        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controlBar.setOpaque(false);

        logToggleButton = new JButton("隐藏日志 ▲");
        styleFlatButton(logToggleButton);
        logToggleButton.setForeground(MUTED);
        logToggleButton.setBackground(PANEL);
        logToggleButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE),
            new EmptyBorder(6, 12, 6, 12)
        ));
        logToggleButton.addActionListener(e -> toggleLogPanel());

        autoScrollCheckbox = new JCheckBox("自动滚动");
        autoScrollCheckbox.setOpaque(false);
        autoScrollCheckbox.setForeground(MUTED);
        autoScrollCheckbox.setFont(uiFont(Font.PLAIN, 12));
        autoScrollCheckbox.setSelected(true);

        controlBar.add(logToggleButton);
        controlBar.add(autoScrollCheckbox);

        container.add(controlBar, BorderLayout.NORTH);
        container.add(buildLogPanel(), BorderLayout.CENTER);

        return container;
    }

    private JPanel buildPairingQrPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        pairingQrLabel.setAlignmentX(LEFT_ALIGNMENT);
        pairingQrLabel.setPreferredSize(new Dimension(212, 212));
        pairingQrHint.setAlignmentX(LEFT_ALIGNMENT);
        pairingQrHint.setForeground(MUTED);
        pairingQrHint.setFont(uiFont(Font.PLAIN, 12));
        panel.add(pairingQrLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(pairingQrHint);
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
        sampleModeButton.addActionListener(event -> refreshWatchStatus());
        bambuModeButton.addActionListener(event -> refreshWatchStatus());
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
        appendLog("[监听] 手动触发近期切片扫描...");
        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                Process process = buildProcess(false).start();
                return pumpProcess(process, "[扫描]");
            }

            @Override
            protected void done() {
                setBusy(scanButton, false);
                try {
                    int exitCode = get();
                    appendLog("手动扫描完成，退出码 " + exitCode + "。");
                } catch (Exception exception) {
                    appendLog("手动扫描失败：" + exception.getMessage());
                    showError(exception.getMessage());
                }
                refreshSnapshot();
            }
        }.execute();
    }

    private void startService() {
        if (embeddedSyncService != null && embeddedSyncService.isRunning()) {
            appendLog("同步服务已在运行。");
            return;
        }
        try {
            embeddedSyncService = new EmbeddedSyncService(this::currentServiceConfig, line -> appendLog("[服务] " + line));
            serviceStatusValue.setText(STATUS_STARTING);
            serviceStatusValue.setForeground(WARN);
            watchStatusValue.setText("初始化中");
            watchStatusValue.setForeground(WARN);
            appendLog("桌面同步服务启动中...");
            embeddedSyncService.start();
            logRecommendedEndpoints();
            serviceStatusValue.setText(STATUS_RUNNING);
            serviceStatusValue.setForeground(SUCCESS);
            refreshWatchStatus();
        } catch (IOException exception) {
            appendLog("启动桌面同步服务失败：" + exception.getMessage());
            serviceStatusValue.setText(STATUS_ERROR);
            serviceStatusValue.setForeground(ERROR);
            watchStatusValue.setText("启动失败");
            watchStatusValue.setForeground(ERROR);
            embeddedSyncService = null;
            showError(exception.getMessage());
        }
    }

    private void stopService() {
        if (embeddedSyncService == null || !embeddedSyncService.isRunning()) {
            appendLog("同步服务未在运行。");
            return;
        }
        appendLog("正在停止桌面同步服务...");
        embeddedSyncService.stop();
        embeddedSyncService = null;
        serviceStatusValue.setText(STATUS_STOPPING);
        serviceStatusValue.setForeground(WARN);
        serviceStatusValue.setText(STATUS_STOPPED);
        serviceStatusValue.setForeground(MUTED);
        refreshWatchStatus();
    }

    private int pumpProcess(Process process, String prefix) throws IOException, InterruptedException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), processOutputCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLog(prefix + " " + line);
            }
        }
        return process.waitFor();
    }

    private ProcessBuilder buildProcess(boolean serverMode) {
        if (serverMode) {
            throw new IllegalArgumentException("Embedded desktop server no longer uses the PowerShell server mode.");
        }
        Path scriptPath = agentRoot().resolve("run-sync-agent.ps1");
        List<String> scriptArgs = new ArrayList<>();

        scriptArgs.add("-MaxFileAgeDays");
        scriptArgs.add(safeText(maxAgeField.getText(), "7"));

        if (sampleModeButton.isSelected()) {
            scriptArgs.add("-UseSample");
        } else {
            scriptArgs.add("-UseBambuGcode");
            List<String> roots = parseSearchRoots();
            if (!roots.isEmpty()) {
                scriptArgs.add("-GcodeSearchRoots");
                scriptArgs.addAll(roots);
            }
        }

        ProcessBuilder builder = buildPowerShellProcess(scriptPath, scriptArgs);
        builder.directory(workspaceRoot.toFile());
        builder.redirectErrorStream(true);
        return builder;
    }

    private void refreshSnapshot() {
        Snapshot snapshot = Snapshot.read(agentRoot(), safeText(portField.getText(), "8823"));
        pendingDraftsValue.setText(Integer.toString(snapshot.pendingDrafts));
        warningsValue.setText(Integer.toString(snapshot.warningCount));
        lastSyncValue.setText(snapshot.lastSyncLabel);
        EndpointSelection selection = resolveEndpointSelection();
        updateEndpointDisplay(selection);
        previewArea.setText(snapshot.previewText);
        warningsArea.setText(snapshot.warningText);
        refreshPairingQr(selection);
        refreshWatchStatus();
        if ((embeddedSyncService == null || !embeddedSyncService.isRunning()) && !STATUS_STOPPED.equals(serviceStatusValue.getText())) {
            serviceStatusValue.setText(STATUS_STOPPED);
            serviceStatusValue.setForeground(MUTED);
        }
    }

    private void refreshWatchStatus() {
        if (embeddedSyncService == null || !embeddedSyncService.isRunning()) {
            watchStatusValue.setText("未启用");
            watchStatusValue.setForeground(MUTED);
            return;
        }
        GcodeWatchService.WatchStatus status = embeddedSyncService.currentWatchStatus();
        watchStatusValue.setText(formatWatchStatus(status));
        watchStatusValue.setForeground(colorForWatchStatus(status.level()));
    }

    private static String formatWatchStatus(GcodeWatchService.WatchStatus status) {
        String detail = status.detail() == null ? "" : status.detail().trim();
        if (detail.isEmpty()) {
            return status.summary();
        }
        return "<html><div style='line-height:1.25;max-width:220px;'><span style='font-size:12px;'>"
            + status.summary()
            + "</span><br><span style='font-size:11px;color:#9EADBF;'>"
            + detail
            + "</span></div></html>";
    }

    private static Color colorForWatchStatus(GcodeWatchService.WatchLevel level) {
        return switch (level) {
            case IDLE -> DesktopSyncApp.MUTED;
            case INFO -> DesktopSyncApp.WARN;
            case SUCCESS -> DesktopSyncApp.SUCCESS;
            case WARNING -> DesktopSyncApp.WARN;
            case ERROR -> DesktopSyncApp.ERROR;
        };
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
            showError("无法打开目录：" + exception.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, APP_TITLE, JOptionPane.ERROR_MESSAGE);
    }

    private void copyRecommendedEndpoint() {
        EndpointSelection selection = resolveEndpointSelection();
        EndpointCandidate candidate = selection.primary();
        if (candidate == null) {
            showError("当前未找到可复制的推荐地址。");
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(candidate.url()), null);
            updateEndpointDisplay(selection);
            appendLog("已复制" + candidate.label() + "：" + candidate.url());
        } catch (Exception exception) {
            showError("复制地址失败：" + exception.getMessage());
        }
    }

    private void refreshRecommendedEndpoint() {
        EndpointSelection selection = resolveEndpointSelection();
        updateEndpointDisplay(selection);
        refreshPairingQr(selection);
        logRecommendedEndpoints(selection);
        if (selection.primary() != null) {
            appendLog("推荐地址已刷新：" + selection.primary().url());
        }
    }

    private void updateEndpointDisplay(EndpointSelection selection) {
        if (selection == null || selection.primary() == null) {
            endpointValue.setText("暂无可用地址");
            lanEndpointValue.setText("暂无可用地址");
            return;
        }
        endpointValue.setText(formatEndpointLabel(selection.primary()));
        if (selection.lan() != null) {
            lanEndpointValue.setText(formatEndpointLabel(selection.lan()));
        } else {
            lanEndpointValue.setText("<html><div style='color:#9EADBF;line-height:1.3;'>暂无可用的局域网备用地址</div></html>");
        }
    }

    private String formatEndpointLabel(EndpointCandidate candidate) {
        boolean serviceRunning = embeddedSyncService != null && embeddedSyncService.isRunning();
        String reachText = serviceRunning
            ? (candidate.reachable() ? "已通过检测实现可连接" : "暂未经过连接检测")
            : "等待服务开启";
        return "<html><div style='line-height:1.3;max-width:260px;'><span style='font-size:11px;color:#9EADBF;'>" + candidate.label() + " · " + reachText + "</span><br><span style='font-size:13px;'>" + candidate.url() + "</span></div></html>";
    }

    private void refreshPairingQr(EndpointSelection selection) {
        if (embeddedSyncService == null || !embeddedSyncService.isRunning()) {
            pairingQrLabel.setIcon(null);
            pairingQrLabel.setText("请先启动服务");
            pairingQrHint.setText("启动服务后，会自动生成当前推荐地址的二维码");
            return;
        }

        if (selection == null || selection.primary() == null) {
            pairingQrLabel.setIcon(null);
            pairingQrLabel.setText("未找到可配对地址");
            pairingQrHint.setText("请稍后刷新，或改用手动输入同步地址");
            return;
        }

        EndpointCandidate candidate = selection.primary();
        try {
            pairingQrLabel.setIcon(new javax.swing.ImageIcon(generateQrCodeImage(candidate.url(), 208)));
            pairingQrLabel.setText("");
            if (selection.lan() != null) {
                pairingQrHint.setText(
                    "当前二维码内容（主推荐）：" +
                    candidate.url() +
                    "。同一 Wi‑Fi 备用：" +
                    selection.lan().url()
                );
            } else {
                pairingQrHint.setText("当前二维码内容（主推荐）：" + candidate.url());
            }
        } catch (WriterException exception) {
            pairingQrLabel.setIcon(null);
            pairingQrLabel.setText("二维码生成失败");
            pairingQrHint.setText("请先复制推荐地址手动粘贴到手机：" + candidate.url());
        }
    }

    private static BufferedImage generateQrCodeImage(String content, int size) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new java.util.Date());
            logArea.append("[" + timestamp + "] " + line + System.lineSeparator());
            if (autoScrollCheckbox == null || autoScrollCheckbox.isSelected()) {
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    private void adjustResponsiveSizes() {
        int frameHeight = getHeight();
        if (frameHeight <= 0) {
            return;
        }

        int previewHeight = Math.max(100, (int) (frameHeight * 0.35));
        int warningsHeight = Math.max(60, (int) (frameHeight * 0.25));
        int logHeight = logPanelCollapsed ? 0 : Math.max(60, (int) (frameHeight * 0.20));

        if (previewScrollPane != null) {
            previewScrollPane.setPreferredSize(new Dimension(previewScrollPane.getWidth(), previewHeight));
        }
        if (warningsScrollPane != null) {
            warningsScrollPane.setPreferredSize(new Dimension(warningsScrollPane.getWidth(), warningsHeight));
        }
        if (logScrollPane != null) {
            logScrollPane.setPreferredSize(new Dimension(logScrollPane.getWidth(), logHeight));
        }
        if (logContentPanel != null) {
            logContentPanel.setVisible(!logPanelCollapsed);
        }

        revalidate();
        repaint();
    }

    private void toggleLogPanel() {
        logPanelCollapsed = !logPanelCollapsed;
        logToggleButton.setText(logPanelCollapsed ? "显示日志 ▼" : "隐藏日志 ▲");
        adjustResponsiveSizes();
    }

    private void shutdown() {
        if (embeddedSyncService != null && embeddedSyncService.isRunning()) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "桌面同步服务仍在运行，是否先停止服务再关闭应用？",
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
        EndpointSelection selection = resolveEndpointSelection();
        EndpointCandidate primary = selection.primary();
        if (primary != null) {
            return primary.url();
        }
        return "http://127.0.0.1:" + safeText(portField.getText(), "8823");
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

    static String resolvePowerShellExecutable() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null) {
            Path path = Paths.get(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.exists(path)) {
                return path.toString();
            }
        }
        return "powershell.exe";
    }

    static ProcessBuilder buildPowerShellProcess(Path scriptPath, List<String> scriptArgs) {
        List<String> command = new ArrayList<>();
        command.add(resolvePowerShellExecutable());
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-Command");
        command.add(buildPowerShellUtf8Command(scriptPath, scriptArgs));
        return new ProcessBuilder(command);
    }

    private static String buildPowerShellUtf8Command(Path scriptPath, List<String> scriptArgs) {
        StringBuilder command = new StringBuilder();
        command.append("$utf8 = [System.Text.UTF8Encoding]::new($false);");
        command.append("[Console]::InputEncoding = $utf8;");
        command.append("[Console]::OutputEncoding = $utf8;");
        command.append("$OutputEncoding = $utf8;");
        command.append("& ");
        command.append(toPowerShellLiteral(scriptPath.toAbsolutePath().normalize().toString()));
        for (String argument : scriptArgs) {
            command.append(' ');
            command.append(toPowerShellArgument(argument));
        }
        return command.toString();
    }

    private static String toPowerShellLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String toPowerShellArgument(String value) {
        if (value.matches("-[A-Za-z][A-Za-z0-9]*")) {
            return value;
        }
        return toPowerShellLiteral(value);
    }

    static Charset processOutputCharset() {
        return StandardCharsets.UTF_8;
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

    private List<EndpointCandidate> resolveEndpointCandidates() {
        try {
            List<EndpointCandidate> candidates = new ArrayList<>();
            String port = safeText(portField.getText(), "8823");
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
                    String ip = address.getHostAddress();
                    int score = scoreInterface(network, address);
                    EndpointKind kind = determineEndpointKind(network, address);
                    String label;
                    if (kind == EndpointKind.TAILSCALE) {
                        label = "推荐使用（Tailscale/MagicDNS）";
                    } else if (kind == EndpointKind.LAN) {
                        label = "局域网兼容地址";
                    } else {
                        label = "其他可用网络地址";
                    }
                    candidates.add(new EndpointCandidate(label, "http://" + ip + ":" + port, score, kind, false));
                }
            }
            candidates.sort(Comparator.comparingInt(EndpointCandidate::score).reversed());
            if (!candidates.isEmpty()) {
                return populateReachability(deduplicateEndpoints(candidates));
            }
        } catch (Exception ignored) {
        }
        return List.of(new EndpointCandidate("当前可用地址", "http://127.0.0.1:" + safeText(portField.getText(), "8823"), 0, EndpointKind.OTHER, false));
    }

    private static int scoreInterface(NetworkInterface network, InetAddress address) throws Exception {
        String name = (network.getName() + " " + network.getDisplayName()).toLowerCase(Locale.ROOT);
        String ip = address.getHostAddress();
        int score = 0;

        if (isTailscaleAddress(network, ip)) {
            score += 500;
        }
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

    private static boolean isTailscaleAddress(NetworkInterface network, String ip) {
        String name = (network.getName() + " " + network.getDisplayName()).toLowerCase(Locale.ROOT);
        if (name.contains("tailscale")) {
            return true;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 100 && second >= 64 && second <= 127;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private List<EndpointCandidate> deduplicateEndpoints(List<EndpointCandidate> candidates) {
        List<EndpointCandidate> deduplicated = new ArrayList<>();
        for (EndpointCandidate candidate : candidates) {
            boolean exists = deduplicated.stream().anyMatch(item -> item.url().equals(candidate.url()));
            if (!exists) {
                deduplicated.add(candidate);
            }
        }
        return deduplicated;
    }

    private List<EndpointCandidate> populateReachability(List<EndpointCandidate> candidates) {
        boolean serviceRunning = embeddedSyncService != null && embeddedSyncService.isRunning();
        List<EndpointCandidate> result = new ArrayList<>();
        for (EndpointCandidate candidate : candidates) {
            boolean reachable = serviceRunning && isEndpointReachable(candidate.url());
            result.add(new EndpointCandidate(candidate.label(), candidate.url(), candidate.score(), candidate.kind(), reachable));
        }
        return result;
    }

    private EndpointSelection resolveEndpointSelection() {
        List<EndpointCandidate> candidates = resolveEndpointCandidates();
        EndpointCandidate primary = selectPrimaryCandidate(candidates);
        EndpointCandidate lan = selectLanCandidate(candidates, primary);
        return new EndpointSelection(primary, lan);
    }

    private EndpointCandidate selectPrimaryCandidate(List<EndpointCandidate> candidates) {
        return candidates.stream()
            .max(Comparator.comparingInt(this::primaryPriority))
            .orElse(null);
    }

    private int primaryPriority(EndpointCandidate candidate) {
        int kindScore = candidate.kind() == EndpointKind.TAILSCALE ? 1000 : candidate.kind() == EndpointKind.LAN ? 500 : 0;
        int reachBonus = candidate.reachable() ? 200 : 0;
        return kindScore + reachBonus + candidate.score();
    }

    private EndpointCandidate selectLanCandidate(List<EndpointCandidate> candidates, EndpointCandidate primary) {
        return candidates.stream()
            .filter(candidate -> candidate.kind() == EndpointKind.LAN && !candidate.equals(primary))
            .max(Comparator.comparingInt(this::lanPriority))
            .orElse(null);
    }

    private int lanPriority(EndpointCandidate candidate) {
        int reachBonus = candidate.reachable() ? 1000 : 0;
        return reachBonus + candidate.score();
    }

    private EndpointKind determineEndpointKind(NetworkInterface network, InetAddress address) {
        if (isTailscaleAddress(network, address.getHostAddress())) {
            return EndpointKind.TAILSCALE;
        }
        return address.isSiteLocalAddress() ? EndpointKind.LAN : EndpointKind.OTHER;
    }

    private boolean isEndpointReachable(String baseUrl) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(baseUrl + "/health").openConnection();
            connection.setConnectTimeout(300);
            connection.setReadTimeout(300);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode > 299) {
                connection.disconnect();
                return false;
            }
            String body = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            connection.disconnect();
            return body.contains("\"status\":\"ok\"") && body.contains("\"source\":\"DESKTOP_AGENT\"");
        } catch (Exception exception) {
            return false;
        }
    }

    private void logRecommendedEndpoints(EndpointSelection selection) {
        if (selection == null || selection.primary() == null) {
            appendLog("未找到可用地址，请稍后刷新。");
            return;
        }
        EndpointCandidate primary = selection.primary();
        appendLog("主推荐：" + primary.label() + "：" + primary.url());
        if (selection.lan() != null) {
            EndpointCandidate lan = selection.lan();
            appendLog("局域网备用：" + lan.label() + "：" + lan.url());
        } else {
            appendLog("局域网备用：暂无可用地址");
        }
    }

    private void logRecommendedEndpoints() {
        logRecommendedEndpoints(resolveEndpointSelection());
    }

    private EmbeddedSyncService.Config currentServiceConfig() {
        return new EmbeddedSyncService.Config(
            agentRoot(),
            Integer.parseInt(safeText(portField.getText(), "8823")),
            Integer.parseInt(safeText(maxAgeField.getText(), "7")),
            sampleModeButton.isSelected(),
            parseSearchRoots()
        );
    }

    private record EndpointCandidate(String label, String url, int score, EndpointKind kind, boolean reachable) {
    }

    private enum EndpointKind {
        TAILSCALE,
        LAN,
        OTHER
    }

    private record EndpointSelection(EndpointCandidate primary, EndpointCandidate lan) {
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
        button.setRolloverEnabled(true);
        button.setMargin(new Insets(9, 16, 9, 16));
        addHoverEffect(button);
    }

    private static void addHoverEffect(JButton button) {
        button.addMouseListener(new MouseAdapter() {
            private Color savedBg;

            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    savedBg = button.getBackground();
                    button.setBackground(brightenColor(savedBg, 0.15f));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (savedBg != null) {
                    button.setBackground(savedBg);
                    savedBg = null;
                }
            }
        });
    }

    private static Color brightenColor(Color color, float factor) {
        int r = Math.min(255, (int) (color.getRed() * (1 + factor)));
        int g = Math.min(255, (int) (color.getGreen() * (1 + factor)));
        int b = Math.min(255, (int) (color.getBlue() * (1 + factor)));
        return new Color(r, g, b);
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
        final String previewText;
        final String warningText;

        private Snapshot(
            int pendingDrafts,
            int warningCount,
            String lastSyncLabel,
            String previewText,
            String warningText
        ) {
            this.pendingDrafts = pendingDrafts;
            this.warningCount = warningCount;
            this.lastSyncLabel = lastSyncLabel;
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
                ? "暂无警告。"
                : String.join(System.lineSeparator(), warnings);
            Long updatedAt = extractLong(state, "updatedAt");
            String lastSync = updatedAt == null
                ? "尚未同步"
                : TIME_FORMAT.format(Instant.ofEpochMilli(updatedAt));

            return new Snapshot(
                jobs.size(),
                warnings.size(),
                lastSync,
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
                return "暂无待处理草稿。服务运行后，手机可拉取的结果会显示在这里。";
            }
            StringBuilder builder = new StringBuilder();
            for (String job : jobs) {
                builder.append("模型：")
                    .append(Objects.toString(extractString(job, "modelName"), "-"))
                    .append(System.lineSeparator());
                builder.append("用量：")
                    .append(Objects.toString(extractLong(job, "estimatedUsageGrams"), "0"))
                    .append("g")
                    .append(System.lineSeparator());
                builder.append("材料：")
                    .append(Objects.toString(extractString(job, "targetMaterial"), "未指定"))
                    .append(System.lineSeparator());
                builder.append("任务 ID：")
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
