package client;

import common.AgentData;
import common.MonitorService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.rmi.Naming;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MonitorClient extends JFrame {

    private MonitorService monitorService;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextArea alertsArea;
    private JSpinner thresholdSpinner;
    private int cpuThreshold = 80; // Seuil par défaut
    private String currentUser;

    public MonitorClient(String user) {
        super("Système de Surveillance Distribué - Connecté: " + user);
        this.currentUser = user;
        setSize(900, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
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

        // Configuration du seuil
        toolBar.add(new JLabel(" Seuil CPU (%): "));
        thresholdSpinner = new JSpinner(new SpinnerNumberModel(80, 10, 100, 5));
        thresholdSpinner.setMaximumSize(new Dimension(60, 30));
        thresholdSpinner.addChangeListener(e -> {
            cpuThreshold = (int) thresholdSpinner.getValue();
            System.out.println("Nouveau seuil: " + cpuThreshold + "%");
        });
        toolBar.add(thresholdSpinner);

        toolBar.addSeparator();

        // Bouton Export CSV
        JButton exportBtn = new JButton("Exporter CSV");
        exportBtn.addActionListener(e -> exportToCSV());
        toolBar.add(exportBtn);

        toolBar.addSeparator();

        // Info utilisateur
        toolBar.add(Box.createHorizontalGlue());
        JLabel userLabel = new JLabel("Utilisateur: " + currentUser + "  ");
        userLabel.setFont(new Font("Arial", Font.BOLD, 12));
        toolBar.add(userLabel);

        add(toolBar, BorderLayout.NORTH);

        // 2. Tableau des agents (Centre)
        String[] columnNames = {"Agent ID", "CPU (%)", "Mémoire (%)", "Disque (%)", "Dernière MAJ", "Statut"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Table non éditable
            }
        };
        table = new JTable(tableModel);
        
        // Appliquer les barres de progression aux colonnes CPU, Mémoire, Disque
        ProgressBarRenderer progressRenderer = new ProgressBarRenderer();
        table.getColumnModel().getColumn(1).setCellRenderer(progressRenderer); // CPU
        table.getColumnModel().getColumn(2).setCellRenderer(progressRenderer); // Mémoire
        table.getColumnModel().getColumn(3).setCellRenderer(progressRenderer); // Disque

        table.setRowHeight(25);
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 3. Zone des alertes (Bas)
        alertsArea = new JTextArea(6, 20);
        alertsArea.setEditable(false);
        alertsArea.setForeground(Color.RED);
        alertsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JPanel alertPanel = new JPanel(new BorderLayout());
        alertPanel.setBorder(BorderFactory.createTitledBorder("Alertes Critiques"));
        alertPanel.add(new JScrollPane(alertsArea), BorderLayout.CENTER);
        add(alertPanel, BorderLayout.SOUTH);
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
        // Rafraîchir toutes les 1 secondes (1000 ms)
        Timer timer = new Timer(1000, e -> updateData());
        timer.start();
    }

    private void updateData() {
        if (monitorService == null) return;

        try {
            // 1. Récupérer les agents
            List<AgentData> agents = monitorService.getAgents();
            
            // Mettre à jour le tableau
            tableModel.setRowCount(0); // Effacer les anciennes données
            for (AgentData agent : agents) {
                // Déterminer le statut selon le seuil configuré
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

            // 2. Récupérer les alertes
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
                // En-têtes
                writer.println("Agent ID,CPU (%),Mémoire (%),Disque (%),Timestamp,Statut");
                
                // Données
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
        // Lancer l'interface dans le thread Swing
        SwingUtilities.invokeLater(() -> {
            // 1. Afficher l'écran de login
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);

            // 2. Vérifier l'authentification
            if (loginDialog.isAuthenticated()) {
                new MonitorClient(loginDialog.getCurrentUser()).setVisible(true);
            } else {
                System.out.println("Authentification annulée.");
                System.exit(0);
            }
        });
    }
}
