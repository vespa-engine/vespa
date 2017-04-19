// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validates xml files against one schema.
 *
 * @author tonytv
 */
public class SchemaValidator {

    public static final String schemaDirBase = System.getProperty("java.io.tmpdir", File.separator + "tmp" + File.separator + "vespa");
    static final String servicesXmlSchemaName = "services.rnc";
    static final String hostsXmlSchemaName = "hosts.rnc";
    static final String deploymentXmlSchemaName = "deployment.rnc";
    private final CustomErrorHandler errorHandler = new CustomErrorHandler();
    private final ValidationDriver driver;
    private DeployLogger deployLogger;
    private static final Logger log = Logger.getLogger(SchemaValidator.class.getName());

    /**
     * Initializes the validator by using the given file as schema file
     * @param schema a schema file in RNC format
     * @param logger a logger
     * @param vespaVersion the version of Vespa we should validate against
     */
    public SchemaValidator(String schema, DeployLogger logger, Version vespaVersion) {
        this.deployLogger = logger;
        driver = new ValidationDriver(PropertyMap.EMPTY, instanceProperties(), CompactSchemaReader.getInstance());
        File schemaDir = new File(schemaDirBase);
        try {
            schemaDir = saveSchemasFromJar(new File(SchemaValidator.schemaDirBase), vespaVersion);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        loadSchema(new File(schemaDir + File.separator + "schema" + File.separator + schema));
        IOUtils.recursiveDeleteDir(schemaDir);
    }

    /**
     * Initializes the validator by using the given file as schema file
     * @param schema a schema file in RNC format
     * @param vespaVersion the version we should validate against
     * @throws IOException if it is not possible to read schema files
     */
    public SchemaValidator(String schema, Version vespaVersion) throws IOException {
        this(schema, new BaseDeployLogger(), vespaVersion);
    }

    /**
     * Create a validator for services.xml for tests
     * @throws IOException if it is not possible to read schema files
     */
    public static SchemaValidator createTestValidatorServices(Version vespaVersion) throws IOException {
        return new SchemaValidator(servicesXmlSchemaName, vespaVersion);
    }

    /**
     * Create a validator for hosts.xml for tests
     * @throws IOException if it is not possible to read schema files
    */
    public static SchemaValidator createTestValidatorHosts(Version vespaVersion) throws IOException {
        return new SchemaValidator(hostsXmlSchemaName, vespaVersion);
    }

    /**
     * Create a validator for deployment.xml for tests
     *
     * @throws IOException if it is not possible to read schema files
     */
    public static SchemaValidator createTestValidatorDeployment(Version vespaVersion) throws IOException {
        return new SchemaValidator(deploymentXmlSchemaName, vespaVersion);
    }

    private class CustomErrorHandler implements ErrorHandler {
        volatile String fileName;

        public void warning(SAXParseException e) throws SAXException {
            deployLogger.log(Level.WARNING, message(e));
        }

        public void error(SAXParseException e) throws SAXException {
            throw new IllegalArgumentException(message(e));
        }

        public void fatalError(SAXParseException e) throws SAXException {
            throw new IllegalArgumentException(message(e));
        }

        private String message(SAXParseException e) {
            return "XML error in " + fileName + ": " +
                    Exceptions.toMessageString(e)
                    + " [" + e.getLineNumber() + ":" + e.getColumnNumber() + "]";
        }
    }

    /**
     * Look for the schema files that should be in vespa-model.jar and saves them on temp dir.
     *
     * @return the directory the schema files are stored in
     * @throws IOException if it is not possible to read schema files
     */
    private File saveSchemasFromJar(File tmpBase, Version vespaVersion) throws IOException {
        final Class<? extends SchemaValidator> schemaValidatorClass = this.getClass();
        final ClassLoader classLoader = schemaValidatorClass.getClassLoader();
        Enumeration<URL> uris = classLoader.getResources("schema");
        if (uris==null) return null;
        File tmpDir = java.nio.file.Files.createTempDirectory(tmpBase.toPath(), "vespa").toFile();
        log.log(LogLevel.DEBUG, "Saving schemas to " + tmpDir);
        while(uris.hasMoreElements()) {
            URL u = uris.nextElement();
            log.log(LogLevel.DEBUG, "uri for resource 'schema'=" + u.toString());
            if ("jar".equals(u.getProtocol())) {
                JarURLConnection jarConnection = (JarURLConnection) u.openConnection();
    			JarFile jarFile = jarConnection.getJarFile();
                for (Enumeration<JarEntry> entries = jarFile.entries();
                     entries.hasMoreElements();) {

                    JarEntry je=entries.nextElement();
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
                        schemaPath = new File(Defaults.getDefaults().vespaHome() + "share/vespa/schema/version/5.x/schema/");
                    } else {
                        schemaPath = new File(Defaults.getDefaults().vespaHome() + "share/vespa/schema/");
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

    private void loadSchema(File schemaFile) {
        try {
            driver.loadSchema(ValidationDriver.fileInputSource(schemaFile));
        } catch (SAXException e) {
            throw new RuntimeException("Invalid schema '" + schemaFile + "'", e);
        } catch (IOException e) {
            throw new RuntimeException("IO error reading schema '" + schemaFile + "'", e);
        }
    }

    private PropertyMap instanceProperties() {
        PropertyMapBuilder builder = new PropertyMapBuilder();
        builder.put(ValidateProperty.ERROR_HANDLER, errorHandler);
        return builder.toPropertyMap();
    }

    public void validate(File file) throws IOException {
        validate(file, file.getName());
    }

    public void validate(File file, String fileName) throws IOException {
        validate(ValidationDriver.fileInputSource(file), fileName);
    }

    public void validate(Reader reader) throws IOException {
        validate(new InputSource(reader), null);
    }

    public void validate(NamedReader reader) throws IOException {
        validate(new InputSource(reader), reader.getName());
    }

    public void validate(InputSource inputSource, String fileName)  throws IOException {
        errorHandler.fileName = (fileName == null ? " input" : fileName);
        try {
            if ( ! driver.validate(inputSource)) {
                // Shouldn't happen, error handler should have thrown
                throw new RuntimeException("Aborting due to earlier XML errors.");
            }
        } catch (SAXException e) {
            // This should never happen, as it is handled by the ErrorHandler
            // installed for the driver.
            throw new IllegalArgumentException(
                    "XML error in " + (fileName == null ? " input" : fileName) + ": " + Exceptions.toMessageString(e));
        }
    }
}
