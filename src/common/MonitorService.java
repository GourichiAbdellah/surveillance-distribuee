package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface MonitorService extends Remote {
    // Récupérer la liste de tous les agents connectés
    List<AgentData> getAgents() throws RemoteException;
    
    // Récupérer les alertes récentes (messages)
    List<String> getAlerts() throws RemoteException;
    
    // Récupérer l'historique d'un agent (ou tous si agentId est vide)
    List<String[]> getHistory(String agentId, int maxRecords) throws RemoteException;
    
    // Récupérer les statistiques d'un agent
    Map<String, Double> getStatistics(String agentId) throws RemoteException;
}
