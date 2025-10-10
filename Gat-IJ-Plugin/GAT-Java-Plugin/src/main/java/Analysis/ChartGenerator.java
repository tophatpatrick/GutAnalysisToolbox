package Analysis;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class ChartGenerator {

    public static void createSingleCellTypeChart(String csvPath, String cellType) throws Exception {
        // Read CSV and count frequencies
        Map<Integer, Integer> frequencyMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    int neighborCount = Integer.parseInt(parts[1].trim());
                    frequencyMap.put(neighborCount, frequencyMap.getOrDefault(neighborCount, 0) + 1);
                }
            }
        }

        // Create dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        int minNeighbors = frequencyMap.keySet().stream().min(Integer::compare).orElse(0);
        int maxNeighbors = frequencyMap.keySet().stream().max(Integer::compare).orElse(0);

        for (int i = minNeighbors; i <= maxNeighbors; i++) {
            dataset.addValue(frequencyMap.getOrDefault(i, 0), "Frequency", String.valueOf(i));
        }

        // Create chart
        JFreeChart chart = ChartFactory.createBarChart(
                "Neighbor Count Distribution - " + cellType,
                "Number of Neighbors",
                "Frequency (Number of Cells)",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );

        // Customize appearance
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(70, 130, 180)); // Steel blue
        renderer.setDrawBarOutline(true);
        renderer.setSeriesOutlinePaint(0, Color.BLACK);

        // Save chart
        File outputFile = new File(csvPath).getParentFile();
        String chartPath = outputFile.getAbsolutePath() + File.separator +
                "neighbor_count_barchart_" + cellType + ".png";
        ChartUtils.saveChartAsPNG(new File(chartPath), chart, 800, 600);
    }

    public static void createTwoCellTypeChart(String csvPath, String cellType1, String cellType2) throws Exception {
        // Read CSV and count frequencies for both cell types
        Map<Integer, Integer> freq1 = new HashMap<>();
        Map<Integer, Integer> freq2 = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    // Cell 1 neighbors
                    if (!parts[0].trim().isEmpty()) {
                        int count1 = Integer.parseInt(parts[1].trim());
                        freq1.put(count1, freq1.getOrDefault(count1, 0) + 1);
                    }
                    // Cell 2 neighbors
                    if (!parts[2].trim().isEmpty()) {
                        int count2 = Integer.parseInt(parts[3].trim());
                        freq2.put(count2, freq2.getOrDefault(count2, 0) + 1);
                    }
                }
            }
        }

        // Create dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        int minNeighbors = Math.min(
                freq1.keySet().stream().min(Integer::compare).orElse(0),
                freq2.keySet().stream().min(Integer::compare).orElse(0)
        );
        int maxNeighbors = Math.max(
                freq1.keySet().stream().max(Integer::compare).orElse(0),
                freq2.keySet().stream().max(Integer::compare).orElse(0)
        );

        for (int i = minNeighbors; i <= maxNeighbors; i++) {
            dataset.addValue(freq1.getOrDefault(i, 0), cellType1, String.valueOf(i));
            dataset.addValue(freq2.getOrDefault(i, 0), cellType2, String.valueOf(i));
        }

        // Create chart
        JFreeChart chart = ChartFactory.createBarChart(
                "Neighbor Count Distribution - " + cellType1 + " and " + cellType2,
                "Number of Neighbors",
                "Frequency (Number of Cells)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // Customize appearance
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(70, 130, 180)); // Steel blue
        renderer.setSeriesPaint(1, new Color(255, 127, 80)); // Coral
        renderer.setDrawBarOutline(true);
        renderer.setSeriesOutlinePaint(0, Color.BLACK);
        renderer.setSeriesOutlinePaint(1, Color.BLACK);

        // Save chart
        File outputFile = new File(csvPath).getParentFile();
        String chartPath = outputFile.getAbsolutePath() + File.separator +
                "neighbor_count_barchart_" + cellType1 + "_and_" + cellType2 +".png";
        ChartUtils.saveChartAsPNG(new File(chartPath), chart, 1000, 600);
    }
}
