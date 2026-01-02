package client;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class LoginDialog extends JDialog {

    // Enumération des rôles
    public enum Role {
        ADMIN,      // Accès total : export, stats, config seuils
        OPERATEUR,  // Peut voir les alertes et l'historique, pas d'export
        LECTEUR     // Lecture seule du tableau
    }

    private JTextField usernameField;
    private JPasswordField passwordField;
    private boolean authenticated = false;
    private String currentUser = null;
    private Role currentRole = null;

    // Base de données utilisateurs (login -> password)
    private static final Map<String, String> USERS = new HashMap<>();
    // Rôles des utilisateurs (login -> role)
    private static final Map<String, Role> USER_ROLES = new HashMap<>();
    
    static {
        // Utilisateurs et mots de passe
        USERS.put("admin", "admin123");
        USERS.put("operateur", "oper123");
        USERS.put("etudiant", "etudiant");
        
        // Attribution des rôles
        USER_ROLES.put("admin", Role.ADMIN);
        USER_ROLES.put("operateur", Role.OPERATEUR);
        USER_ROLES.put("etudiant", Role.LECTEUR);
    }

    public LoginDialog(Frame parent) {
        super(parent, "Connexion - Système de Surveillance", true);
        setSize(350, 220);
        setLocationRelativeTo(parent);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Panel du formulaire
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.add(new JLabel("Utilisateur:"));
        usernameField = new JTextField();
        formPanel.add(usernameField);

        formPanel.add(new JLabel("Mot de passe:"));
        passwordField = new JPasswordField();
        formPanel.add(passwordField);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Panel des boutons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton loginBtn = new JButton("Se connecter");
        JButton cancelBtn = new JButton("Annuler");

        loginBtn.addActionListener(e -> authenticate());
        cancelBtn.addActionListener(e -> {
            authenticated = false;
            dispose();
        });

        // Permettre Entrée pour valider
        passwordField.addActionListener(e -> authenticate());
        usernameField.addActionListener(e -> passwordField.requestFocus());

        buttonPanel.add(loginBtn);
        buttonPanel.add(cancelBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Titre avec info rôles
        JPanel titlePanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Authentification requise", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titlePanel.add(titleLabel, BorderLayout.CENTER);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        add(mainPanel);
    }

    private void authenticate() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (USERS.containsKey(username) && USERS.get(username).equals(password)) {
            authenticated = true;
            currentUser = username;
            currentRole = USER_ROLES.getOrDefault(username, Role.LECTEUR);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this,
                "Identifiants incorrects.\n\n" +
                "Comptes disponibles:\n" +
                "- admin / admin123 (Admin)\n" +
                "- operateur / oper123 (Opérateur)\n" +
                "- etudiant / etudiant (Lecteur)",
                "Erreur d'authentification",
                JOptionPane.ERROR_MESSAGE);
            passwordField.setText("");
            passwordField.requestFocus();
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public Role getCurrentRole() {
        return currentRole;
    }
}
