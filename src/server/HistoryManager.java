package server;

import common.AgentData;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Gestionnaire d'historique - Stockage persistant des données dans un fichier
 */
public class HistoryManager {
    
    private static final String HISTORY_FILE = "historique_agents.csv";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Sauvegarde une entrée dans l'historique
    public static synchronized void saveToHistory(AgentData data) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(HISTORY_FILE, true))) {
            String line = String.format("%s,%s,%.2f,%.2f,%.2f,%s",
                dateFormat.format(data.getTimestamp()),
                data.getAgentId(),
                data.getCpuUsage(),
                data.getMemoryUsage(),
                data.getDiskUsage(),
                data.isCritical() ? "CRITIQUE" : "OK"
            );
            writer.println(line);
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde historique: " + e.getMessage());
        }
    }
    
    // Récupère l'historique pour un agent donné (ou tous si agentId est null)
    public static List<String[]> getHistory(String agentId, int maxRecords) {
        List<String[]> history = new ArrayList<>();
        File file = new File(HISTORY_FILE);
        
        if (!file.exists()) {
            return history;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            List<String[]> allRecords = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    // Filtrer par agent si spécifié
                    if (agentId == null || agentId.isEmpty() || parts[1].equals(agentId)) {
                        allRecords.add(parts);
                    }
                }
            }
            
            // Retourner les derniers enregistrements (copie pour éviter NotSerializableException)
            int start = Math.max(0, allRecords.size() - maxRecords);
            history = new ArrayList<>(allRecords.subList(start, allRecords.size()));
            
        } catch (IOException e) {
            System.err.println("Erreur lecture historique: " + e.getMessage());
        }
        
        return history;
    }
    
    // Calcule les statistiques pour un agent
    public static Map<String, Double> getStatistics(String agentId) {
        Map<String, Double> stats = new HashMap<>();
        List<String[]> history = getHistory(agentId, 1000);
        
        if (history.isEmpty()) {
            stats.put("avgCpu", 0.0);
            stats.put("avgMemory", 0.0);
            stats.put("maxCpu", 0.0);
            stats.put("maxMemory", 0.0);
            stats.put("minCpu", 0.0);
            stats.put("minMemory", 0.0);
            stats.put("totalRecords", 0.0);
            stats.put("criticalCount", 0.0);
            return stats;
        }
        
        double sumCpu = 0, sumMemory = 0;
        double maxCpu = 0, maxMemory = 0;
        double minCpu = 100, minMemory = 100;
        int criticalCount = 0;
        
        for (String[] record : history) {
            try {
                double cpu = Double.parseDouble(record[2]);
                double memory = Double.parseDouble(record[3]);
                
                sumCpu += cpu;
                sumMemory += memory;
                maxCpu = Math.max(maxCpu, cpu);
                maxMemory = Math.max(maxMemory, memory);
                minCpu = Math.min(minCpu, cpu);
                minMemory = Math.min(minMemory, memory);
                
                if ("CRITIQUE".equals(record[5])) {
                    criticalCount++;
                }
            } catch (NumberFormatException e) {
                // Ignorer les lignes mal formatées
            }
        }
        
        int count = history.size();
        stats.put("avgCpu", sumCpu / count);
        stats.put("avgMemory", sumMemory / count);
        stats.put("maxCpu", maxCpu);
        stats.put("maxMemory", maxMemory);
        stats.put("minCpu", minCpu);
        stats.put("minMemory", minMemory);
        stats.put("totalRecords", (double) count);
        stats.put("criticalCount", (double) criticalCount);
        
        return stats;
    }
}
