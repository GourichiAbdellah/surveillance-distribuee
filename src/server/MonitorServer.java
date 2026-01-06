package server;

import common.AgentData;
import common.MonitorService;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MonitorServer extends UnicastRemoteObject implements MonitorService {

    // Stockage des données des agents (Thread-safe)
    private Map<String, AgentData> agentsMap = new ConcurrentHashMap<>();
    private List<String> alertsLog = Collections.synchronizedList(new ArrayList<>());

    private static final int UDP_PORT = 9876;
    private static final int TCP_PORT = 9877;
    private static final int RMI_PORT = 1099;

    public MonitorServer() throws RemoteException {
        super();
    }

    @Override
    public List<AgentData> getAgents() throws RemoteException {
        return new ArrayList<>(agentsMap.values());
    }

    @Override
    public List<String> getAlerts() throws RemoteException {
        // Retourne les 10 dernières alertes
        synchronized (alertsLog) {
            int size = alertsLog.size();
            if (size <= 10) return new ArrayList<>(alertsLog);
            return new ArrayList<>(alertsLog.subList(size - 10, size));
        }
    }

    @Override
    public List<String[]> getHistory(String agentId, int maxRecords) throws RemoteException {
        return HistoryManager.getHistory(agentId, maxRecords);
    }

    @Override
    public Map<String, Double> getStatistics(String agentId) throws RemoteException {
        return HistoryManager.getStatistics(agentId);
    }

    // Thread pour écouter les messages UDP (Mises à jour périodiques)
    private void startUdpListener() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
                System.out.println("Serveur UDP démarré sur le port " + UDP_PORT);
                byte[] buffer = new byte[4096];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    // Désérialisation de l'objet AgentData
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(packet.getData());
                         ObjectInputStream ois = new ObjectInputStream(bis)) {
                        
                        AgentData data = (AgentData) ois.readObject();
                        agentsMap.put(data.getAgentId(), data);
                        // Sauvegarder dans l'historique
                        HistoryManager.saveToHistory(data);
                        System.out.println("UDP Reçu: " + data); // Debug
                    } catch (Exception e) {
                        System.err.println("Erreur lecture paquet UDP: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Thread pour écouter les messages TCP (Alertes critiques)
    private void startTcpListener() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
                System.out.println("Serveur TCP (Alertes) démarré sur le port " + TCP_PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleTcpClient(clientSocket)).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleTcpClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String alertMessage = in.readLine();
            if (alertMessage != null) {
                String log = "[ALERTE CRITIQUE] " + alertMessage + " à " + new java.util.Date();
                System.out.println(log);
                alertsLog.add(log);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            // Afficher les IPs disponibles pour aider l'utilisateur
            System.out.println("\nAdresses IP détectées sur ce serveur :");
            String serverIp = null;
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        System.out.println(" - " + iface.getDisplayName() + " -> " + ip);
                        if (serverIp == null) serverIp = ip; // Prendre la première IP trouvée
                    }
                }
            }
            
            if (serverIp != null) {
                System.setProperty("java.rmi.server.hostname", serverIp);
                System.out.println("\nConfiguration RMI sur l'IP : " + serverIp);
            } else {
                System.out.println("\nAucune IP externe détectée, RMI utilisera localhost.");
            }

            // Démarrer le registre RMI
            try {
                LocateRegistry.createRegistry(RMI_PORT);
                System.out.println("Registre RMI démarré sur le port " + RMI_PORT);
            } catch (Exception e) {
                System.out.println("Registre RMI déjà existant ou erreur: " + e.getMessage());
            }

            MonitorServer server = new MonitorServer();
            
            // Lier l'objet distant
            Naming.rebind("rmi://localhost:" + RMI_PORT + "/MonitorService", server);
            System.out.println("Service RMI 'MonitorService' enregistré.");

            // Démarrer les écoutes réseau
            server.startUdpListener();
            server.startTcpListener();

            System.out.println("Serveur prêt et en attente...");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
