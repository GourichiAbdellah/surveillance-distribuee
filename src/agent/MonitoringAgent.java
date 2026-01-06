package agent;

import common.AgentData;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.io.File;
import javax.swing.JOptionPane;

public class MonitoringAgent {

    private String agentId;
    private String serverAddress;
    private static final int UDP_PORT = 9876;
    private static final int TCP_PORT = 9877;
    private OperatingSystemMXBean osBean;

    public MonitoringAgent(String agentId, String serverAddress) {
        this.agentId = agentId;
        this.serverAddress = serverAddress;
        try {
            this.osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        } catch (Exception e) {
            System.err.println("Impossible d'accéder aux métriques système: " + e.getMessage());
        }
    }

    public void start() {
        System.out.println("Agent " + agentId + " démarré. Connexion au serveur: " + serverAddress);
        
        new Thread(() -> {
            while (true) {
                try {
                    // 1. Récupérer les vraies métriques
                    double cpu = 0;
                    double ram = 0;
                    double disk = 0;

                    if (osBean != null) {
                        // CPU Usage (valeur entre 0.0 et 1.0 -> convertir en %)
                        // La première valeur peut être NaN ou -1
                        double sysLoad = osBean.getCpuLoad();
                        cpu = (sysLoad < 0) ? 0 : sysLoad * 100;

                        // RAM Usage
                        long totalMem = osBean.getTotalMemorySize();
                        long freeMem = osBean.getFreeMemorySize();
                        ram = ((double)(totalMem - freeMem) / totalMem) * 100;
                    }

                    // Disk Usage (Partition racine)
                    File root = new File("/");
                    long totalSpace = root.getTotalSpace();
                    long freeSpace = root.getFreeSpace();
                    if (totalSpace > 0) {
                        disk = ((double)(totalSpace - freeSpace) / totalSpace) * 100;
                    }

                    AgentData data = new AgentData(agentId, cpu, ram, disk);

                    // 2. Envoyer via UDP (Mise à jour régulière)
                    sendUdpUpdate(data);

                    // 3. Vérifier seuil critique et envoyer via TCP si nécessaire
                    if (cpu > 80.0) {
                        data.setCritical(true);
                        sendTcpAlert("CPU Surcharge: " + String.format("%.2f", cpu) + "%");
                    }

                    // Pause de 2 secondes
                    Thread.sleep(2000);

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Erreur Agent: " + e.getMessage());
                }
            }
        }).start();
    }

    private void sendUdpUpdate(AgentData data) {
        try (DatagramSocket socket = new DatagramSocket();
             ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {

            oos.writeObject(data);
            byte[] bytes = bos.toByteArray();

            InetAddress address = InetAddress.getByName(serverAddress);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, UDP_PORT);
            
            socket.send(packet);
            // System.out.println("Données envoyées (UDP): " + data);

        } catch (Exception e) {
            System.err.println("Erreur envoi UDP: " + e.getMessage());
        }
    }

    private void sendTcpAlert(String message) {
        try (Socket socket = new Socket(serverAddress, TCP_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            out.println("Agent [" + agentId + "]: " + message);
            System.out.println("Alerte envoyée (TCP): " + message);

        } catch (Exception e) {
            System.err.println("Erreur envoi TCP: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String serverIp = "localhost";
        String agentName = "Agent-" + System.getProperty("user.name");

        try {
            // 1. Priorité aux arguments en ligne de commande
            if (args.length > 0) {
                serverIp = args[0];
                if (args.length > 1) {
                    agentName = args[1];
                }
            } 
            // 2. Sinon, mode interactif si graphique disponible
            else if (!java.awt.GraphicsEnvironment.isHeadless()) {
                String inputIp = JOptionPane.showInputDialog(null, 
                    "Entrez l'adresse IP du serveur de monitoring :", 
                    "Configuration Agent", 
                    JOptionPane.QUESTION_MESSAGE);
                if (inputIp != null && !inputIp.trim().isEmpty()) {
                    serverIp = inputIp.trim();
                }

                String inputName = JOptionPane.showInputDialog(null, 
                    "Entrez le nom de cet ordinateur (Agent ID) :", 
                    agentName);
                if (inputName != null && !inputName.trim().isEmpty()) {
                    agentName = inputName.trim();
                }
            }
        } catch (Exception e) {
            System.out.println("Mode console uniquement.");
        }

        new MonitoringAgent(agentName, serverIp).start();
    }
}
