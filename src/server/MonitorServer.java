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
            // FIX: Forcer l'utilisation de localhost pour éviter les problèmes réseaux sur Linux
            System.setProperty("java.rmi.server.hostname", "localhost");

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
