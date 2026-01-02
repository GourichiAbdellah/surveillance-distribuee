package client;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class LoginDialog extends JDialog {

    private JTextField usernameField;
    private JPasswordField passwordField;
    private boolean authenticated = false;
    private String currentUser = null;

    // Base de données utilisateurs simple (login -> password)
    private static final Map<String, String> USERS = new HashMap<>();
    static {
        USERS.put("admin", "admin123");
        USERS.put("user", "user123");
        USERS.put("etudiant", "etudiant");
    }

    public LoginDialog(Frame parent) {
        super(parent, "Connexion - Système de Surveillance", true);
        setSize(350, 200);
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

        // Titre
        JLabel titleLabel = new JLabel("Authentification requise", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        add(mainPanel);
    }

    private void authenticate() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (USERS.containsKey(username) && USERS.get(username).equals(password)) {
            authenticated = true;
            currentUser = username;
            dispose();
        } else {
            JOptionPane.showMessageDialog(this,
                "Identifiants incorrects.\n\nUtilisateurs disponibles:\n- admin / admin123\n- user / user123\n- etudiant / etudiant",
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
}
