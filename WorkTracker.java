import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class WorkTracker {
    private static Map<String, LocalDateTime> startTimes = new HashMap<>();
    private static Map<String, Long> timeSpent = new HashMap<>();
    private static String[] categories;
    private static boolean displayMinutes;
    private static boolean autoSaveOnExit;
    private static LocalDateTime sessionStart;
    private static File currentSessionFile;
    private static final String WORK_LOGS_DIR = "work_logs";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static void main(String[] args) {
        loadConfig();
        checkPreviousSession();
        createUI();
        sessionStart = LocalDateTime.now();
        if (autoSaveOnExit) {
            Runtime.getRuntime().addShutdownHook(new Thread(WorkTracker::saveData));
        }
    }

    private static void loadConfig() {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get("config.txt"))) {
            properties.load(input);
            categories = properties.getProperty("categories").split(",");
            displayMinutes = Boolean.parseBoolean(properties.getProperty("displayMinutes"));
            autoSaveOnExit = Boolean.parseBoolean(properties.getProperty("autoSaveOnExit"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void checkPreviousSession() {
        String today = LocalDateTime.now().format(DATE_FORMAT);
        File dir = new File(WORK_LOGS_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] matchingFiles = dir.listFiles((d, name) -> name.startsWith("work_log_" + today));
            if (matchingFiles != null && matchingFiles.length > 0) {
                Arrays.sort(matchingFiles, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
                int response = JOptionPane.showConfirmDialog(null,
                        "A log file for today exists. Do you want to continue the previous session?",
                        "Continue Previous Session",
                        JOptionPane.YES_NO_OPTION);
                if (response == JOptionPane.YES_OPTION) {
                    importPreviousSession(matchingFiles[0]);
                }
            }
        }
    }

    private static void importPreviousSession(File file) {
        currentSessionFile = file;
        try (Scanner scanner = new Scanner(file)) {
            scanner.nextLine(); // Skip header
            while (scanner.hasNextLine()) {
                String[] line = scanner.nextLine().split(",");
                if (line.length == 2) {
                    String category = line[0];
                    long minutes = Long.parseLong(line[1]);
                    timeSpent.put(category, minutes);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createUI() {
        JFrame frame = new JFrame("Work Tracker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(categories.length + 1, 1));

        for (String category : categories) {
            JButton button = new JButton("Start " + category + (displayMinutes ? " (" + timeSpent.getOrDefault(category, 0L) + " min)" : ""));
            button.addActionListener(new CategoryButtonListener(category, button));
            frame.add(button);
        }

        JButton saveButton = new JButton("Save to CSV");
        saveButton.addActionListener(new SaveButtonListener());
        frame.add(saveButton);

        frame.setSize(300, (categories.length + 1) * 60);
        frame.setVisible(true);
    }

    private static class CategoryButtonListener implements ActionListener {
        private final String category;
        private final JButton button;

        public CategoryButtonListener(String category, JButton button) {
            this.category = category;
            this.button = button;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            LocalDateTime now = LocalDateTime.now();

            if (startTimes.containsKey(category)) {
                LocalDateTime startTime = startTimes.remove(category);
                long minutes = ChronoUnit.MINUTES.between(startTime, now);
                timeSpent.put(category, timeSpent.getOrDefault(category, 0L) + minutes);
                updateButtonText();
            } else {
                for (String cat : categories) {
                    if (startTimes.containsKey(cat)) {
                        LocalDateTime startTime = startTimes.remove(cat);
                        long minutes = ChronoUnit.MINUTES.between(startTime, now);
                        timeSpent.put(cat, timeSpent.getOrDefault(cat, 0L) + minutes);
                        updateButtonText(cat);
                    }
                }
                startTimes.put(category, now);
                updateButtonText();
            }
        }

        private void updateButtonText() {
            long minutes = timeSpent.getOrDefault(category, 0L);
            button.setText("Stop " + category + (displayMinutes ? " (" + minutes + " min)" : ""));
        }

        private void updateButtonText(String category) {
            for (Component component : button.getParent().getComponents()) {
                if (component instanceof JButton && ((JButton) component).getText().contains(category)) {
                    long minutes = timeSpent.getOrDefault(category, 0L);
                    ((JButton) component).setText("Start " + category + (displayMinutes ? " (" + minutes + " min)" : ""));
                }
            }
        }
    }

    private static class SaveButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            saveData();
            JOptionPane.showMessageDialog(null, "Data saved to " + WORK_LOGS_DIR);
        }
    }

    private static void saveData() {
        try {
            Files.createDirectories(Paths.get(WORK_LOGS_DIR));

            if (currentSessionFile == null) {
                LocalDateTime now = LocalDateTime.now();
                long sessionMinutes = ChronoUnit.MINUTES.between(sessionStart, now);
                String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String filename = String.format("work_log_%s_%dmin.csv", timestamp, sessionMinutes);
                currentSessionFile = new File(WORK_LOGS_DIR, filename);
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(currentSessionFile))) {
                writer.println("Category,TimeSpent(Minutes)");
                for (String category : categories) {
                    writer.println(category + "," + timeSpent.getOrDefault(category, 0L));
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
