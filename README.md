# Distributed Monitoring System

A Java-based distributed system for monitoring agents that tracks CPU, memory, and disk usage across multiple agents with real-time alerts and visualization.

## 🌟 Features

- **Real-time Monitoring**: Track system metrics (CPU, RAM, Disk) from multiple agents
- **Multi-Protocol Communication**:
  - **UDP**: Regular status updates from agents
  - **TCP**: Critical alert notifications
  - **RMI**: Client-server communication for data retrieval
- **Role-Based Access Control**: Admin and Guest roles with different permissions
- **Alert System**: Automatic alerts when CPU usage exceeds threshold
- **Data Visualization**: 
  - Real-time table with progress bars
  - Sortable and searchable agent data
  - Alert history
- **Data Export**: CSV export functionality
- **History Management**: Persistent storage of agent data

## 📁 Project Structure

```
prjt/
├── src/
│   ├── agent/
│   │   └── MonitoringAgent.java       # Agent that monitors system metrics
│   ├── client/
│   │   ├── MonitorClient.java         # GUI client application
│   │   ├── LoginDialog.java           # User authentication dialog
│   │   └── ProgressBarRenderer.java   # Custom table cell renderer
│   ├── common/
│   │   ├── AgentData.java             # Data model for agent metrics
│   │   └── MonitorService.java        # RMI service interface
│   └── server/
│       ├── MonitorServer.java         # Server that collects agent data
│       └── HistoryManager.java        # Manages historical data storage
├── bin/                               # Compiled .class files
└── historique_agents.csv              # Agent history storage
```

## 🔧 Prerequisites

- Java Development Kit (JDK) 8 or higher
- Basic understanding of Java RMI, networking, and Swing

## 🚀 Getting Started

### 1. Compilation

Compile all Java files from the project root:

```bash
cd /home/abdo/vscode/java_guermah/prjt
javac -d bin src/common/*.java src/server/*.java src/agent/*.java src/client/*.java
```

### 2. Start the RMI Registry

Open a new terminal and start the RMI registry:

```bash
cd /home/abdo/vscode/java_guermah/prjt/bin
rmiregistry 1099
```

Leave this terminal running.

### 3. Start the Server

Open a new terminal and start the monitoring server:

```bash
cd /home/abdo/vscode/java_guermah/prjt/bin
java server.MonitorServer
```

The server will start listening on:
- **UDP Port 9876**: For agent updates
- **TCP Port 9877**: For critical alerts
- **RMI Port 1099**: For client connections

### 4. Start Agent(s)

Open new terminal(s) for each agent you want to run:

```bash
cd /home/abdo/vscode/java_guermah/prjt/bin
java agent.MonitoringAgent Agent1
```

You can start multiple agents with different IDs:
```bash
java agent.MonitoringAgent Agent2
java agent.MonitoringAgent Agent3
```

Each agent will:
- Generate random CPU, RAM, and disk metrics
- Send updates via UDP every 2 seconds
- Send TCP alerts when CPU exceeds 80%

### 5. Start the Client

Open a new terminal and start the client GUI:

```bash
cd /home/abdo/vscode/java_guermah/prjt/bin
java client.MonitorClient
```

**Login Credentials:**
- **Admin**: `admin` / `admin123`
  - Full access to all features
  - Can export data, view statistics, and access history
  
- **Guest**: `guest` / `guest123`
  - Read-only access
  - Limited feature set

## 📊 Client Interface Features

### Main Dashboard
- **Agent Table**: Displays all active agents with real-time metrics
  - Agent ID
  - CPU Usage (with progress bar)
  - Memory Usage (with progress bar)
  - Disk Usage (with progress bar)
  - Last Update timestamp
  - Status (Normal/Critical)

### Controls
- **Search**: Filter agents by ID
- **Threshold**: Adjust CPU alert threshold (Admin only)
- **Export**: Export data to CSV (Admin only)
- **Statistics**: View agent statistics (Admin only)
- **History**: View historical data (Admin only)
- **Refresh**: Manual data refresh

### Alert Panel
- Displays the 10 most recent alerts
- Automatically updates every 3 seconds

## 🔐 Architecture Overview

### Communication Protocols

1. **UDP (Agent → Server)**
   - Lightweight, fast updates
   - Sends `AgentData` objects every 2 seconds
   - Used for regular status updates

2. **TCP (Agent → Server)**
   - Reliable delivery for critical events
   - Triggered when CPU > 80%
   - Ensures alert messages are received

3. **RMI (Client ↔ Server)**
   - Remote method invocation
   - Client calls `getAgents()` and `getAlerts()`
   - Type-safe, object-oriented communication

### Data Flow
```
Agent 1 ──UDP──┐
Agent 2 ──UDP──┤
Agent 3 ──UDP──┼──→ Server ←──RMI──→ Client (GUI)
Agent N ──UDP──┘      ↑
                      │
                    TCP (Alerts)
```

## 🛠️ Configuration

### Port Configuration
Edit the following constants in the respective files if needed:

**MonitorServer.java:**
```java
private static final int UDP_PORT = 9876;
private static final int TCP_PORT = 9877;
private static final int RMI_PORT = 1099;
```

**MonitoringAgent.java:**
```java
private static final int UDP_PORT = 9876;
private static final int TCP_PORT = 9877;
```

### Adding New Users
Edit `LoginDialog.java` to add new users:
```java
users.put("newuser", new UserInfo("password", Role.ADMIN));
```

## 📝 CSV History Format

The `historique_agents.csv` file stores agent data with the following format:
```csv
Timestamp,AgentId,CPU,Memory,Disk
2026-01-03 14:30:45,Agent1,45.2,60.1,55.0
```

## 🐛 Troubleshooting

### Common Issues

1. **RMI Registry Not Found**
   - Ensure rmiregistry is running before starting the server
   - Make sure you're running it from the `bin` directory

2. **Connection Refused**
   - Check that the server is running
   - Verify firewall settings allow connections on ports 9876, 9877, and 1099

3. **ClassNotFoundException**
   - Ensure all files are compiled to the `bin` directory
   - Run commands from the `bin` directory

4. **Agent Not Appearing in Client**
   - Verify the agent is running and sending data
   - Check server console for incoming messages
   - Ensure correct server address in agent configuration

## 🔄 Refresh Rates

- **Agent Updates**: Every 2 seconds (UDP)
- **Client Refresh**: Every 3 seconds (auto-refresh)
- **Critical Alerts**: Immediate (TCP)

## 📈 Future Enhancements

- Add authentication via database
- Implement agent auto-discovery
- Add more system metrics (network, processes)
- Create REST API for web clients
- Add persistent alert logging
- Implement agent health monitoring
- Add dashboard visualization charts

## 👥 User Roles

| Feature | Admin | Guest |
|---------|-------|-------|
| View Agents | ✅ | ✅ |
| View Alerts | ✅ | ✅ |
| Search | ✅ | ✅ |
| Adjust Threshold | ✅ | ❌ |
| Export Data | ✅ | ❌ |
| View Statistics | ✅ | ❌ |
| View History | ✅ | ❌ |

## 📄 License

This project is for educational purposes.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

---

**Note**: This is a simulation system. Agents generate random data for demonstration purposes. In a production environment, agents would collect actual system metrics using platform-specific APIs (e.g., `OperatingSystemMXBean` for Java).
