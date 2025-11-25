// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.config.DocumenttypesConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.Schema;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Standalone tool to derive Vespa configuration files from a schema (.sd) file.
 * Usage: DeriveConfigFromSchema <schema-file.sd> [output-dir]
 * @author arnej
 */
public class DeriveConfigFromSchema {

    private static final Logger log = Logger.getLogger(DeriveConfigFromSchema.class.getName());

    private static class SimpleDeployLogger implements DeployLogger {
        @Override
        public void log(Level level, String message) {
            System.err.println(level + ": " + message);
        }
    }

    /**
     * Derive configuration from a single schema file or directory of schema files.
     *
     * @param schemaPath path to the .sd file or directory containing .sd files
     * @param outputDir directory where config files will be written
     * @throws IOException if file operations fail
     * @throws ParseException if schema parsing fails
     */
    public static void deriveFromFile(String schemaPath, String outputDir) throws IOException, ParseException {
        File input = new File(schemaPath);
        if (!input.exists()) {
            throw new IOException("Schema file or directory not found: " + schemaPath);
        }

        if (input.isDirectory()) {
            deriveFromDirectory(schemaPath, outputDir);
        } else {
            deriveFromSingleFile(schemaPath, outputDir);
        }
    }

    private static void deriveFromSingleFile(String schemaFile, String outputDir) throws IOException, ParseException {
        File schema = new File(schemaFile);
        if (!schema.getName().endsWith(".sd")) {
            throw new IOException("Schema file must have .sd extension: " + schemaFile);
        }

        // Create output directory
        File outDir = new File(outputDir);
        outDir.mkdirs();

        System.out.println("Processing schema: " + schemaFile);
        System.out.println("Output directory: " + outputDir);

        DeployLogger logger = new SimpleDeployLogger();
        TestProperties properties = new TestProperties();

        // Build the application from the schema file
        ApplicationBuilder builder = new ApplicationBuilder(logger);
        builder.addSchemaFile(schemaFile);
        builder.build(true);

        // Get the schema (there should be only one)
        Schema schema_obj = builder.getSchema();
        String schemaName = schema_obj.getName();
        System.out.println("Schema name: " + schemaName);

        // Create derived configuration
        DerivedConfiguration config = new DerivedConfiguration(
            new DeployState.Builder()
                .properties(properties)
                .deployLogger(logger)
                .rankProfileRegistry(builder.getRankProfileRegistry())
                .queryProfiles(builder.getQueryProfileRegistry())
                .build(),
            builder.getSchema(schemaName),
            SchemaInfo.IndexMode.INDEX
        );

        // Export all configuration files
        exportConfiguration(builder, config, outputDir);

        System.out.println("\nSuccessfully generated configuration files in: " + outputDir);
    }

    /**
     * Derive configuration from all schema files in a directory.
     * All schemas are loaded together (allowing inheritance between them),
     * and configs are generated for each schema in separate subdirectories.
     */
    private static void deriveFromDirectory(String schemaDir, String outputDir) throws IOException, ParseException {
        File dir = new File(schemaDir);

        // Find all .sd files in the directory
        File[] schemaFiles = dir.listFiles((d, name) -> name.endsWith(".sd"));
        if (schemaFiles == null || schemaFiles.length == 0) {
            throw new IOException("No .sd files found in directory: " + schemaDir);
        }

        System.out.println("Processing directory: " + schemaDir);
        System.out.println("Found " + schemaFiles.length + " schema file(s)");
        System.out.println("Output directory: " + outputDir);

        DeployLogger logger = new SimpleDeployLogger();
        TestProperties properties = new TestProperties();

        // Build the application from all schema files (so inheritance works)
        ApplicationBuilder builder = new ApplicationBuilder(logger);
        for (File schemaFile : schemaFiles) {
            System.out.println("  Loading: " + schemaFile.getName());
            builder.addSchemaFile(schemaFile.getAbsolutePath());
        }
        builder.build(true);

        System.out.println("\nGenerating configurations for " +
                          builder.application().schemas().size() +
                          " schema(s)");

        // Generate configs for each schema
        for (Schema schema : builder.application().schemas().values()) {
            String schemaName = schema.getName();
            String schemaOutputDir = outputDir + "/" + schemaName;

            System.out.println("\n--- Processing schema: " + schemaName + " ---");

            // Create output directory for this schema
            File outDir = new File(schemaOutputDir);
            outDir.mkdirs();

            // Create derived configuration
            DerivedConfiguration config = new DerivedConfiguration(
                new DeployState.Builder()
                    .properties(properties)
                    .deployLogger(logger)
                    .rankProfileRegistry(builder.getRankProfileRegistry())
                    .queryProfiles(builder.getQueryProfileRegistry())
                    .build(),
                schema,
                SchemaInfo.IndexMode.INDEX
            );

            // Export all configuration files
            exportConfiguration(builder, config, schemaOutputDir);
            System.out.println("Generated in: " + schemaOutputDir);
        }

        System.out.println("\n✓ Successfully processed all schemas in: " + schemaDir);
    }

    private static void exportConfiguration(ApplicationBuilder builder,
                                           DerivedConfiguration config,
                                           String outputDir) throws IOException {
        // Export the main derived configuration files
        config.export(outputDir);

        // Export document manager config
        DocumentmanagerConfig.Builder docManagerBuilder = new DocumentmanagerConfig.Builder();
        new DocumentManager().produce(builder.getModel(), docManagerBuilder);
        DocumentmanagerConfig docManagerConfig = docManagerBuilder.build();
        String docManagerCfg = docManagerConfig.toString();
        writeConfigFile(outputDir, "documentmanager.cfg", docManagerCfg);

        // Export document types config
        DocumenttypesConfig.Builder docTypesBuilder = new DocumenttypesConfig.Builder();
        new DocumentTypes().produce(builder.getModel(), docTypesBuilder);
        DocumenttypesConfig docTypesConfig = docTypesBuilder.build();
        String docTypesCfg = docTypesConfig.toString();
        writeConfigFile(outputDir, "documenttypes.cfg", docTypesCfg);

        // Export query profiles
        DerivedConfiguration.exportQueryProfiles(builder.getQueryProfileRegistry(), outputDir);

        // Export constants and ONNX models
        config.exportConstants(outputDir);
        config.exportOnnxModels(outputDir);

        System.out.println("\nGenerated configuration files:");
        listGeneratedFiles(new File(outputDir));
    }

    private static void writeConfigFile(String dir, String filename, String content) throws IOException {
        String path = dir + "/" + filename;
        IOUtils.writeFile(path, content, false);
        if (!content.endsWith("\n")) {
            IOUtils.writeFile(path, "\n", true);
        }
    }

    private static void listGeneratedFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile()) {
                System.out.println("  - " + file.getName());
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: DeriveConfigFromSchema <schema-file.sd | schema-dir> [output-dir]");
            System.err.println();
            System.err.println("Arguments:");
            System.err.println("  <schema-file.sd>  Path to a Vespa schema file to process");
            System.err.println("  <schema-dir>      Path to a directory containing .sd files");
            System.err.println("                    (all schemas loaded together for inheritance support)");
            System.err.println("  [output-dir]      Optional output directory (default: ./derived-config)");
            System.err.println();
            System.err.println("When a directory is provided:");
            System.err.println("  - All .sd files are loaded together (inheritance works)");
            System.err.println("  - Configs for each schema are generated in separate subdirectories");
            System.err.println();
            System.err.println("Examples:");
            System.err.println("  DeriveConfigFromSchema my-schema.sd");
            System.err.println("  DeriveConfigFromSchema /path/to/schema.sd /output/path");
            System.err.println("  DeriveConfigFromSchema /path/to/schemas/ /output/path");
            System.exit(1);
        }

        String schemaPath = args[0];
        String outputDir = args.length > 1 ? args[1] : "./derived-config";

        try {
            deriveFromFile(schemaPath, outputDir);
        } catch (IOException e) {
            System.err.println("ERROR: I/O error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (ParseException e) {
            System.err.println("ERROR: Failed to parse schema: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("ERROR: Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
