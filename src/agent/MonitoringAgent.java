package agent;

import common.AgentData;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Random;

public class MonitoringAgent {

    private String agentId;
    private String serverAddress = "localhost";
    private static final int UDP_PORT = 9876;
    private static final int TCP_PORT = 9877;

    public MonitoringAgent(String agentId) {
        this.agentId = agentId;
    }

    public void start() {
        System.out.println("Agent " + agentId + " démarré.");
        Random random = new Random();

        new Thread(() -> {
            while (true) {
                try {
                    // 1. Simuler des données
                    double cpu = 10 + random.nextDouble() * 90; // Entre 10 et 100
                    double ram = 20 + random.nextDouble() * 60; // Entre 20 et 80
                    double disk = 40 + random.nextDouble() * 20;

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
        // Lancer plusieurs agents pour tester
        new MonitoringAgent("Agent-Alpha").start();
        
        try { Thread.sleep(1000); } catch (Exception e) {}
        
        new MonitoringAgent("Agent-Beta").start();
    }
}
