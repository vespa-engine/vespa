package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.ConfigDefinitionDir;
import com.yahoo.config.application.XmlPreProcessor;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.Zone;
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
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Preprocesses a files application package.
 */
class ApplicationPackagePreprocessor {

    private final FilesApplicationPackage applicationPackage;
    private final TransformerFactory transformerFactory = XML.createTransformerFactory();
    private final File preprocessedDir;
    private final AppSubDirs appSubDirs;
    private final boolean includeSourceFiles;

    ApplicationPackagePreprocessor(FilesApplicationPackage applicationPackage,
                                   Optional<File> preprocessedDir,
                                   boolean includeSourceFiles) {
        this.applicationPackage = applicationPackage;
        this.includeSourceFiles = includeSourceFiles;
        this.preprocessedDir = preprocessedDir.orElse(FilesApplicationPackage.fileUnder(applicationPackage.getAppDir(),
                                                                                        Path.fromString(FilesApplicationPackage.preprocessed)));
        this.appSubDirs = new AppSubDirs(applicationPackage.getAppDir());
    }

    public ApplicationPackage preprocess(Zone zone) throws IOException {
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
        FilesApplicationPackage preprocessedApp = FilesApplicationPackage.fromFile(preprocessedDir, includeSourceFiles);
        copyUserDefsIntoApplication();
        return preprocessedApp;
    }

    private void preprocess(File appDir, File dir, Zone zone) throws IOException {
        validateServicesFile();
        IOUtils.copyDirectory(appDir, dir, - 1,
                              (__, name) -> ! List.of(FilesApplicationPackage.preprocessed,
                                                      ApplicationPackage.SERVICES,
                                                      ApplicationPackage.HOSTS,
                                                      ApplicationPackage.CONFIG_DEFINITIONS_DIR).contains(name));
        preprocessXML(FilesApplicationPackage.fileUnder(dir, Path.fromString(ApplicationPackage.SERVICES)), applicationPackage.getServicesFile(), zone);
        preprocessXML(FilesApplicationPackage.fileUnder(dir, Path.fromString(ApplicationPackage.HOSTS)), applicationPackage.getHostsFile(), zone);
    }

    private void preprocessXML(File destination, File inputXml, Zone zone) throws IOException {
        if ( ! inputXml.exists()) return;
        try {
            InstanceName instance = applicationPackage.getMetaData().getApplicationId().instance();
            Document document = new XmlPreProcessor(applicationPackage.getAppDir(),
                                                    inputXml,
                                                    instance,
                                                    zone.environment(),
                                                    zone.region(),
                                                    zone.cloud().name(),
                                                    applicationPackage.getDeploymentSpec().tags(instance, zone.environment()))
                                        .run();

            try (FileOutputStream outputStream = new FileOutputStream(destination)) {
                transformerFactory.newTransformer().transform(new DOMSource(document), new StreamResult(outputStream));
            }
        } catch (TransformerException | ParserConfigurationException | SAXException e) {
            throw new RuntimeException("Error preprocessing " + inputXml.getPath() + ": " + e.getMessage(), e);
        }
    }

    private void validateServicesFile() throws IOException {
        File servicesFile = applicationPackage.getServicesFile();
        if ( ! servicesFile.exists())
            throw new IllegalArgumentException(ApplicationPackage.SERVICES + " does not exist in application package. " +
                                               "There are " + filesInApplicationPackage() + " files in the directory");
        if (IOUtils.readFile(servicesFile).isEmpty())
            throw new IllegalArgumentException(ApplicationPackage.SERVICES + " in application package is empty. " +
                                               "There are " + filesInApplicationPackage() + " files in the directory");
    }

    private void copyUserDefsIntoApplication() {
        File destination = appSubDirs.configDefs();
        destination.mkdir();
        ConfigDefinitionDir defDir = new ConfigDefinitionDir(destination);
        // Copy the user's def files from components.
        List<Bundle> bundlesAdded = new ArrayList<>();
        for (Bundle bundle : applicationPackage.getBundles()) {
            defDir.addConfigDefinitionsFromBundle(bundle, bundlesAdded);
            bundlesAdded.add(bundle);
        }
    }

    private long filesInApplicationPackage() {
        return uncheck(() -> { try (var files = Files.list(applicationPackage.getAppDir().toPath())) { return files.count(); } });
    }

}
