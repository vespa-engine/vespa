// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.ConfigDefinitionDir;
import com.yahoo.config.application.XmlPreProcessor;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.zone.ZoneInfo;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.text.XML;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Preprocesses a files application package.
 */
class ApplicationPackagePreprocessor {

    private final FilesApplicationPackage applicationPackage;
    private final TransformerFactory transformerFactory = XML.createTransformerFactory();
    private final File preprocessedDir;
    private final boolean includeSourceFiles;
    private final Map<String, FilesApplicationPackage> inheritableApplications;

    ApplicationPackagePreprocessor(FilesApplicationPackage applicationPackage,
                                   Optional<File> preprocessedDir,
                                   boolean includeSourceFiles,
                                   Map<String, FilesApplicationPackage> inheritableApplications) {
        this.applicationPackage = applicationPackage;
        this.includeSourceFiles = includeSourceFiles;
        this.preprocessedDir = preprocessedDir.orElse(FilesApplicationPackage.fileUnder(applicationPackage.getAppDir(),
                                                                                        Path.fromString(FilesApplicationPackage.preprocessed)));
        this.inheritableApplications = inheritableApplications;
    }

    public ApplicationPackage preprocess(ZoneInfo zone) throws IOException {
        java.nio.file.Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory(applicationPackage.getAppDir().getParentFile().toPath(), "preprocess-tempdir");
            preprocess(applicationPackage.getAppDir(), tempDir.toFile(), zone);
            IOUtils.recursiveDeleteDir(preprocessedDir);
            // Use 'move' to make sure we do this atomically, important to avoid writing only partial content e.g.
            // when shutting down.
            // Temp directory needs to be on the same file system as appDir for 'move' to work,
            // if it fails (with DirectoryNotEmptyException (!)) we need to use 'copy' instead
            // (this will always be the case for the application package for a standalone container).
            Files.move(tempDir, preprocessedDir.toPath());
            tempDir = null;
        } catch (AccessDeniedException | DirectoryNotEmptyException e) {
            preprocess(applicationPackage.getAppDir(), preprocessedDir, zone);
        } finally {
            if (tempDir != null)
                IOUtils.recursiveDeleteDir(tempDir.toFile());
        }
        FilesApplicationPackage preprocessedApp = FilesApplicationPackage.fromDir(preprocessedDir, includeSourceFiles,
                                                                                  inheritableApplications);
        copyUserDefsIntoApplication(preprocessedApp);
        return preprocessedApp;
    }

    private void preprocess(File appDir, File dir, ZoneInfo zone) throws IOException {
        validateServicesFile();
        IOUtils.copyDirectory(appDir, dir, - 1,
                              (parent, name) -> ! (List.of(FilesApplicationPackage.preprocessed,
                                                           ApplicationPackage.SERVICES,
                                                           ApplicationPackage.HOSTS,
                                                           ApplicationPackage.CONFIG_DEFINITIONS_DIR).contains(name)
                                                   || (parent.equals(appDir) && name.equals(ApplicationPackage.DEPLOYMENT_FILE.getName()))));
        Tags tags = tags(zone);
        preprocessXML(FilesApplicationPackage.fileUnder(dir, Path.fromString(ApplicationPackage.SERVICES)),
                      applicationPackage.applicationFile(ApplicationPackage.SERVICES), zone, tags);
        preprocessXML(FilesApplicationPackage.fileUnder(dir, Path.fromString(ApplicationPackage.HOSTS)),
                      applicationPackage.applicationFile(ApplicationPackage.HOSTS), zone, tags);
        preprocessXML(FilesApplicationPackage.fileUnder(dir, ApplicationPackage.DEPLOYMENT_FILE),
                      applicationPackage.applicationFile(ApplicationPackage.DEPLOYMENT_FILE), zone, tags);
    }

    /**
     * Returns the tags to use when preprocessing. Tags are declared in deployment.xml, which is itself
     * preprocessed, so first preprocess deployment.xml without tags to be able to read them.
     */
    private Tags tags(ZoneInfo zone) {
        File deploymentXml = applicationPackage.applicationFile(ApplicationPackage.DEPLOYMENT_FILE);
        if ( ! deploymentXml.exists()) return Tags.empty();

        Document document = preprocessXML(deploymentXml, zone, Tags.empty());
        StringWriter xml = new StringWriter();
        try {
            transformerFactory.newTransformer().transform(new DOMSource(document), new StreamResult(xml));
        } catch (TransformerException e) {
            throw new RuntimeException("Error preprocessing " + deploymentXml.getPath() + ": " + e.getMessage(), e);
        }
        DeploymentSpec spec = new DeploymentSpecXmlReader(false).read(xml.toString());
        return spec.tags(applicationPackage.getMetaData().getApplicationId().instance(), zone.environment());
    }

    private void preprocessXML(File destination, File inputXml, ZoneInfo zone, Tags tags) throws IOException {
        if ( ! inputXml.exists()) return;

        Document document = preprocessXML(inputXml, zone, tags);
        try (FileOutputStream outputStream = new FileOutputStream(destination)) {
            transformerFactory.newTransformer().transform(new DOMSource(document), new StreamResult(outputStream));
        } catch (TransformerException e) {
            throw new RuntimeException("Error preprocessing " + inputXml.getPath() + ": " + e.getMessage(), e);
        }
    }

    private Document preprocessXML(File inputXml, ZoneInfo zone, Tags tags) {
        try {
            ApplicationName application = applicationPackage.getMetaData().getApplicationId().application();
            InstanceName instance = applicationPackage.getMetaData().getApplicationId().instance();
            return new XmlPreProcessor(applicationPackage.getAppDir(),
                                       inputXml,
                                       application,
                                       instance,
                                       zone.environment(),
                                       zone.region(),
                                       zone.cloud(),
                                       tags)
                    .run();
        } catch (IOException | TransformerException | ParserConfigurationException | SAXException e) {
            throw new RuntimeException("Error preprocessing " + inputXml.getPath() + ": " + e.getMessage(), e);
        }
    }

    private void validateServicesFile() throws IOException {
        File servicesFile = applicationPackage.applicationFile(ApplicationPackage.SERVICES);
        if ( ! servicesFile.exists())
            throw new IllegalArgumentException(ApplicationPackage.SERVICES + " does not exist in application package. " +
                                               "There are " + filesInApplicationPackage() + " files in the directory");
        if (IOUtils.readFile(servicesFile).isEmpty())
            throw new IllegalArgumentException(ApplicationPackage.SERVICES + " in application package is empty. " +
                                               "There are " + filesInApplicationPackage() + " files in the directory");
    }

    private long filesInApplicationPackage() {
        return uncheck(() -> { try (var files = Files.list(applicationPackage.getAppDir().toPath())) { return files.count(); } });
    }

    private static void copyUserDefsIntoApplication(FilesApplicationPackage applicationPackage) {
        File destination = new AppSubDirs(applicationPackage.getAppDir()).configDefs();
        destination.mkdir();
        ConfigDefinitionDir defDir = new ConfigDefinitionDir(destination);
        // Copy the user's def files from components.
        List<Bundle> bundlesAdded = new ArrayList<>();
        for (Bundle bundle : applicationPackage.getBundles()) {
            defDir.addConfigDefinitionsFromBundle(bundle, bundlesAdded);
            bundlesAdded.add(bundle);
        }
    }

}
