package client;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {

    private int threshold = 80;

    public ProgressBarRenderer() {
        super(0, 100);
        setStringPainted(true);
    }
    
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        
        int progress = 0;
        if (value instanceof Number) {
            progress = ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                progress = (int) Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                progress = 0;
            }
        }

        setValue(progress);
        setString(progress + "%");

        // Couleur selon le niveau
        if (progress >= threshold) {
            setForeground(Color.RED);
        } else if (progress >= (threshold * 0.75)) {
            setForeground(Color.ORANGE);
        } else {
            setForeground(new Color(0, 150, 0)); // Vert
        }

        return this;
    }
}
