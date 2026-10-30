import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class Main {
    private final JFrame frame = new JFrame("Tag Extractor");
    private final JLabel documentLabel = new JLabel("Document: (not selected)");
    private final JLabel stopWordsLabel = new JLabel("Stop words: (not selected)");
    private final JTextArea resultsArea = new JTextArea(24, 60);
    private Path documentPath;
    private Path stopWordsPath;
    private Map<String, Integer> frequencies;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().show());
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        JPanel filePanel = new JPanel(new BorderLayout(8, 4));
        JPanel fileLabels = new JPanel(new BorderLayout());
        fileLabels.add(documentLabel, BorderLayout.NORTH);
        fileLabels.add(stopWordsLabel, BorderLayout.SOUTH);
        filePanel.add(fileLabels, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton chooseDocumentButton = new JButton("Choose Document");
        JButton chooseStopWordsButton = new JButton("Choose Stop Words");
        JButton extractButton = new JButton("Extract Tags");
        JButton saveButton = new JButton("Save Results");
        buttons.add(chooseDocumentButton);
        buttons.add(chooseStopWordsButton);
        buttons.add(extractButton);
        buttons.add(saveButton);
        filePanel.add(buttons, BorderLayout.SOUTH);

        resultsArea.setEditable(false);
        resultsArea.setText("Choose a document and a stop-word file, then select Extract Tags.");
        frame.add(filePanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(resultsArea), BorderLayout.CENTER);

        chooseDocumentButton.addActionListener(event -> chooseDocument());
        chooseStopWordsButton.addActionListener(event -> chooseStopWords());
        extractButton.addActionListener(event -> extractTags());
        saveButton.addActionListener(event -> saveResults());

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void chooseDocument() {
        Path selected = chooseTextFile("Choose document to scan");
        if (selected != null) {
            documentPath = selected;
            documentLabel.setText("Document: " + selected.getFileName());
            clearResults();
        }
    }

    private void chooseStopWords() {
        Path selected = chooseTextFile("Choose stop-word file");
        if (selected != null) {
            stopWordsPath = selected;
            stopWordsLabel.setText("Stop words: " + selected.getFileName());
            clearResults();
        }
    }

    private Path chooseTextFile(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().toPath();
        }
        return null;
    }

    private void extractTags() {
        if (documentPath == null || stopWordsPath == null) {
            JOptionPane.showMessageDialog(frame,
                    "Choose both a document and a stop-word file first.",
                    "Files required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        frequencies = null;
        resultsArea.setText("Extracting tags...");
        try {
            Set<String> stopWords = loadStopWords(stopWordsPath);
            frequencies = countWords(documentPath, stopWords);
            resultsArea.setText(formatResults());
            resultsArea.setCaretPosition(0);
        } catch (IOException exception) {
            resultsArea.setText("Tag extraction failed.");
            JOptionPane.showMessageDialog(frame,
                    "Could not read the selected file:\n" + exception.getMessage(),
                    "File error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Set<String> loadStopWords(Path path) throws IOException {
        Set<String> stopWords = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                addWords(line, word -> stopWords.add(word));
            }
        }
        return stopWords;
    }

    private Map<String, Integer> countWords(Path path, Set<String> stopWords) throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                addWords(line, word -> {
                    if (!stopWords.contains(word)) {
                        counts.merge(word, 1, Integer::sum);
                    }
                });
            }
        }
        return counts;
    }

    private void addWords(String text, Consumer<String> consumer) {
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String word : normalized.split("[^\\p{L}]+")) {
            if (!word.isEmpty()) {
                consumer.accept(word);
            }
        }
    }

    private String formatResults() {
        StringBuilder report = new StringBuilder();
        report.append("Tags for: ").append(documentPath.getFileName()).append('\n');
        report.append("Word\tFrequency\n");

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(frequencies.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .reversed()
                .thenComparing(Map.Entry::getKey));

        for (Map.Entry<String, Integer> entry : entries) {
            report.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
        }
        if (entries.isEmpty()) {
            report.append("No tags found.");
        }
        return report.toString();
    }

    private void saveResults() {
        if (frequencies == null) {
            JOptionPane.showMessageDialog(frame,
                    "Extract tags before saving the results.",
                    "No results", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save extracted tags");
        chooser.setSelectedFile(new java.io.File("tags.txt"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8)) {
            writer.write(formatResults());
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(frame,
                    "Could not save the results:\n" + exception.getMessage(),
                    "File error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearResults() {
        frequencies = null;
        resultsArea.setText("Choose a document and a stop-word file, then select Extract Tags.");
    }
}
