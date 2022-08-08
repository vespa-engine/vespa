// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.component.Version;
import com.yahoo.io.IOUtils;
import org.osgi.framework.Bundle;
import org.xml.sax.SAXException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static java.nio.file.Files.createTempDirectory;
import static org.osgi.framework.FrameworkUtil.getBundle;

/**
 * Wrapper class for schema validators for application package xml files
 *
 * @author hmusum
 */
public class SchemaValidators {

    private static final String schemaDirBase = System.getProperty("java.io.tmpdir", File.separator + "tmp" + File.separator + "vespa");
    private static final Logger log = Logger.getLogger(SchemaValidators.class.getName());

    private static final String servicesXmlSchemaName = "services.rnc";
    private static final String hostsXmlSchemaName = "hosts.rnc";
    private static final String deploymentXmlSchemaName = "deployment.rnc";
    private static final String validationOverridesXmlSchemaName = "validation-overrides.rnc";
    private static final String containerIncludeXmlSchemaName = "container-include.rnc";
    private static final String routingStandaloneXmlSchemaName = "routing-standalone.rnc";


    private final SchemaValidator servicesXmlValidator;
    private final SchemaValidator hostsXmlValidator;
    private final SchemaValidator deploymentXmlValidator;
    private final SchemaValidator validationOverridesXmlValidator;
    private final SchemaValidator containerIncludeXmlValidator;
    private final SchemaValidator routingStandaloneXmlValidator;

    /**
     * Initializes the validator by using the given file as schema file
     *
     * @param vespaVersion the version of Vespa we should validate against
     */
    public SchemaValidators(Version vespaVersion) {
        File schemaDir = null;
        try {
            schemaDir = saveSchemasFromJar(new File(SchemaValidators.schemaDirBase), vespaVersion);
            servicesXmlValidator = createValidator(schemaDir, servicesXmlSchemaName);
            hostsXmlValidator = createValidator(schemaDir, hostsXmlSchemaName);
            deploymentXmlValidator = createValidator(schemaDir, deploymentXmlSchemaName);
            validationOverridesXmlValidator = createValidator(schemaDir, validationOverridesXmlSchemaName);
            containerIncludeXmlValidator = createValidator(schemaDir, containerIncludeXmlSchemaName);
            routingStandaloneXmlValidator = createValidator(schemaDir, routingStandaloneXmlSchemaName);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            if (schemaDir != null)
                IOUtils.recursiveDeleteDir(schemaDir);
        }
    }

    public SchemaValidator servicesXmlValidator() {
        return servicesXmlValidator;
    }

    public SchemaValidator hostsXmlValidator() {
        return hostsXmlValidator;
    }

    public SchemaValidator deploymentXmlValidator() {
        return deploymentXmlValidator;
    }

    SchemaValidator validationOverridesXmlValidator() {
        return validationOverridesXmlValidator;
    }

    SchemaValidator containerIncludeXmlValidator() {
        return containerIncludeXmlValidator;
    }

    SchemaValidator routingStandaloneXmlValidator() {
        return routingStandaloneXmlValidator;
    }

    /**
     * Looks for schema files in config-model.jar and saves them in a temp dir. Uses schema files
     * in $VESPA_HOME/share/vespa/schema/[major-version].x/ otherwise
     *
     * @return the directory the schema files are stored in
     * @throws IOException if it is not possible to read schema files
     */
    private File saveSchemasFromJar(File tmpBase, Version vespaVersion) throws IOException {
        Class<? extends SchemaValidators> schemaValidatorClass = this.getClass();
        Enumeration<URL> uris = schemaValidatorClass.getClassLoader().getResources("schema");
        if (uris == null) throw new IllegalArgumentException("Could not find XML schemas ");

        File tmpDir = createTempDirectory(tmpBase.toPath(), "vespa").toFile();
        log.log(Level.FINE, () -> "Will save all XML schemas for " + vespaVersion + " to " + tmpDir);
        boolean schemasFound = false;
        while (uris.hasMoreElements()) {
            URL u = uris.nextElement();
            // Used when building standalone-container
            if ("jar".equals(u.getProtocol())) {
                JarURLConnection jarConnection = (JarURLConnection) u.openConnection();
                JarFile jarFile = jarConnection.getJarFile();
                for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
                    JarEntry je = entries.nextElement();
                    if (je.getName().startsWith("schema/") && je.getName().endsWith(".rnc")) {
                        schemasFound = true;
                        writeContentsToFile(tmpDir, je.getName(), jarFile.getInputStream(je));
                    }
                }
                jarFile.close();
            } else if ("bundle".equals(u.getProtocol())) {
                Bundle bundle = getBundle(schemaValidatorClass);
                // Use schemas on disk when bundle is null (which is the case when using config-model-fat-amended.jar)
                if (bundle == null) {
                    String pathPrefix = getDefaults().underVespaHome("share/vespa/schema/");
                    File schemaPath = new File(pathPrefix + "version/" + vespaVersion.getMajor() + ".x/schema/");
                    // Fallback to path without version if path with version does not exist
                    if (! schemaPath.exists()) {
                        log.log(Level.INFO, "Found no schemas in " + schemaPath + ", fallback to schemas in " + pathPrefix);
                        schemaPath = new File(pathPrefix);
                    }
                    log.log(Level.FINE, "Using schemas found in " + schemaPath);
                    schemasFound = true;
                    copySchemas(schemaPath, tmpDir);
                } else {
                    log.log(Level.FINE, () -> String.format("Saving schemas for model bundle %s:%s", bundle.getSymbolicName(), bundle.getVersion()));
                    for (Enumeration<URL> entries = bundle.findEntries("schema", "*.rnc", true); entries.hasMoreElements(); ) {
                        URL url = entries.nextElement();
                        writeContentsToFile(tmpDir, url.getFile(), url.openStream());
                        schemasFound = true;
                    }
                }
            } else if ("file".equals(u.getProtocol())) { // Used when running unit tests
                File schemaPath = new File(u.getPath());
                copySchemas(schemaPath, tmpDir);
                schemasFound = true;
            }
        }

        if ( ! schemasFound) {
            IOUtils.recursiveDeleteDir(tmpDir);
            throw new IllegalArgumentException("Could not find schemas for version " + vespaVersion);
        }

        return tmpDir;
    }

    private static void copySchemas(File from, File to) throws IOException {
        if (! from.exists()) throw new IOException("Could not find schema source directory '" + from + "'");
        if (! from.isDirectory()) throw new IOException("Schema source '" + from + "' is not a directory");
        File sourceFile = new File(from, servicesXmlSchemaName);
        if (! sourceFile.exists()) throw new IOException("Schema source file '" + sourceFile + "' not found");
        IOUtils.copyDirectoryInto(from, to);
    }

    private static void writeContentsToFile(File outDir, String outFile, InputStream inputStream) throws IOException {
        String contents = IOUtils.readAll(new InputStreamReader(inputStream));
        File out = new File(outDir, outFile);
        IOUtils.writeFile(out, contents, false);
    }

    private SchemaValidator createValidator(File schemaDir, String schemaFile) {
        try {
            File file = new File(schemaDir + File.separator + "schema" + File.separator + schemaFile);
            return new SchemaValidator(file, new BaseDeployLogger());
        } catch (SAXException e) {
            throw new RuntimeException("Invalid schema '" + schemaFile + "'", e);
        } catch (IOException e) {
            throw new RuntimeException("IO error reading schema '" + schemaFile + "'", e);
        }
    }

}
