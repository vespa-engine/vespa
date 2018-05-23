// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
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
import java.util.logging.Logger;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

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


    private final DeployLogger deployLogger;

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
    public SchemaValidators(Version vespaVersion, DeployLogger logger) {
        this.deployLogger = logger;
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
        } catch (Exception e) {
            throw e;
        } finally {
            if (schemaDir != null)
                IOUtils.recursiveDeleteDir(schemaDir);
        }
    }

    /**
     * Initializes the validator by using the given file as schema file
     *
     * @param vespaVersion the version of Vespa we should validate against
     */
    public SchemaValidators(Version vespaVersion) {
        this(vespaVersion, new BaseDeployLogger());
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

    public SchemaValidator routingStandaloneXmlValidator() {
        return routingStandaloneXmlValidator;
    }

    /**
     * Look for the schema files that should be in vespa-model.jar and saves them on temp dir.
     *
     * @return the directory the schema files are stored in
     * @throws IOException if it is not possible to read schema files
     */
    File saveSchemasFromJar(File tmpBase, Version vespaVersion) throws IOException {
        final Class<? extends SchemaValidators> schemaValidatorClass = this.getClass();
        final ClassLoader classLoader = schemaValidatorClass.getClassLoader();
        Enumeration<URL> uris = classLoader.getResources("schema");
        if (uris == null) return null;
        File tmpDir = java.nio.file.Files.createTempDirectory(tmpBase.toPath(), "vespa").toFile();
        log.log(LogLevel.DEBUG, "Will save all XML schemas to " + tmpDir);
        while (uris.hasMoreElements()) {
            URL u = uris.nextElement();
            log.log(LogLevel.DEBUG, "uri for resource 'schema'=" + u.toString());
            if ("jar".equals(u.getProtocol())) {
                JarURLConnection jarConnection = (JarURLConnection) u.openConnection();
                JarFile jarFile = jarConnection.getJarFile();
                for (Enumeration<JarEntry> entries = jarFile.entries();
                     entries.hasMoreElements(); ) {

                    JarEntry je = entries.nextElement();
                    if (je.getName().startsWith("schema/") && je.getName().endsWith(".rnc")) {
                        writeContentsToFile(tmpDir, je.getName(), jarFile.getInputStream(je));
                    }
                }
                jarFile.close();
            } else if ("bundle".equals(u.getProtocol())) {
                Bundle bundle = FrameworkUtil.getBundle(schemaValidatorClass);
                log.log(LogLevel.DEBUG, classLoader.toString());
                log.log(LogLevel.DEBUG, "bundle=" + bundle);
                // TODO: Hack to handle cases where bundle=null
                if (bundle == null) {
                    File schemaPath;
                    if (vespaVersion.getMajor() == 5) {
                        schemaPath = new File(getDefaults().underVespaHome("share/vespa/schema/version/5.x/schema/"));
                    } else {
                        schemaPath = new File(getDefaults().underVespaHome("share/vespa/schema/"));
                    }
                    log.log(LogLevel.DEBUG, "Using schemas found in " + schemaPath);
                    copySchemas(schemaPath, tmpDir);
                } else {
                    log.log(LogLevel.DEBUG, String.format("Saving schemas for model bundle %s:%s", bundle.getSymbolicName(), bundle
                            .getVersion()));
                    for (Enumeration<URL> entries = bundle.findEntries("schema", "*.rnc", true);
                         entries.hasMoreElements(); ) {

                        URL url = entries.nextElement();
                        writeContentsToFile(tmpDir, url.getFile(), url.openStream());
                    }
                }
            } else if ("file".equals(u.getProtocol())) {
                File schemaPath = new File(u.getPath());
                copySchemas(schemaPath, tmpDir);
            }
        }
        return tmpDir;
    }

    // TODO: This only copies schema for services.xml. Why?
    private static void copySchemas(File from, File to) throws IOException {
        // TODO: only copy .rnc files.
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
            return new SchemaValidator(file, deployLogger);
        } catch (SAXException e) {
            throw new RuntimeException("Invalid schema '" + schemaFile + "'", e);
        } catch (IOException e) {
            throw new RuntimeException("IO error reading schema '" + schemaFile + "'", e);
        }
    }

}
