package com.mc1510ty.TekitouAIStudio;

import java.io.IOException;
import java.util.Arrays;

public class Launcher {
    public static void main(String[] args) throws IOException {
        if (!Arrays.asList(args).contains("--no-memory-monitor")) {
            MemoryMonitor.launch("TekitouWorldSimulator Client");
        }
        new Main().start(args);
    }
}