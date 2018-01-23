package com.inesv.ecchain.kernel.deploy;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.FoundryMachine;
import com.inesv.ecchain.kernel.core.H2;
import com.inesv.ecchain.kernel.http.API;
import com.inesv.ecchain.kernel.peer.Peers;
import org.springframework.beans.factory.annotation.Autowired;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

public class EcDesktopSystemTray {
@Autowired
    private static RuntimeEnvironment runtimeEnvironment;
    public static final int DELAY = 1000;
    private final JFrame ecwrapper = new JFrame();
    private final DateFormat ecdateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.getDefault());
    private SystemTray tray;
    private JDialog ecstatusDialog;
    private JPanel ecstatusPanel;
    private ImageIcon ecimageIcon;
    private TrayIcon ectrayIcon;
    private MenuItem ecopenWalletInBrowser;
    private MenuItem ecviewLog;
    private SystemTrayDataProvider ecdataProvider;

    public static String humanReadableByteCount(long bytes) {
        int unit = 1000;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "" + ("KMGTPE").charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static String getEcProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        if (runtimeName == null) {
            return "";
        }
        String[] tokens = runtimeName.split("@");
        if (tokens.length == 2) {
            return tokens[0];
        }
        return "";
    }

    void getGUI() {
        if (!SystemTray.isSupported()) {
            LoggerUtil.logInfo("SystemTray is not supported");
            return;
        }
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        final PopupMenu popup = new PopupMenu();
        ecimageIcon = new ImageIcon("html/www/img/ec-icon-32x32.png", "tray icon");
        ectrayIcon = new TrayIcon(ecimageIcon.getImage());
        ectrayIcon.setImageAutoSize(true);
        tray = SystemTray.getSystemTray();

        MenuItem shutdown = new MenuItem("Shutdown");
        ecopenWalletInBrowser = new MenuItem("Open Wallet in Browser");
        if (!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            ecopenWalletInBrowser.setEnabled(false);
        }
        MenuItem showDesktopApplication = new MenuItem("Show Desktop Application");
        MenuItem refreshDesktopApplication = new MenuItem("Refresh Wallet");
        if (!(RuntimeEnvironment.ecisDesktopApplicationEnabled() && PropertiesUtil.getKeyForBoolean("ec.launchDesktopApplication"))) {
            showDesktopApplication.setEnabled(false);
            refreshDesktopApplication.setEnabled(false);
        }
        ecviewLog = new MenuItem("View Log File");
        if (!Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            ecviewLog.setEnabled(false);
        }
        MenuItem status = new MenuItem("Status");

        popup.add(status);
        popup.add(ecviewLog);
        popup.addSeparator();
        popup.add(ecopenWalletInBrowser);
        popup.add(showDesktopApplication);
        popup.add(refreshDesktopApplication);
        popup.addSeparator();
        popup.add(shutdown);
        ectrayIcon.setPopupMenu(popup);
        ectrayIcon.setToolTip("Initializing");
        try {
            tray.add(ectrayIcon);
        } catch (AWTException e) {
            LoggerUtil.logError("TrayIcon could not be added", e);
            return;
        }

        ectrayIcon.addActionListener(e -> displayEcStatus());

        ecopenWalletInBrowser.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(ecdataProvider.getEcWallet());
            } catch (IOException ex) {
                LoggerUtil.logError("Cannot open wallet in browser", ex);
            }
        });

        showDesktopApplication.addActionListener(e -> {
            try {
                Class.forName("ecdesktop.DesktopApplication").getMethod("launch").invoke(null);
            } catch (ReflectiveOperationException exception) {
                LoggerUtil.logError("ecdesktop.DesktopApplication failed to launch", exception);
            }
        });

        refreshDesktopApplication.addActionListener(e -> {
            try {
                Class.forName("ecdesktop.DesktopApplication").getMethod("refresh").invoke(null);
            } catch (ReflectiveOperationException exception) {
                LoggerUtil.logError("ecdesktop.DesktopApplication failed to refresh", exception);
            }
        });

        ecviewLog.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(ecdataProvider.getEcLogFile());
            } catch (IOException ex) {
                LoggerUtil.logError("Cannot view log", ex);
            }
        });

        status.addActionListener(e -> displayEcStatus());

        shutdown.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(null,
                    "Sure you want to shutdown EC?\n\nIf you do, this will stop forging, shufflers and account monitors.\n\n",
                    "Shutdown",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                LoggerUtil.logInfo("Shutdown requested by System Tray");
                System.exit(0); // Implicitly invokes shutdown using the shutdown hook
            }
        });

        ActionListener statusUpdater = evt -> {
            if (ecstatusDialog == null || !ecstatusDialog.isVisible()) {
                return;
            }
            displayEcStatus();
        };
        new Timer(DELAY, statusUpdater).start();
    }

    private void displayEcStatus() {
        EcBlock lastEcBlock = EcBlockchainImpl.getInstance().getLastECBlock();
        Collection<FoundryMachine> allFoundryMachines = FoundryMachine.getAllFoundryMachines();

        StringBuilder generators = new StringBuilder();
        for (FoundryMachine foundryMachine : allFoundryMachines) {
            generators.append(Convert.rsAccount(foundryMachine.getAccountId())).append(' ');
        }
        Object optionPaneBackground = UIManager.get("OptionPane.background");
        UIManager.put("OptionPane.background", Color.WHITE);
        Object panelBackground = UIManager.get("Panel.background");
        UIManager.put("Panel.background", Color.WHITE);
        Object textFieldBackground = UIManager.get("TextField.background");
        UIManager.put("TextField.background", Color.WHITE);
        Container statusPanelParent = null;
        if (ecstatusDialog != null && ecstatusPanel != null) {
            statusPanelParent = ecstatusPanel.getParent();
            statusPanelParent.remove(ecstatusPanel);
        }
        ecstatusPanel = new JPanel();
        ecstatusPanel.setLayout(new BoxLayout(ecstatusPanel, BoxLayout.Y_AXIS));

        putLabelRow(ecstatusPanel, "Installation");
        putDataRow(ecstatusPanel, "Application", Constants.EC_APPLICATION);
        putDataRow(ecstatusPanel, "Version", Constants.EC_VERSION);
        putDataRow(ecstatusPanel, "Network", "MainNet");
        putDataRow(ecstatusPanel, "Working offline", "" + Constants.IS_OFFLINE);
        putDataRow(ecstatusPanel, "Wallet", String.valueOf(API.getWelcomeecpageuri()));
        putDataRow(ecstatusPanel, "Peer port", String.valueOf(Peers.getDefaultPeerPort()));
        putDataRow(ecstatusPanel, "Program folder", String.valueOf(Paths.get(".").toAbsolutePath().getParent()));
        putDataRow(ecstatusPanel, "User folder", String.valueOf(Paths.get(runtimeEnvironment.getDirProvider().getEcUserHomeDir()).toAbsolutePath()));
        putDataRow(ecstatusPanel, "Database URL", H2.H2 == null ? "unavailable" : H2.H2.geth2Url());
        putEmptyRow(ecstatusPanel);

        if (lastEcBlock != null) {
            putLabelRow(ecstatusPanel, "Last EcBlock");
            putDataRow(ecstatusPanel, "Height", String.valueOf(lastEcBlock.getHeight()));
            putDataRow(ecstatusPanel, "Timestamp", String.valueOf(lastEcBlock.getTimestamp()));
            putDataRow(ecstatusPanel, "EcTime", String.valueOf(new Date(Convert.fromepochtime(lastEcBlock.getTimestamp()))));
            putDataRow(ecstatusPanel, "Seconds passed", String.valueOf(new EcTime.EpochEcTime().getTime() - lastEcBlock.getTimestamp()));
            putDataRow(ecstatusPanel, "Forging", String.valueOf(allFoundryMachines.size() > 0));
            if (allFoundryMachines.size() > 0) {
                putDataRow(ecstatusPanel, "Forging accounts", generators.toString());
            }
        }

        putEmptyRow(ecstatusPanel);
        putLabelRow(ecstatusPanel, "Environment");
        putDataRow(ecstatusPanel, "Number of peers", String.valueOf(Peers.getAllPeers().size()));
        putDataRow(ecstatusPanel, "Available processors", String.valueOf(Runtime.getRuntime().availableProcessors()));
        putDataRow(ecstatusPanel, "Max memory", humanReadableByteCount(Runtime.getRuntime().maxMemory()));
        putDataRow(ecstatusPanel, "Total memory", humanReadableByteCount(Runtime.getRuntime().totalMemory()));
        putDataRow(ecstatusPanel, "Free memory", humanReadableByteCount(Runtime.getRuntime().freeMemory()));
        putDataRow(ecstatusPanel, "Process id", getEcProcessId());
        putEmptyRow(ecstatusPanel);
        putDataRow(ecstatusPanel, "Updated", ecdateFormat.format(new Date()));
        if (ecstatusDialog == null || !ecstatusDialog.isVisible()) {
            JOptionPane pane = new JOptionPane(ecstatusPanel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, ecimageIcon);
            ecstatusDialog = pane.createDialog(ecwrapper, "EC Server Status");
            ecstatusDialog.setVisible(true);
            ecstatusDialog.dispose();
        } else {
            if (statusPanelParent != null) {
                statusPanelParent.add(ecstatusPanel);
                statusPanelParent.revalidate();
            }
            ecstatusDialog.getContentPane().validate();
            ecstatusDialog.getContentPane().repaint();
            EventQueue.invokeLater(ecstatusDialog::toFront);
        }
        UIManager.put("OptionPane.background", optionPaneBackground);
        UIManager.put("Panel.background", panelBackground);
        UIManager.put("TextField.background", textFieldBackground);
    }

    private void putDataRow(JPanel parent, String text, String value) {
        JPanel rowPanel = new JPanel();
        if (!"".equals(value)) {
            rowPanel.add(Box.createRigidArea(new Dimension(20, 0)));
        }
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
        if (!"".equals(text) && !"".equals(value)) {
            text += ':';
        }
        JLabel textLabel = new JLabel(text);
        // textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD));
        rowPanel.add(textLabel);
        rowPanel.add(Box.createRigidArea(new Dimension(140 - textLabel.getPreferredSize().width, 0)));
        JTextField valueField = new JTextField(value);
        valueField.setEditable(false);
        valueField.setBorder(BorderFactory.createEmptyBorder());
        rowPanel.add(valueField);
        rowPanel.add(Box.createRigidArea(new Dimension(4, 0)));
        parent.add(rowPanel);
        parent.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    private void putLabelRow(JPanel parent, String text) {
        putDataRow(parent, text, "");
    }

    private void putEmptyRow(JPanel parent) {
        putLabelRow(parent, "");
    }

    void shutdown() {
        SwingUtilities.invokeLater(() -> tray.remove(ectrayIcon));
    }

    void alert(String message) {
        JOptionPane.showMessageDialog(null, message, "Initialization Error", JOptionPane.ERROR_MESSAGE);
    }
}
