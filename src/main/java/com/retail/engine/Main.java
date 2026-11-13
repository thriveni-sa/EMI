package com.retail.engine;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standard Command-Line Application Entry Point for ECE-035.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("ECE-035: Electronics Retail No-Cost EMI & Cashback Offer Engine");
        System.out.println("================================================================");

        Path baseDir = Paths.get(".").toAbsolutePath().normalize();
        Path commandFile = (args.length > 0) ? Paths.get(args[0]) : baseDir.resolve("commands.txt");

        if (!commandFile.isAbsolute() && !java.nio.file.Files.exists(commandFile)) {
            Path inData = baseDir.resolve("data").resolve(commandFile.toString());
            if (java.nio.file.Files.exists(inData)) {
                commandFile = inData;
            }
        }

        try {
            System.out.println("Initializing Engine at workspace: " + baseDir);
            EngineBootstrap bootstrap = new EngineBootstrap(baseDir);
            bootstrap.initialize();

            System.out.println("Processing command stream: " + commandFile);
            long startTime = System.currentTimeMillis();
            bootstrap.processCommands(commandFile);
            bootstrap.exportRemainingReports();
            long elapsed = System.currentTimeMillis() - startTime;

            System.out.printf("Execution completed successfully in %d ms.\n", elapsed);
            System.out.println("Evaluation reports generated:");
            System.out.println(" - offer_summary.csv");
            System.out.println(" - emi_schedule.csv");
            System.out.println(" - returns_log.csv");
            System.out.println(" - cashback_report.csv");
        } catch (Exception e) {
            System.err.println("Fatal execution error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

