package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface MonitorService extends Remote {
    // Récupérer la liste de tous les agents connectés
    List<AgentData> getAgents() throws RemoteException;
    
    // Récupérer les alertes récentes (messages)
    List<String> getAlerts() throws RemoteException;
}
