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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

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
    private static final String BUTTON_SCAN = "\u624b\u52a8\u626b\u63cf\u8fd1\u5207\u7247";
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
        this.lanEndpointValue = statValue("\u6682\u65e0\u53ef\u7528\u5730\u5740");
        this.watchStatusValue = statValue("\u672a\u542f\u7528");
        this.pendingDraftsValue = statValue("--");
        this.warningsValue = statValue("--");
        this.lastSyncValue = statValue("\u5c1a\u672a\u540c\u6b65");
        this.pairingQrLabel = new JLabel();
        this.pairingQrHint = new JLabel("\u542f\u52a8\u670d\u52a1\u540e\u4f1a\u5728\u8fd9\u91cc\u751f\u6210\u624b\u673a\u626b\u7801\u914d\u5bf9\u4e8c\u7ef4\u7801");
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
            "\u684c\u9762\u7aef\u8d1f\u8d23\u76d1\u542c Bambu \u5207\u7247\u7f13\u5b58\uff0c\u81ea\u52a8\u6355\u83b7 G-code\uff0c\u63d0\u4f9b\u5185\u7f6e\u540c\u6b65\u670d\u52a1\uff0c\u5e76\u4f18\u5148\u652f\u6301 Tailscale \u8fde\u63a5\u3002"
        );
        subtitle.setForeground(MUTED);
        subtitle.setFont(uiFont(Font.PLAIN, 13));

        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(8));
        titleBlock.add(subtitle);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        stats.setOpaque(false);
        stats.add(statCard("\u670d\u52a1\u72b6\u6001", serviceStatusValue));
        stats.add(statCard("\u4e3b\u63a8\u8350\u5730\u5740", endpointValue));
        stats.add(statCard("\u5c40\u57df\u7f51\u5907\u7528\u5730\u5740", lanEndpointValue));
        stats.add(statCard("G-code \u76d1\u542c", watchStatusValue));
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
        panel.add(sectionHint("\u542f\u52a8\u670d\u52a1\u540e\u4f1a\u81ea\u52a8\u76d1\u542c Bambu Studio \u7f13\u5b58\u76ee\u5f55\uff0c\u53d1\u73b0\u65b0\u5207\u7247\u540e\u4f1a\u5148\u7b49\u6587\u4ef6\u5199\u5165\u7a33\u5b9a\uff0c\u518d\u81ea\u52a8\u8f6c\u5165\u8349\u7a3f\u3002\u624b\u52a8\u626b\u63cf\u53ea\u7528\u4e8e\u5149\u542c\u7f51\u6355\u5076\u53d1\u6f0f\u6355\u65f6\u7684\u5160\u5e95\u3002"));
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
        actions.add(textButton("\u590d\u5236\u63a8\u8350\u5730\u5740", event -> copyRecommendedEndpoint()));
        actions.add(textButton("\u5237\u65b0\u63a8\u8350\u5730\u5740", event -> refreshRecommendedEndpoint()));
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
        panel.add(sectionHint("\u5efa\u8bae\u6d41\u7a0b\uff1a\u5148\u542f\u52a8\u670d\u52a1\uff0c\u4fdd\u6301 Bambu Studio \u8fd0\u884c\uff0c\u5b8c\u6210\u5207\u7247\u540e\u7531\u684c\u9762\u7aef\u81ea\u52a8\u6355\u83b7\u5e76\u5165\u8349\u7a3f\uff0c\u624b\u673a\u7aef\u518d\u62c9\u53d6\u3001\u786e\u8ba4\u8017\u6750\u3002"));
        panel.add(Box.createVerticalStrut(8));
        panel.add(sectionHint("\u5f53\u524d GUI \u5df2\u5185\u7f6e HTTP \u540c\u6b65\u670d\u52a1\uff0c\u5e95\u5c42\u4ecd\u4f7f\u7528 PowerShell \u540c\u6b65\u5f15\u64ce\u751f\u6210\u8349\u7a3f\u548c\u72b6\u6001\u3002"));
        return panel;
    }

    private JPanel buildPreviewPanel() {
        JPanel panel = cardPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(sectionTitle("\u626b\u7801\u914d\u5bf9"));
        panel.add(sectionHint("\u624b\u673a\u7aef\u70b9\u51fb\u201c\u626b\u7801\u914d\u5bf9\u201d\u540e\uff0c\u76f4\u63a5\u626b\u63cf\u8fd9\u4e2a\u4e8c\u7ef4\u7801\u5373\u53ef\u5199\u5165\u540c\u6b65\u5730\u5740\u3002"));
        panel.add(Box.createVerticalStrut(12));
        panel.add(buildPairingQrPanel());
        panel.add(Box.createVerticalStrut(16));
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
        appendLog("[\u76d1\u542c] \u624b\u52a8\u89e6\u53d1\u8fd1\u671f\u5207\u7247\u626b\u63cf...");
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
                    appendLog("\u624b\u52a8\u626b\u63cf\u5b8c\u6210\uff0c\u9000\u51fa\u7801 " + exitCode + "\u3002");
                } catch (Exception exception) {
                    appendLog("\u624b\u52a8\u626b\u63cf\u5931\u8d25\uff1a" + exception.getMessage());
                    showError(exception.getMessage());
                }
                refreshSnapshot();
            }
        }.execute();
    }

    private void startService() {
        if (embeddedSyncService != null && embeddedSyncService.isRunning()) {
            appendLog("\u540c\u6b65\u670d\u52a1\u5df2\u5728\u8fd0\u884c\u3002");
            return;
        }
        try {
            embeddedSyncService = new EmbeddedSyncService(this::currentServiceConfig, line -> appendLog("[\u670d\u52a1] " + line));
            serviceStatusValue.setText(STATUS_STARTING);
            serviceStatusValue.setForeground(WARN);
            watchStatusValue.setText("\u521d\u59cb\u5316\u4e2d");
            watchStatusValue.setForeground(WARN);
            appendLog("\u684c\u9762\u540c\u6b65\u670d\u52a1\u542f\u52a8\u4e2d...");
            embeddedSyncService.start();
            logRecommendedEndpoints();
            serviceStatusValue.setText(STATUS_RUNNING);
            serviceStatusValue.setForeground(SUCCESS);
            refreshWatchStatus();
        } catch (IOException exception) {
            appendLog("\u542f\u52a8\u684c\u9762\u540c\u6b65\u670d\u52a1\u5931\u8d25\uff1a" + exception.getMessage());
            serviceStatusValue.setText(STATUS_ERROR);
            serviceStatusValue.setForeground(ERROR);
            watchStatusValue.setText("\u542f\u52a8\u5931\u8d25");
            watchStatusValue.setForeground(ERROR);
            embeddedSyncService = null;
            showError(exception.getMessage());
        }
    }

    private void stopService() {
        if (embeddedSyncService == null || !embeddedSyncService.isRunning()) {
            appendLog("\u540c\u6b65\u670d\u52a1\u672a\u5728\u8fd0\u884c\u3002");
            return;
        }
        appendLog("\u6b63\u5728\u505c\u6b62\u684c\u9762\u540c\u6b65\u670d\u52a1...");
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
            watchStatusValue.setText("\u672a\u542f\u7528");
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
            showError("\u65e0\u6cd5\u6253\u5f00\u76ee\u5f55\uff1a" + exception.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, APP_TITLE, JOptionPane.ERROR_MESSAGE);
    }

    private void copyRecommendedEndpoint() {
        EndpointSelection selection = resolveEndpointSelection();
        EndpointCandidate candidate = selection.primary();
        if (candidate == null) {
            showError("\u5f53\u524d\u672a\u627e\u5230\u53ef\u590d\u5236\u7684\u63a8\u8350\u5730\u5740\u3002");
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(candidate.url()), null);
            updateEndpointDisplay(selection);
            appendLog("\u5df2\u590d\u5236" + candidate.label() + "\uff1a" + candidate.url());
        } catch (Exception exception) {
            showError("\u590d\u5236\u5730\u5740\u5931\u8d25\uff1a" + exception.getMessage());
        }
    }

    private void refreshRecommendedEndpoint() {
        EndpointSelection selection = resolveEndpointSelection();
        updateEndpointDisplay(selection);
        refreshPairingQr(selection);
        logRecommendedEndpoints(selection);
        if (selection.primary() != null) {
            appendLog("\u63a8\u8350\u5730\u5740\u5df2\u5237\u65b0\uff1a" + selection.primary().url());
        }
    }

    private void updateEndpointDisplay(EndpointSelection selection) {
        if (selection == null || selection.primary() == null) {
            endpointValue.setText("\u6682\u65e0\u53ef\u7528\u5730\u5740");
            lanEndpointValue.setText("\u6682\u65e0\u53ef\u7528\u5730\u5740");
            return;
        }
        endpointValue.setText(formatEndpointLabel(selection.primary()));
        if (selection.lan() != null) {
            lanEndpointValue.setText(formatEndpointLabel(selection.lan()));
        } else {
            lanEndpointValue.setText("<html><div style='color:#9EADBF;line-height:1.3;'>\u6682\u65e0\u53ef\u7528\u7684\u5c40\u57df\u7f51\u5907\u7528\u5730\u5740</div></html>");
        }
    }

    private String formatEndpointLabel(EndpointCandidate candidate) {
        boolean serviceRunning = embeddedSyncService != null && embeddedSyncService.isRunning();
        String reachText = serviceRunning
            ? (candidate.reachable() ? "\u5df2\u901a\u8fc7\u68c0\u6d4b\u5b9e\u73b0\u53ef\u8fde\u63a5" : "\u6682\u672a\u7ecf\u8fc7\u8fde\u63a5\u68c0\u6d4b")
            : "\u7b49\u5f85\u670d\u52a1\u5f00\u542f";
        return "<html><div style='line-height:1.3;max-width:260px;'><span style='font-size:11px;color:#9EADBF;'>" + candidate.label() + " · " + reachText + "</span><br><span style='font-size:13px;'>" + candidate.url() + "</span></div></html>";
    }

    private void refreshPairingQr(EndpointSelection selection) {
        if (embeddedSyncService == null || !embeddedSyncService.isRunning()) {
            pairingQrLabel.setIcon(null);
            pairingQrLabel.setText("\u8bf7\u5148\u542f\u52a8\u670d\u52a1");
            pairingQrHint.setText("\u542f\u52a8\u670d\u52a1\u540e\uff0c\u4f1a\u81ea\u52a8\u751f\u6210\u5f53\u524d\u63a8\u8350\u5730\u5740\u7684\u4e8c\u7ef4\u7801");
            return;
        }

        if (selection == null || selection.primary() == null) {
            pairingQrLabel.setIcon(null);
            pairingQrLabel.setText("\u672a\u627e\u5230\u53ef\u914d\u5bf9\u5730\u5740");
            pairingQrHint.setText("\u8bf7\u7a0d\u540e\u5237\u65b0\uff0c\u6216\u6539\u7528\u624b\u52a8\u8f93\u5165\u540c\u6b65\u5730\u5740");
            return;
        }

        EndpointCandidate candidate = selection.primary();
        try {
            pairingQrLabel.setIcon(new javax.swing.ImageIcon(generateQrCodeImage(candidate.url(), 208)));
            pairingQrLabel.setText("");
            if (selection.lan() != null) {
                pairingQrHint.setText(
                    "\u5f53\u524d\u4e8c\u7ef4\u7801\u5185\u5bb9\uff08\u4e3b\u63a8\u8350\uff09\uff1a" +
                    candidate.url() +
                    "\u3002\u540c\u4e00 Wi\u2011Fi \u5907\u7528\uff1a" +
                    selection.lan().url()
                );
            } else {
                pairingQrHint.setText("\u5f53\u524d\u4e8c\u7ef4\u7801\u5185\u5bb9\uff08\u4e3b\u63a8\u8350\uff09\uff1a" + candidate.url());
            }
        } catch (WriterException exception) {
            pairingQrLabel.setIcon(null);
            pairingQrLabel.setText("\u4e8c\u7ef4\u7801\u751f\u6210\u5931\u8d25");
            pairingQrHint.setText("\u8bf7\u5148\u590d\u5236\u63a8\u8350\u5730\u5740\u624b\u52a8\u7c98\u8d34\u5230\u624b\u673a\uff1a" + candidate.url());
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
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void shutdown() {
        if (embeddedSyncService != null && embeddedSyncService.isRunning()) {
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
                        label = "\u63a8\u8350\u4f7f\u7528\uff08Tailscale/MagicDNS\uff09";
                    } else if (kind == EndpointKind.LAN) {
                        label = "\u5c40\u57df\u7f51\u517c\u5bb9\u5730\u5740";
                    } else {
                        label = "\u5176\u4ed6\u53ef\u7528\u7f51\u7edc\u5730\u5740";
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
        return List.of(new EndpointCandidate("\u5f53\u524d\u53ef\u7528\u5730\u5740", "http://127.0.0.1:" + safeText(portField.getText(), "8823"), 0, EndpointKind.OTHER, false));
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
            appendLog("\u672a\u627e\u5230\u53ef\u7528\u5730\u5740\uff0c\u8bf7\u7a0d\u540e\u5237\u65b0\u3002");
            return;
        }
        EndpointCandidate primary = selection.primary();
        appendLog("\u4e3b\u63a8\u8350\uff1a" + primary.label() + "\uff1a" + primary.url());
        if (selection.lan() != null) {
            EndpointCandidate lan = selection.lan();
            appendLog("\u5c40\u57df\u7f51\u5907\u7528\uff1a" + lan.label() + "\uff1a" + lan.url());
        } else {
            appendLog("\u5c40\u57df\u7f51\u5907\u7528\uff1a\u6682\u65e0\u53ef\u7528\u5730\u5740");
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
