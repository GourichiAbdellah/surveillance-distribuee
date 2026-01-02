package common;

import java.io.Serializable;
import java.util.Date;

public class AgentData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String agentId;
    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private Date timestamp;
    private boolean isCritical;

    public AgentData(String agentId, double cpuUsage, double memoryUsage, double diskUsage) {
        this.agentId = agentId;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.diskUsage = diskUsage;
        this.timestamp = new Date();
        this.isCritical = false;
    }

    public String getAgentId() { return agentId; }
    public double getCpuUsage() { return cpuUsage; }
    public double getMemoryUsage() { return memoryUsage; }
    public double getDiskUsage() { return diskUsage; }
    public Date getTimestamp() { return timestamp; }
    public boolean isCritical() { return isCritical; }
    public void setCritical(boolean critical) { isCritical = critical; }

    @Override
    public String toString() {
        return String.format("Agent[%s] CPU: %.1f%%, MEM: %.1f%%", agentId, cpuUsage, memoryUsage);
    }
}
