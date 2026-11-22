package com.mc1510ty.TekitouAIStudio;

import java.io.IOException;

public class Launcher {
    public static void main(String[] args) throws IOException {
        MemoryMonitor.launch();
        new Main().main(args);
    }
}
