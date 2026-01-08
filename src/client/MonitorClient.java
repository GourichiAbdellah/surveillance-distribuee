package client;

import common.AgentData;
import common.MonitorService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.rmi.Naming;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class MonitorClient extends JFrame {

    private MonitorService monitorService;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextArea alertsArea;
    private JSpinner thresholdSpinner;
    private JTextField searchField;
    private TableRowSorter<DefaultTableModel> sorter;
    private int cpuThreshold = 80; // Seuil par défaut
    private String currentUser;
    private LoginDialog.Role currentRole;
    private String serverAddress;
    
    // Composants pour les boutons (pour gérer les droits)
    private JButton exportBtn;
    private JButton statsBtn;
    private JButton historyBtn;

    public MonitorClient(String user, LoginDialog.Role role, String serverAddress) {
        super("Système de Surveillance Distribué - " + user + " [" + role + "]");
        this.currentUser = user;
        this.currentRole = role;
        this.serverAddress = serverAddress;
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        applyRoleRestrictions();
        connectToServer();
        startRefreshTimer();
    }

    private void initUI() {
        // Layout principal
        setLayout(new BorderLayout());

        // 1. Barre d'outils (Haut)
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton refreshBtn = new JButton("Rafraîchir");
        refreshBtn.addActionListener(e -> updateData());
        toolBar.add(refreshBtn);

        toolBar.addSeparator();

        // Recherche et filtrage
        toolBar.add(new JLabel(" Rechercher: "));
        searchField = new JTextField(12);
        searchField.setMaximumSize(new Dimension(150, 30));
        searchField.addActionListener(e -> filterTable());
        toolBar.add(searchField);
        
        JButton filterBtn = new JButton("Filtrer");
        filterBtn.addActionListener(e -> filterTable());
        toolBar.add(filterBtn);
        
        JButton clearBtn = new JButton("Effacer");
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            sorter.setRowFilter(null);
        });
        toolBar.add(clearBtn);

        toolBar.addSeparator();

        // Configuration du seuil
        toolBar.add(new JLabel(" Seuil CPU (%): "));
        thresholdSpinner = new JSpinner(new SpinnerNumberModel(80, 10, 100, 5));
        thresholdSpinner.setMaximumSize(new Dimension(60, 30));
        thresholdSpinner.addChangeListener(e -> {
            cpuThreshold = (int) thresholdSpinner.getValue();
            // Mettre à jour le renderer pour toutes les colonnes concernées
            ProgressBarRenderer renderer = (ProgressBarRenderer) table.getColumnModel().getColumn(1).getCellRenderer();
            if (renderer != null) renderer.setThreshold(cpuThreshold);
            
            renderer = (ProgressBarRenderer) table.getColumnModel().getColumn(2).getCellRenderer();
            if (renderer != null) renderer.setThreshold(cpuThreshold);
            
            renderer = (ProgressBarRenderer) table.getColumnModel().getColumn(3).getCellRenderer();
            if (renderer != null) renderer.setThreshold(cpuThreshold);
            
            table.repaint();
        });
        toolBar.add(thresholdSpinner);

        toolBar.addSeparator();

        // Bouton Export CSV

        toolBar.addSeparator();

        // Bouton Export CSV
        exportBtn = new JButton("Exporter CSV");
        exportBtn.addActionListener(e -> exportToCSV());
        toolBar.add(exportBtn);

        // Bouton Statistiques
        statsBtn = new JButton("Statistiques");
        statsBtn.addActionListener(e -> showStatistics());
        toolBar.add(statsBtn);

        // Bouton Historique
        historyBtn = new JButton("Historique");
        historyBtn.addActionListener(e -> showHistory());
        toolBar.add(historyBtn);

        toolBar.addSeparator();

        // Info utilisateur et rôle
        toolBar.add(Box.createHorizontalGlue());
        JLabel userLabel = new JLabel(currentUser + " [" + currentRole + "]  ");
        userLabel.setFont(new Font("Arial", Font.BOLD, 12));
        toolBar.add(userLabel);

        add(toolBar, BorderLayout.NORTH);

        // 2. Tableau des agents (Centre)
        String[] columnNames = {"Agent ID", "CPU (%)", "Mémoire (%)", "Disque (%)", "Dernière MAJ", "Statut"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        
        // Configurer le tri et filtrage
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        
        // Appliquer les barres de progression
        ProgressBarRenderer progressRenderer = new ProgressBarRenderer();
        table.getColumnModel().getColumn(1).setCellRenderer(progressRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(progressRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(progressRenderer);

        table.setRowHeight(25);
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 3. Zone des alertes (Bas)
        alertsArea = new JTextArea(5, 20);
        alertsArea.setEditable(false);
        alertsArea.setForeground(Color.RED);
        alertsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JPanel alertPanel = new JPanel(new BorderLayout());
        alertPanel.setBorder(BorderFactory.createTitledBorder("Alertes Critiques"));
        alertPanel.add(new JScrollPane(alertsArea), BorderLayout.CENTER);
        add(alertPanel, BorderLayout.SOUTH);
    }

    // Appliquer les restrictions selon le rôle
    private void applyRoleRestrictions() {
        switch (currentRole) {
            case ADMIN:
                // Admin a accès à tout
                break;
            case OPERATEUR:
                // Opérateur peut voir stats et historique mais pas exporter
                exportBtn.setEnabled(false);
                exportBtn.setToolTipText("Réservé aux administrateurs");
                break;
            case LECTEUR:
                // Lecteur : lecture seule
                exportBtn.setEnabled(false);
                exportBtn.setToolTipText("Réservé aux administrateurs");
                statsBtn.setEnabled(false);
                statsBtn.setToolTipText("Réservé aux opérateurs et administrateurs");
                historyBtn.setEnabled(false);
                historyBtn.setToolTipText("Réservé aux opérateurs et administrateurs");
                thresholdSpinner.setEnabled(false);
                break;
        }
    }

    // Filtrer la table selon le texte de recherche
    private void filterTable() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        }
    }

    // Afficher les statistiques
    private void showStatistics() {
        if (monitorService == null) return;
        
        try {
            String[] options = {"Statistiques en temps réel (Derniers 1000 records)", "Statistiques par Période"};
            int choice = JOptionPane.showOptionDialog(this, 
                "Quelles statistiques voulez-vous voir ?", 
                "Type de Statistiques", 
                JOptionPane.DEFAULT_OPTION, 
                JOptionPane.QUESTION_MESSAGE, 
                null, options, options[0]);

            if (choice == 0) {
                // Mode original : statistiques rapides basées sur les derniers ~1000 messages
                String agentId = "";
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    agentId = (String) table.getValueAt(selectedRow, 0);
                }
                displayStats(monitorService.getStatistics(agentId), agentId.isEmpty() ? "Tous les agents" : "Agent: " + agentId);
                
            } else if (choice == 1) {
                // Mode nouveau : statistiques par période
                JPanel panel = new JPanel(new GridLayout(4, 2));
                JTextField startField = new JTextField(new SimpleDateFormat("yyyy-MM-dd 00:00:00").format(new Date()));
                JTextField endField = new JTextField(new SimpleDateFormat("yyyy-MM-dd 23:59:59").format(new Date()));
                
                panel.add(new JLabel("Du (yyyy-MM-dd HH:mm:ss):"));
                panel.add(startField);
                panel.add(new JLabel("Au (yyyy-MM-dd HH:mm:ss):"));
                panel.add(endField);
                panel.add(new JLabel("Agent ID (Vide = Tous):"));
                JTextField agentField = new JTextField();
                
                // Pré-remplir si une ligne est sélectionnée
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    agentField.setText((String) table.getValueAt(selectedRow, 0));
                }
                panel.add(agentField);

                int result = JOptionPane.showConfirmDialog(null, panel, "Sélectionner la Période", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date start = sdf.parse(startField.getText());
                    Date end = sdf.parse(endField.getText());
                    String agentId = agentField.getText().trim();
                    
                    Map<String, Double> stats = monitorService.getStatisticsByDate(agentId.isEmpty() ? null : agentId, start, end);
                    displayStats(stats, agentId.isEmpty() ? "Tous les agents" : "Agent: " + agentId);
                }
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erreur: " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void displayStats(Map<String, Double> stats, String title) {
        String message = String.format(
            "=== Statistiques: %s ===\n\n" +
            "CPU:\n" +
            "  - Moyenne: %.1f%%\n" +
            "  - Min: %.1f%%\n" +
            "  - Max: %.1f%%\n\n" +
            "Mémoire:\n" +
            "  - Moyenne: %.1f%%\n" +
            "  - Min: %.1f%%\n" +
            "  - Max: %.1f%%\n\n" +
            "Total enregistrements: %.0f\n" +
            "Alertes critiques: %.0f",
            title,
            stats.get("avgCpu"), stats.get("minCpu"), stats.get("maxCpu"),
            stats.get("avgMemory"), stats.get("minMemory"), stats.get("maxMemory"),
            stats.get("totalRecords"), stats.get("criticalCount")
        );
        
        JOptionPane.showMessageDialog(this, message, "Résultats Statistiques", JOptionPane.INFORMATION_MESSAGE);
    }

    // Afficher l'historique
    private void showHistory() {
        if (monitorService == null) return;
        
        try {
            String agentId = "";
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                agentId = (String) table.getValueAt(selectedRow, 0);
            }
            
            List<String[]> history = monitorService.getHistory(agentId, 50);
            
            // Créer une fenêtre pour l'historique
            JDialog historyDialog = new JDialog(this, "Historique" + (agentId.isEmpty() ? "" : " - " + agentId), true);
            historyDialog.setSize(700, 400);
            historyDialog.setLocationRelativeTo(this);
            
            String[] columns = {"Date/Heure", "Agent", "CPU (%)", "Mémoire (%)", "Disque (%)", "Statut"};
            DefaultTableModel historyModel = new DefaultTableModel(columns, 0);
            
            for (String[] record : history) {
                historyModel.addRow(record);
            }
            
            JTable historyTable = new JTable(historyModel);
            historyTable.setAutoCreateRowSorter(true);
            historyDialog.add(new JScrollPane(historyTable));
            historyDialog.setVisible(true);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erreur: " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void connectToServer() {
        try {
            String url = "rmi://" + serverAddress + ":1099/MonitorService";
            monitorService = (MonitorService) Naming.lookup(url);
            System.out.println("Connecté au serveur RMI sur " + serverAddress);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Impossible de se connecter au serveur (" + serverAddress + ").\n\n" +
                "Vérifiez que :\n" +
                "1. Le fichier 'MonitorServer' est bien lancé sur la machine cible.\n" +
                "2. L'adresse IP est correcte.\n" +
                "3. Le pare-feu autorise les connexions (Ports 1099, 9876, 9877).\n\n" +
                "Erreur technique : " + e.getMessage(), 
                "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void startRefreshTimer() {
        Timer timer = new Timer(1000, e -> updateData());
        timer.start();
    }

    private void updateData() {
        if (monitorService == null) return;

        try {
            List<AgentData> agents = monitorService.getAgents();
            Date now = new Date();
            
            tableModel.setRowCount(0);
            for (AgentData agent : agents) {
                // Vérifier si l'agent est déconnecté (pas de mise à jour depuis 15 secondes)
                long diff = now.getTime() - agent.getTimestamp().getTime();
                boolean isOffline = diff > 15000;
                
                String status;
                if (isOffline) {
                    status = "OFFLINE";
                } else {
                    status = agent.getCpuUsage() >= cpuThreshold ? "CRITIQUE" : "OK";
                }
                
                Object[] row = {
                    agent.getAgentId(),
                    isOffline ? "0" : String.format("%.0f", agent.getCpuUsage()),
                    isOffline ? "0" : String.format("%.0f", agent.getMemoryUsage()),
                    isOffline ? "0" : String.format("%.0f", agent.getDiskUsage()),
                    new SimpleDateFormat("HH:mm:ss").format(agent.getTimestamp()),
                    status
                };
                tableModel.addRow(row);
            }

            List<String> alerts = monitorService.getAlerts();
            StringBuilder sb = new StringBuilder();
            for (String alert : alerts) {
                sb.append(alert).append("\n");
            }
            alertsArea.setText(sb.toString());

        } catch (Exception e) {
            System.err.println("Erreur lors de la mise à jour: " + e.getMessage());
        }
    }

    private void exportToCSV() {
        String[] options = {"Vue Actuelle (Agents Connectés)", "Historique Complet (Par Période)"};
        int choice = JOptionPane.showOptionDialog(this, 
            "Que voulez-vous exporter ?", 
            "Type d'Export", 
            JOptionPane.DEFAULT_OPTION, 
            JOptionPane.QUESTION_MESSAGE, 
            null, options, options[0]);

        if (choice == 0) {
            exportCurrentView();
        } else if (choice == 1) {
            exportHistoryByDate();
        }
    }

    private void exportCurrentView() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File("surveillance_vue_" + 
            new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(fileChooser.getSelectedFile()))) {
                writer.println("Agent ID,CPU (%),Mémoire (%),Disque (%),Timestamp,Statut");
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    StringBuilder line = new StringBuilder();
                    for (int j = 0; j < tableModel.getColumnCount(); j++) {
                        if (j > 0) line.append(",");
                        Object value = tableModel.getValueAt(i, j);
                        line.append(value != null ? value.toString() : "");
                    }
                    writer.println(line.toString());
                }
                JOptionPane.showMessageDialog(this, "Export réussi !");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Erreur: " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportHistoryByDate() {
        JPanel panel = new JPanel(new GridLayout(4, 2));
        JTextField startField = new JTextField(new SimpleDateFormat("yyyy-MM-dd 00:00:00").format(new Date()));
        JTextField endField = new JTextField(new SimpleDateFormat("yyyy-MM-dd 23:59:59").format(new Date()));
        
        panel.add(new JLabel("Du (yyyy-MM-dd HH:mm:ss):"));
        panel.add(startField);
        panel.add(new JLabel("Au (yyyy-MM-dd HH:mm:ss):"));
        panel.add(endField);
        panel.add(new JLabel("Agent ID (Vide = Tous):"));
        JTextField agentField = new JTextField();
        panel.add(agentField);

        int result = JOptionPane.showConfirmDialog(null, panel, "Sélectionner la Période", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date start = sdf.parse(startField.getText());
                Date end = sdf.parse(endField.getText());
                String agentId = agentField.getText().trim();
                
                List<String[]> data = monitorService.getHistoryByDate(agentId.isEmpty() ? null : agentId, start, end);
                
                if (data.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Aucune donnée trouvée pour cette période.");
                    return;
                }

                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setSelectedFile(new java.io.File("historique_" + 
                    new SimpleDateFormat("yyyyMMdd").format(start) + ".csv"));
                
                if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(fileChooser.getSelectedFile()))) {
                        writer.println("Date,Agent ID,CPU (%),Mémoire (%),Disque (%),Statut");
                        for (String[] row : data) {
                            writer.println(String.join(",", row));
                        }
                        JOptionPane.showMessageDialog(this, "Historique exporté (" + data.size() + " lignes) !");
                    }
                }

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Erreur (Format de date invalide ?): " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);

            if (loginDialog.isAuthenticated()) {
                String inputIp = JOptionPane.showInputDialog(null, 
                    "Entrez l'adresse IP du serveur de monitoring :", 
                    "Configuration Client", 
                    JOptionPane.QUESTION_MESSAGE);
                
                String serverAddress = "localhost";
                if (inputIp != null && !inputIp.trim().isEmpty()) {
                    serverAddress = inputIp.trim();
                }

                new MonitorClient(loginDialog.getCurrentUser(), loginDialog.getCurrentRole(), serverAddress).setVisible(true);
            } else {
                System.out.println("Authentification annulée.");
                System.exit(0);
            }
        });
    }
}
