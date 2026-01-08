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
            String line = String.format(Locale.US, "%s,%s,%.2f,%.2f,%.2f,%s",
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
    
    // Récupérer l'historique par date
    public static List<String[]> getHistoryByDate(String agentId, Date startDate, Date endDate) {
        List<String[]> history = new ArrayList<>();
        File file = new File(HISTORY_FILE);
        
        if (!file.exists()) {
            return history;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    try {
                        Date recordDate = dateFormat.parse(parts[0]);
                        boolean dateInRange = (startDate == null || !recordDate.before(startDate)) && 
                                              (endDate == null || !recordDate.after(endDate));
                        
                        if (dateInRange) {
                            if (agentId == null || agentId.isEmpty() || parts[1].equals(agentId)) {
                                history.add(parts);
                            }
                        }
                    } catch (Exception e) {
                        // Ignorer les erreurs de parsing de date
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lecture historique: " + e.getMessage());
        }
        
        return history;
    }

    // Calcule les statistiques pour un agent (Derniers 1000 enregistrements)
    public static Map<String, Double> getStatistics(String agentId) {
        List<String[]> history = getHistory(agentId, 1000);
        return calculateStatsFromHistory(history);
    }
    
    // Calcule les statistiques pour un agent sur une période donnée
    public static Map<String, Double> getStatisticsByDate(String agentId, Date startDate, Date endDate) {
        List<String[]> history = getHistoryByDate(agentId, startDate, endDate);
        return calculateStatsFromHistory(history);
    }

    private static Map<String, Double> calculateStatsFromHistory(List<String[]> history) {
        Map<String, Double> stats = new HashMap<>();
        
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
        int validRecords = 0;
        
        for (String[] record : history) {
            try {
                // Remplacer virgule par point pour supporter les anciens formats/autres locales
                double cpu = Double.parseDouble(record[2].replace(',', '.'));
                double memory = Double.parseDouble(record[3].replace(',', '.'));
                
                sumCpu += cpu;
                sumMemory += memory;
                maxCpu = Math.max(maxCpu, cpu);
                maxMemory = Math.max(maxMemory, memory);
                minCpu = Math.min(minCpu, cpu);
                minMemory = Math.min(minMemory, memory);
                
                if ("CRITIQUE".equals(record[5])) {
                    criticalCount++;
                }
                validRecords++;
            } catch (NumberFormatException e) {
                // Ignorer les lignes mal formatées
            }
        }
        
        if (validRecords == 0) validRecords = 1; // Éviter division par zéro

        stats.put("avgCpu", sumCpu / validRecords);
        stats.put("avgMemory", sumMemory / validRecords);
        stats.put("maxCpu", maxCpu);
        stats.put("maxMemory", maxMemory);
        stats.put("minCpu", minCpu);
        stats.put("minMemory", minMemory);
        stats.put("totalRecords", (double) validRecords);
        stats.put("criticalCount", (double) criticalCount);
        
        return stats;
    }
}
