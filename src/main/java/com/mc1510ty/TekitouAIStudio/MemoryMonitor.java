package com.mc1510ty.TekitouAIStudio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

public class MemoryMonitor extends JFrame {
    private JLabel heapLabel;
    private JProgressBar heapProgressBar;
    private JLabel nonHeapLabel;
    private JLabel totalLabel;

    private Timer timer;

    public MemoryMonitor() {
        setTitle("TekitouLLM - Memory Monitor");
        setSize(380, 240);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // メインパネルのレイアウト
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 1. ヒープメモリセクション（プログレスバー付き）
        panel.add(createSectionTitle("■ ヒープメモリ (Heap)"));
        heapLabel = new JLabel("測定中...");
        heapProgressBar = new JProgressBar(0, 1000000000);
        heapProgressBar.setStringPainted(true);
        panel.add(heapLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(heapProgressBar);
        panel.add(Box.createVerticalStrut(10));

        // 2. 非ヒープメモリセクション
        panel.add(createSectionTitle("■ 非ヒープメモリ (Non-Heap)"));
        nonHeapLabel = new JLabel("測定中...");
        panel.add(nonHeapLabel);
        panel.add(Box.createVerticalStrut(10));

        // 3. 合計メモリセクション
        panel.add(createSectionTitle("■ 合計使用量 (Total Used)"));
        totalLabel = new JLabel("測定中...");
        panel.add(totalLabel);

        add(panel);

        // 500ミリ秒ごとにデータを更新
        timer = new Timer(500, e -> updateMetrics());
        timer.start();
    }

    private JLabel createSectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setForeground(new Color(60, 120, 180));
        return label;
    }

    private void updateMetrics() {
        // ヒープメモリ
        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long heapUsed = heapUsage.getUsed() / (1024 * 1024);
        long heapMax = heapUsage.getMax() / (1024 * 1024);
        double heapPercent = heapUsage.getMax() > 0
                ? (double) heapUsage.getUsed() / heapUsage.getMax() * 100
                : 0;

        heapLabel.setText(String.format(
                "使用量: %d MB / 最大: %d MB", heapUsed, heapMax));

        heapProgressBar.setString(String.format("%.2f%%", heapPercent));
        heapProgressBar.setValue((int) Math.round(heapPercent * 10_000_000));

        // 非ヒープメモリ
        MemoryUsage nonHeapUsage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long nonHeapUsed = nonHeapUsage.getUsed() / (1024 * 1024);
        nonHeapLabel.setText(String.format("使用量: %d MB", nonHeapUsed));

        // 合計使用量
        long totalUsed = heapUsed + nonHeapUsed;
        totalLabel.setText(String.format("総消費量: %d MB", totalUsed));
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            new MemoryMonitor().setVisible(true);
        });
    }
}