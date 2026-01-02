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
    
    // Composants pour les boutons (pour gérer les droits)
    private JButton exportBtn;
    private JButton statsBtn;
    private JButton historyBtn;

    public MonitorClient(String user, LoginDialog.Role role) {
        super("Système de Surveillance Distribué - " + user + " [" + role + "]");
        this.currentUser = user;
        this.currentRole = role;
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
        });
        toolBar.add(thresholdSpinner);

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
            // Obtenir l'agent sélectionné ou tous
            String agentId = "";
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                agentId = (String) table.getValueAt(selectedRow, 0);
            }
            
            Map<String, Double> stats = monitorService.getStatistics(agentId);
            
            String title = agentId.isEmpty() ? "Tous les agents" : "Agent: " + agentId;
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
            
            JOptionPane.showMessageDialog(this, message, "Statistiques", JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erreur: " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
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
            String url = "rmi://localhost:1099/MonitorService";
            monitorService = (MonitorService) Naming.lookup(url);
            System.out.println("Connecté au serveur RMI.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Impossible de se connecter au serveur.\n\n" +
                "Vérifiez que :\n" +
                "1. Le fichier 'MonitorServer' est bien lancé.\n" +
                "2. Il n'y a pas d'erreur dans la console du serveur.\n\n" +
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
            
            tableModel.setRowCount(0);
            for (AgentData agent : agents) {
                String status = agent.getCpuUsage() >= cpuThreshold ? "CRITIQUE" : "OK";
                
                Object[] row = {
                    agent.getAgentId(),
                    String.format("%.0f", agent.getCpuUsage()),
                    String.format("%.0f", agent.getMemoryUsage()),
                    String.format("%.0f", agent.getDiskUsage()),
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
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File("surveillance_export_" + 
            new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
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
                
                JOptionPane.showMessageDialog(this, 
                    "Données exportées avec succès!\n" + fileChooser.getSelectedFile().getAbsolutePath(),
                    "Export CSV", JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Erreur lors de l'export: " + e.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);

            if (loginDialog.isAuthenticated()) {
                new MonitorClient(loginDialog.getCurrentUser(), loginDialog.getCurrentRole()).setVisible(true);
            } else {
                System.out.println("Authentification annulée.");
                System.exit(0);
            }
        });
    }
}
