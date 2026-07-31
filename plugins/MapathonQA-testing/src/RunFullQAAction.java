package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;

public class RunFullQAAction extends AbstractAction {

    private static final String TM_API = "https://tasking-manager-production-api.hotosm.org/api/v2";

    public RunFullQAAction() { super(I18n.tr("Run Full QA Check...")); }

    @Override
    public void actionPerformed(ActionEvent e) { showStep1Dialog(); }

    private void showStep1Dialog() {
        JDialog dlg = new JDialog((java.awt.Frame) null, "MapathonQA \u2013 Step 1: Report Setup", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        bindEscapeToClose(dlg);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 4, 6, 4); gc.anchor = GridBagConstraints.WEST;

        // The Name/Username and Time-Window/Role fields live in their own nested GridBagLayouts
        // (see Card A/B below), each with its own independent column-0 width. Without a shared
        // width, "Name:" (short) and "Project ID:" (in the outer layout) would each size their
        // column to their own label and no longer line up. Pre-sizing every row label to the
        // widest one keeps every text field's left edge aligned across the whole dialog.
        int labelWidth = 0;
        for (String t : new String[]{"Project ID:", "Name:", "Start (UTC):", "End (UTC):", "Username:", "Role:"})
            labelWidth = Math.max(labelWidth, new JLabel(t).getPreferredSize().width);
        final int rowLabelWidth = labelWidth;
        java.util.function.Function<String, JLabel> rowLabel = text -> {
            JLabel l = new JLabel(text);
            l.setPreferredSize(new java.awt.Dimension(rowLabelWidth, l.getPreferredSize().height));
            return l;
        };

        // \u2500\u2500 Report scope toggle: time window (mapathon) vs specific user(s) (whole project) \u2500\u2500
        // First thing in the dialog, since it decides which fields (and which Project ID
        // requirement) apply below.
        gc.gridx=0; gc.gridy=0; gc.gridwidth=2;
        main.add(new JLabel("<html><b>Report Scope</b></html>"), gc);

        JRadioButton rdoTimeWindow = new JRadioButton("Time window (mapathon)", true);
        JRadioButton rdoUser = new JRadioButton("Specific user(s) (whole project)", false);
        ButtonGroup scopeGroup = new ButtonGroup();
        scopeGroup.add(rdoTimeWindow); scopeGroup.add(rdoUser);
        JPanel scopePanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
        scopePanel.add(rdoTimeWindow); scopePanel.add(rdoUser);
        gc.gridy=1; gc.insets = new Insets(0, 4, 10, 4);
        main.add(scopePanel, gc);
        gc.insets = new Insets(6, 4, 6, 4);

        // Compute default time window: end = current UTC hour (floored), start = end - 2h
        java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String defaultEnd = sdf.format(cal.getTime());
        cal.add(java.util.Calendar.HOUR_OF_DAY, -2);
        String defaultStart = sdf.format(cal.getTime());
        String initialStart = !MapathonQAPlugin.lastStart.isEmpty() ? MapathonQAPlugin.lastStart : defaultStart;
        String initialEnd   = !MapathonQAPlugin.lastEnd.isEmpty()   ? MapathonQAPlugin.lastEnd   : defaultEnd;

        // \u2500\u2500 Card A: Mapathon Name + Project ID (time window mode) / Username(s) (user mode) -
        // same slot. Each card is its own GridBagLayout so panelA's overall size (driven by the
        // larger of the two cards) never depends on which one is showing - that's what kept
        // shifting things around when Project ID lived outside the cards.
        java.awt.CardLayout cardsA = new java.awt.CardLayout();
        JPanel panelA = new JPanel(cardsA);

        JPanel nameCard = new JPanel(new GridBagLayout());
        GridBagConstraints ngc = new GridBagConstraints();
        ngc.insets = new Insets(6, 4, 6, 4); ngc.anchor = GridBagConstraints.WEST;
        ngc.gridx=0; ngc.gridy=0; ngc.gridwidth=2;
        nameCard.add(new JLabel("<html><b>Mapathon Name</b> <small>(optional)</small></html>"), ngc);
        ngc.gridy=1; ngc.gridwidth=1;
        ngc.gridx=0; nameCard.add(rowLabel.apply("Name:"), ngc);
        JTextField mapathonNameField = new JTextField(MapathonQAPlugin.lastMapathonName, 20);
        mapathonNameField.setToolTipText("Shown on the report, e.g. \"Kathmandu University Mapathon\"");
        ngc.gridx=1; nameCard.add(mapathonNameField, ngc);
        ngc.gridy=2; ngc.gridx=0; ngc.gridwidth=2; ngc.insets = new Insets(14, 4, 2, 4);
        nameCard.add(new JLabel("<html><b>HOT Tasking Manager Project ID</b></html>"), ngc);
        ngc.gridy=3; ngc.gridwidth=1; ngc.insets = new Insets(6, 4, 6, 4);
        ngc.gridx=0; nameCard.add(rowLabel.apply("Project ID:"), ngc);
        JTextField projectIdField = new JTextField(
            MapathonQAPlugin.lastProjectId > 0 ? String.valueOf(MapathonQAPlugin.lastProjectId) : "", 10);
        projectIdField.setToolTipText("HOT Tasking Manager project number, e.g. 50430");
        ngc.gridx=1; nameCard.add(projectIdField, ngc);

        JPanel userCard = new JPanel(new GridBagLayout());
        GridBagConstraints ugc = new GridBagConstraints();
        ugc.insets = new Insets(6, 4, 6, 4); ugc.anchor = GridBagConstraints.WEST;
        ugc.gridx=0; ugc.gridy=0; ugc.gridwidth=2;
        userCard.add(new JLabel("<html><b>Report by User</b><br><small>Covers the whole project - no time window needed</small></html>"), ugc);
        ugc.gridy=1; ugc.gridwidth=1;
        ugc.gridx=0; userCard.add(rowLabel.apply("Username:"), ugc);
        JTextField userField = new JTextField(20);
        userField.setToolTipText("OSM username, e.g. qeef");
        ugc.gridx=1; userCard.add(userField, ugc);

        panelA.add(nameCard, "name");
        panelA.add(userCard, "user");

        gc.gridx=0; gc.gridy=2; gc.gridwidth=2; gc.insets = new Insets(0, 0, 0, 0);
        main.add(panelA, gc);
        gc.insets = new Insets(6, 4, 6, 4);

        // \u2500\u2500 Card B: Time Window fields + auto-load checkbox (time window mode) /
        // Role + optional Project ID + auto-load checkbox (user mode) - same slot \u2500\u2500
        java.awt.CardLayout cardsB = new java.awt.CardLayout();
        JPanel panelB = new JPanel(cardsB);

        JPanel timeCard = new JPanel(new GridBagLayout());
        GridBagConstraints tgc = new GridBagConstraints();
        tgc.insets = new Insets(6, 4, 6, 4); tgc.anchor = GridBagConstraints.WEST;
        tgc.gridx=0; tgc.gridy=0; tgc.gridwidth=2;
        JCheckBox chkLoadTime = new JCheckBox("Automatically load task grid into JOSM when closing", true);
        timeCard.add(chkLoadTime, tgc);
        tgc.gridy=1; tgc.insets = new Insets(14, 4, 2, 4);
        timeCard.add(new JLabel("<html><b>Mapathon Time Window (UTC)</b><br><small>Format: YYYY-MM-DD HH:MM</small></html>"), tgc);
        tgc.gridy=2; tgc.gridwidth=1; tgc.insets = new Insets(6, 4, 6, 4);
        tgc.gridx=0; timeCard.add(rowLabel.apply("Start (UTC):"), tgc);
        JTextField startField = new JTextField(initialStart, 16);
        tgc.gridx=1; timeCard.add(startField, tgc);
        tgc.gridy=3;
        tgc.gridx=0; timeCard.add(rowLabel.apply("End (UTC):"), tgc);
        JTextField endField = new JTextField(initialEnd, 16);
        tgc.gridx=1; timeCard.add(endField, tgc);

        JPanel roleCard = new JPanel(new GridBagLayout());
        GridBagConstraints rgc = new GridBagConstraints();
        rgc.insets = new Insets(6, 4, 6, 4); rgc.anchor = GridBagConstraints.WEST;
        rgc.gridx=0; rgc.gridy=0; rgc.gridwidth=2;
        roleCard.add(new JLabel("<html><b>Role</b><br><small>Check this user's mapped or validated tasks</small></html>"), rgc);
        JRadioButton rdoMapper = new JRadioButton("Mapper", true);
        JRadioButton rdoValidator = new JRadioButton("Validator", false);
        ButtonGroup roleGroup = new ButtonGroup();
        roleGroup.add(rdoMapper); roleGroup.add(rdoValidator);
        JPanel rolePanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
        rolePanel.add(rdoMapper); rolePanel.add(rdoValidator);
        rgc.gridy=1; rgc.gridwidth=1;
        rgc.gridx=0; roleCard.add(rowLabel.apply("Role:"), rgc);
        rgc.gridx=1; roleCard.add(rolePanel, rgc);
        rgc.gridy=2; rgc.gridx=0; rgc.gridwidth=2; rgc.insets = new Insets(14, 4, 2, 4);
        roleCard.add(new JLabel("<html><b>HOT Tasking Manager Project ID</b> <small>(optional \u2014 only needed to auto-load the task grid)</small></html>"), rgc);
        rgc.gridy=3; rgc.gridwidth=1; rgc.insets = new Insets(6, 4, 6, 4);
        rgc.gridx=0; roleCard.add(rowLabel.apply("Project ID:"), rgc);
        JTextField userProjectIdField = new JTextField(
            MapathonQAPlugin.lastProjectId > 0 ? String.valueOf(MapathonQAPlugin.lastProjectId) : "", 10);
        userProjectIdField.setToolTipText("HOT Tasking Manager project number, e.g. 50430 - optional, only needed to auto-load the task grid");
        rgc.gridx=1; roleCard.add(userProjectIdField, rgc);
        rgc.gridy=4; rgc.gridx=0; rgc.gridwidth=2; rgc.insets = new Insets(10, 4, 6, 4);
        JCheckBox chkLoadUser = new JCheckBox("Automatically load task grid into JOSM when closing", true);
        roleCard.add(chkLoadUser, rgc);

        panelB.add(timeCard, "timewindow");
        panelB.add(roleCard, "role");

        gc.gridx=0; gc.gridy=3; gc.gridwidth=2; gc.insets = new Insets(0, 0, 0, 0);
        main.add(panelB, gc);
        gc.insets = new Insets(6, 4, 6, 4);

        // Switch cards on toggle - CardLayout keeps panelA/panelB's size fixed to the larger
        // card regardless of which is showing, so nothing else in the dialog shifts around.
        java.awt.event.ActionListener scopeListener = ev -> {
            if (rdoUser.isSelected()) { cardsA.show(panelA, "user"); cardsB.show(panelB, "role"); }
            else { cardsA.show(panelA, "name"); cardsB.show(panelB, "timewindow"); }
        };
        rdoTimeWindow.addActionListener(scopeListener);
        rdoUser.addActionListener(scopeListener);

        gc.gridx=0; gc.gridy=4; gc.gridwidth=2; gc.insets = new Insets(14, 4, 6, 4);
        JCheckBox chkHistory = new JCheckBox("Include this report in MapathonQA_history.csv",
            Config.getPref().getBoolean(HistoryLogger.PREF_INCLUDE_HISTORY, false));
        chkHistory.setToolTipText("Appends one row to a persistent history CSV for tracking quality trends across mapathons over time");
        main.add(chkHistory, gc);

        JPanel btns = new JPanel();
        JButton btnFind = new JButton("Find Tasks \u2192");
        JButton btnX    = new JButton("Cancel");
        btns.add(btnFind); btns.add(btnX);

        btnX.addActionListener(ev -> dlg.dispose());
        btnFind.addActionListener(ev -> {
            if (rdoUser.isSelected()) {
                String username = userField.getText().trim();
                if (username.isEmpty()) {
                    JOptionPane.showMessageDialog(dlg, "Please enter a username.", "MapathonQA", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // Project ID is optional in this mode - only needed to auto-load the task grid.
                int uPid = parseId(userProjectIdField.getText());
                if (uPid > 0) MapathonQAPlugin.lastProjectId = uPid;
                // No mapathon name in this mode - use the searched username instead, so the
                // history CSV's "Name" column still identifies what the report covers.
                MapathonQAPlugin.lastMapathonName = username;
                // Whole-project mode has no time window - clear any leftover from a previous
                // time-window run so "Run QA on Current Layer" doesn't wrongly filter by it.
                MapathonQAPlugin.lastStart = "";
                MapathonQAPlugin.lastEnd   = "";
                Config.getPref().putBoolean(HistoryLogger.PREF_INCLUDE_HISTORY, chkHistory.isSelected());
                dlg.dispose();
                resolveUserAndContinue(uPid, username, rdoMapper.isSelected(), chkLoadUser.isSelected());
                return;
            }

            int pid = parseId(projectIdField.getText());
            if (pid < 1) { JOptionPane.showMessageDialog(dlg, "Please enter a valid project ID.", "MapathonQA", JOptionPane.ERROR_MESSAGE); return; }
            String startVal = startField.getText().trim();
            String endVal   = endField.getText().trim();
            MapathonQAPlugin.lastProjectId    = pid;
            MapathonQAPlugin.lastStart        = startVal;
            MapathonQAPlugin.lastEnd          = endVal;
            MapathonQAPlugin.lastMapathonName = mapathonNameField.getText().trim();
            // Time-window mode has no per-user tallying - clear any leftover target user from a
            // previous user-mode run so it doesn't leak into this report.
            MapathonQAPlugin.lastTargetUid = 0;
            Config.getPref().putBoolean(HistoryLogger.PREF_INCLUDE_HISTORY, chkHistory.isSelected());
            dlg.dispose();
            fetchTaskIds(pid, startVal, endVal, chkLoadTime.isSelected());
        });

        dlg.setLayout(new BorderLayout());
        dlg.add(main, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setMinimumSize(dlg.getSize());
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }

    private void fetchTaskIds(int projectId, String start, String end, boolean autoLoad) {
        JDialog prog = progressDialog("Connecting to HOT Tasking Manager...");
        JLabel statusLbl = getStatusLabel(prog);
        prog.setVisible(true);

        SwingWorker<List<Integer>, String> worker = new SwingWorker<List<Integer>, String>() {
            @Override protected List<Integer> doInBackground() throws Exception {
                publish("Fetching activity for project #" + projectId + "...");
                List<Integer> ids = fetchMappedTaskIds(projectId, start, end);
                publish("Found " + ids.size() + " task(s). Building search query...");
                return ids;
            }
            @Override protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) statusLbl.setText(chunks.get(chunks.size()-1));
            }
            @Override protected void done() {
                prog.dispose();
                try { showStep2Dialog(projectId, start, end, get(), autoLoad); }
                catch (Exception ex) { JOptionPane.showMessageDialog(null, "Failed to fetch task list:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        };
        worker.execute();
    }

    /**
     * Resolves the username to its numeric OSM user ID, then hands off to a search query on
     * mappedBy/validatedBy - those are tags already present on the task grid once it's loaded,
     * so JOSM's own search does the actual matching, same as the taskId search built for
     * time-window mode.
     */
    private void resolveUserAndContinue(int projectId, String username, boolean mapperMode, boolean autoLoad) {
        JDialog prog = progressDialog("Looking up OSM user ID...");
        JLabel statusLbl = getStatusLabel(prog);
        prog.setVisible(true);

        SwingWorker<Integer, String> worker = new SwingWorker<Integer, String>() {
            @Override protected Integer doInBackground() {
                publish("Looking up \"" + username + "\"...");
                try { return resolveOsmUserId(username); } catch (Exception ex) { return -1; }
            }
            @Override protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) statusLbl.setText(chunks.get(chunks.size() - 1));
            }
            @Override protected void done() {
                prog.dispose();
                try {
                    int uid = get();
                    if (uid <= 0) {
                        JOptionPane.showMessageDialog(null,
                            "Could not resolve \"" + username + "\" to an OSM user ID (no changesets found).",
                            "MapathonQA", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    showUserSearchDialog(projectId, uid, username, mapperMode, autoLoad);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "User lookup failed:\n" + ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * Resolves an OSM username to its numeric user ID via a public, unauthenticated changeset
     * query - OSM's API has no direct username-to-ID lookup, but every changeset response
     * includes the author's uid, so querying for just one changeset by that display name works.
     * Returns -1 if the username has never made a changeset (or doesn't exist).
     */
    private int resolveOsmUserId(String username) throws Exception {
        String encoded = URLEncoder.encode(username, "UTF-8");
        String urlStr = "https://api.openstreetmap.org/api/0.6/changesets.json?display_name=" + encoded + "&limit=1";
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "MapathonQA-JOSMPlugin/1.0");
        conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("OSM API returned HTTP " + code + " while looking up \"" + username + "\"");

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        String uidStr = extractJsonNumber(sb.toString(), "uid");
        if (uidStr == null) return -1;
        try { return Integer.parseInt(uidStr); } catch (NumberFormatException ex) { return -1; }
    }

    private void showStep2Dialog(int projectId, String start, String end, List<Integer> taskIds, boolean autoLoad) {
        JDialog dlg = new JDialog((java.awt.Frame) null, "MapathonQA \u2013 Step 2: Load & Select Tasks", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setSize(840, 520);
        dlg.setLocationRelativeTo(null);
        bindEscapeToClose(dlg);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 4, 5, 4); gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;

        gc.gridx=0; gc.gridy=0; gc.gridwidth=2;
        String summary = taskIds.isEmpty()
            ? "<html><b style='color:#c62828'>No tasks found</b> mapped between "+start+" and "+end+" UTC.<br>Check the project ID and time window.</html>"
            : "<html><b style='color:#2e7d32'>"+taskIds.size()+" task(s)</b> were mapped during the mapathon<br>(Project #"+projectId+", "+start+" \u2192 "+end+" UTC)</html>";
        main.add(new JLabel(summary), gc);

        String taskGridUrl = "https://tasking-manager-production-api.hotosm.org/api/v2/projects/"+projectId+"/tasks/?as_file=true&format=geojson";
        String searchQuery = taskIds.isEmpty() ? "" : buildJosmSearchQuery(taskIds);
        if (!taskIds.isEmpty()) {
            gc.gridy=1; gc.fill=GridBagConstraints.NONE; gc.weighty=0;
            JButton btnCopyQuery = new JButton("\uD83D\uDCCB Copy Search Query to Clipboard");
            btnCopyQuery.addActionListener(ev -> { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(searchQuery), null); btnCopyQuery.setText("\u2713 Copied!"); });
            main.add(btnCopyQuery, gc);
        }

        gc.gridy=2; gc.gridwidth=2; gc.fill=GridBagConstraints.HORIZONTAL;
        String steps = taskIds.isEmpty() ? "" :
            "<html><body style='line-height:140%'><b>Next steps:</b><ol style='margin-left:16px'>"
            + "<li style='margin-bottom:8px'>If you left \"Automatically load task grid\" ticked on the previous screen, it will load automatically when you close this dialog.</li>"
            + "<li style='margin-bottom:8px'>Use <b>Edit \u2192 Search (Ctrl+F)</b> and paste the search query you copied above to select the mapathon task squares</li>"
            + "<li style='margin-bottom:8px'>Download OSM data for the selected tasks using the <b>Download Along Way</b> tool</li>"
            + "<li style='margin-bottom:8px'>Click <b>Run QA on Current Layer</b> from the MapathonQA menu</li>"
            + "</ol>"
            + "<p style='margin:6px 0 2px'><b>\u2139 Note on task detection:</b></p>"
            + "<p style='margin:2px 0'>Task IDs are based on the <b>most recent action date</b> per task. Tasks mapped during the mapathon but later re-validated or invalidated may show a different date and could fall outside the window.</p>"
            + "<p style='margin:2px 0'><b>Included task statuses:</b> MAPPED, VALIDATED, INVALIDATED, BADIMAGERY, READY \u2014 all statuses are included, the time window is the only filter.</p>"
            + "</body></html>";
        main.add(new JLabel(steps), gc);

        JPanel btns = new JPanel();
        JButton btnBack  = new JButton("\u2190 Back");
        JButton btnClose = new JButton("Close & Continue \u2192");
        JButton btnX     = new JButton("Close");
        btns.add(btnBack);
        if (!taskIds.isEmpty()) btns.add(btnClose);
        else btns.add(btnX);

        btnBack.addActionListener(ev -> { dlg.dispose(); showStep1Dialog(); });
        btnX.addActionListener(ev -> dlg.dispose());
        btnClose.addActionListener(ev -> {
            dlg.dispose();
            if (autoLoad) openTaskGridInJosm(taskGridUrl);
        });

        dlg.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(main); scroll.setBorder(null);
        dlg.add(scroll, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    /**
     * User-mode equivalent of Step 2: instead of a task-ID list we already discovered, this
     * hands off a mappedBy/validatedBy search query - those fields are tags already present on
     * the task grid once loaded, so JOSM's own search does the actual matching. There's no
     * "count found" to show up front here; the match count only becomes known once the user
     * runs the search in JOSM against the loaded grid.
     */
    private void showUserSearchDialog(int projectId, int uid, String username, boolean mapperMode, boolean autoLoad) {
        // Stash the target user so "Run QA on Current Layer" (triggered later, separately, from
        // the menu) knows to tally issues by this author. Cleared back to 0 when time-window
        // mode's "Find Tasks" runs, so a stale target doesn't leak into an unrelated report.
        MapathonQAPlugin.lastTargetUid = uid;
        MapathonQAPlugin.lastTargetUsername = username;
        MapathonQAPlugin.lastMapperMode = mapperMode;

        JDialog dlg = new JDialog((java.awt.Frame) null, "MapathonQA \u2013 Step 2: Load & Select Tasks", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setSize(840, 480);
        dlg.setLocationRelativeTo(null);
        bindEscapeToClose(dlg);

        boolean hasProject = projectId > 0;
        String field = mapperMode ? "mappedBy" : "validatedBy";
        String roleWord = mapperMode ? "mapped" : "validated";
        String searchQuery = field + "=" + uid + (mapperMode ? " AND -validatedBy=*" : "");
        // Mapper mode excludes tasks already validated by anyone - a task only gets a validatedBy
        // tag once it's been through validation, so this leaves just this mapper's outstanding work.
        String taskGridUrl = hasProject
            ? "https://tasking-manager-production-api.hotosm.org/api/v2/projects/"+projectId+"/tasks/?as_file=true&format=geojson"
            : null;

        JPanel main = new JPanel(new GridBagLayout());
        main.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 4, 5, 4); gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;

        gc.gridx=0; gc.gridy=0; gc.gridwidth=2;
        String header = hasProject
            ? "<html><b style='color:#2e7d32'>Search query ready</b> for Project #"+projectId+" ("+(mapperMode?"Mapper":"Validator")+" mode)</html>"
            : "<html><b style='color:#2e7d32'>Search query ready</b> ("+(mapperMode?"Mapper":"Validator")+" mode, no project ID given)</html>";
        main.add(new JLabel(header), gc);

        gc.gridy=1; gc.fill=GridBagConstraints.NONE; gc.weighty=0;
        JButton btnCopyQuery = new JButton("\uD83D\uDCCB Copy Search Query to Clipboard");
        btnCopyQuery.addActionListener(ev -> { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(searchQuery), null); btnCopyQuery.setText("\u2713 Copied!"); });
        main.add(btnCopyQuery, gc);

        gc.gridy=2; gc.gridwidth=2; gc.fill=GridBagConstraints.HORIZONTAL;
        String loadStep = hasProject
            ? "<li style='margin-bottom:8px'>If you left \"Automatically load task grid\" ticked on the previous screen, it will load automatically when you close this dialog.</li>"
            : "<li style='margin-bottom:8px'>No project ID was given, so load the task grid yourself: <b>File \u2192 Open Location (Ctrl+L)</b> with the project's task grid URL.</li>";
        String steps =
            "<html><body style='line-height:140%'><b>Next steps:</b><ol style='margin-left:16px'>"
            + loadStep
            + "<li style='margin-bottom:8px'>Use <b>Edit \u2192 Search (Ctrl+F)</b> and paste the search query you copied above to select this user's task squares</li>"
            + "<li style='margin-bottom:8px'>Download OSM data for the selected tasks using the <b>Download Along Way</b> tool</li>"
            + "<li style='margin-bottom:8px'>Click <b>Run QA on Current Layer</b> from the MapathonQA menu</li>"
            + "</ol>"
            + "<p style='margin:6px 0 2px'><b>\u2139 Note on task detection:</b></p>"
            + "<p style='margin:2px 0'>Each task's <b>"+field+"</b> field only records the <b>most recent</b> user to have "+roleWord+" it. "
            + "If someone else "+roleWord+" it again afterward, the earlier user's work on that task won't show up here.</p>"
            + (mapperMode ? "<p style='margin:2px 0'>The query also excludes tasks that already have a <b>validatedBy</b> tag, "
                + "so it only covers this mapper's currently outstanding (not yet validated) work.</p>" : "")
            + "<p style='margin:2px 0'>The search above runs directly against the task grid layer once it's loaded \u2014 if it selects nothing, "
            + "this user may not have "+roleWord+" any tasks in this project.</p>"
            + "</body></html>";
        main.add(new JLabel(steps), gc);

        JPanel btns = new JPanel();
        JButton btnBack  = new JButton("\u2190 Back");
        JButton btnClose = new JButton("Close & Continue \u2192");
        btns.add(btnBack); btns.add(btnClose);

        btnBack.addActionListener(ev -> { dlg.dispose(); showStep1Dialog(); });
        btnClose.addActionListener(ev -> {
            dlg.dispose();
            if (autoLoad && hasProject) openTaskGridInJosm(taskGridUrl);
        });

        dlg.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(main); scroll.setBorder(null);
        dlg.add(scroll, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    /** Lets Esc close a dialog the same way its Cancel/Close button does - Swing doesn't bind this by default. */
    static void bindEscapeToClose(JDialog dlg) {
        dlg.getRootPane().registerKeyboardAction(ev -> dlg.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private List<Integer> fetchMappedTaskIds(int projectId, String startTime, String endTime) throws Exception {
        Set<Integer> ids = new LinkedHashSet<>();
        String tStart = startTime.trim().replace(" ", "T");
        String tEnd   = endTime.trim().replace(" ", "T");
        if (tStart.length() > 16) tStart = tStart.substring(0, 16);
        if (tEnd.length()   > 16) tEnd   = tEnd.substring(0, 16);

        String urlStr = TM_API + "/projects/" + projectId + "/activities/latest/";
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "MapathonQA-JOSMPlugin/1.0");
        conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (code == 403) throw new Exception("Access denied (HTTP 403) to TM API.");
        if (code == 404) throw new Exception("Project #" + projectId + " not found.");
        if (code != 200) throw new Exception("TM API returned HTTP " + code);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        String json = sb.toString();
        int activityStart = json.indexOf("\"activity\"");
        if (activityStart < 0) throw new Exception("Unexpected API response format.");
        int arrayStart = json.indexOf("[", activityStart);
        if (arrayStart < 0) throw new Exception("No activity array in response.");

        int pos = arrayStart;
        while (true) {
            int objStart = json.indexOf("{", pos);
            if (objStart < 0) break;
            int objEnd = json.indexOf("}", objStart);
            if (objEnd < 0) break;
            String obj = json.substring(objStart, objEnd + 1);

            int taskId = -1;
            String taskIdStr = extractJsonNumber(obj, "taskId");
            if (taskIdStr != null) { try { taskId = Integer.parseInt(taskIdStr); } catch (NumberFormatException ignored) {} }

            String actionDate = extractJsonString(obj, "actionDate");

            if (taskId > 0 && actionDate != null) {
                String dateShort = actionDate.length() >= 16 ? actionDate.substring(0, 16) : actionDate;
                if (dateShort.compareTo(tStart) >= 0 && dateShort.compareTo(tEnd) <= 0) ids.add(taskId);
            }
            pos = objEnd + 1;
        }
        return new ArrayList<>(ids);
    }

    private String extractJsonString(String json, String key) {
        int idx = json.indexOf("\"" + key + "\""); if (idx < 0) return null;
        int colon = json.indexOf(":", idx); if (colon < 0) return null;
        int q1 = json.indexOf("\"", colon + 1); if (q1 < 0) return null;
        int q2 = json.indexOf("\"", q1 + 1); if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private String extractJsonNumber(String json, String key) {
        int idx = json.indexOf("\"" + key + "\""); if (idx < 0) return null;
        int colon = json.indexOf(":", idx); if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return end > start ? json.substring(start, end) : null;
    }

    private String buildJosmSearchQuery(List<Integer> taskIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < taskIds.size(); i++) { if (i > 0) sb.append(" OR "); sb.append("taskId=").append(taskIds.get(i)); }
        return sb.toString();
    }

    private void openTaskGridInJosm(String url) {
        try {
            Class<?> cls = Class.forName("org.openstreetmap.josm.actions.OpenLocationAction");
            Object action = cls.getDeclaredConstructor().newInstance();
            try {
                java.lang.reflect.Method m = cls.getMethod("openUrl", boolean.class, java.util.List.class);
                List<String> urls = new ArrayList<>(); urls.add(url);
                m.invoke(action, false, urls);
            } catch (NoSuchMethodException e1) { try {
                java.lang.reflect.Method m = cls.getMethod("openUrl", java.util.List.class);
                List<String> urls = new ArrayList<>(); urls.add(url);
                m.invoke(action, urls);
            } catch (NoSuchMethodException e2) {
                java.lang.reflect.Method m = cls.getMethod("openUrl", String.class);
                m.invoke(action, url);
            }}
        } catch (Exception ex) { showManualLoadDialog(url); }
    }

    private void showManualLoadDialog(String url) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel("<html>Please load the task grid manually:<br><b>File \u2192 Open Location (Ctrl+L)</b> and paste:</html>"), BorderLayout.NORTH);
        JTextArea urlArea = new JTextArea(url);
        urlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11)); urlArea.setEditable(false); urlArea.setLineWrap(true);
        panel.add(new JScrollPane(urlArea), BorderLayout.CENTER);
        JButton btnCopy = new JButton("Copy URL to Clipboard");
        btnCopy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null));
        panel.add(btnCopy, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(null, panel, "MapathonQA \u2013 Load Task Grid", JOptionPane.INFORMATION_MESSAGE);
    }

    private int parseId(String text) { try { return Integer.parseInt(text.trim()); } catch (NumberFormatException e) { return -1; } }

    private JDialog progressDialog(String msg) {
        JDialog dlg = new JDialog((java.awt.Frame) null, "MapathonQA \u2013 Please wait...", false);
        dlg.setSize(380, 110); dlg.setLocationRelativeTo(null); dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel pp = new JPanel(new BorderLayout(10, 10)); pp.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        pp.add(new JLabel(msg), BorderLayout.CENTER);
        JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true); pp.add(bar, BorderLayout.SOUTH);
        dlg.add(pp); return dlg;
    }

    private JLabel getStatusLabel(JDialog dlg) {
        return (JLabel) ((JPanel) dlg.getContentPane().getComponent(0)).getComponent(0);
    }
}
