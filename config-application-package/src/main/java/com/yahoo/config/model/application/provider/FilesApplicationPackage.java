// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.application.Xml;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.model.application.AbstractApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionBuilder;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Application package derived from local files, i.e. on deployment.
 * Construct using {@link com.yahoo.config.model.application.provider.FilesApplicationPackage#fromFile(java.io.File)} or
 * {@link com.yahoo.config.model.application.provider.FilesApplicationPackage#fromFileWithDeployData(java.io.File, DeployData)}.
 *
 * @author Vegard Havdal
 */
public class FilesApplicationPackage extends AbstractApplicationPackage {

    /**
     * The name of the subdirectory (below the original application package root)
     * where a preprocessed version of this application package is stored.
     * As it happens, the config model is first created with an application package in this subdirectory,
     * and later backed by an application package which is not in this subdirectory.
     * To enable model code to correct for this, this constant must be publicly known.
     *
     * All of this stuff is Very Unfortunate and should be fixed. -Jon
     */
    public static final String preprocessed = ".preprocessed";

    private static final String META_FILE_NAME = ".applicationMetaData";
    private static final Map<Path, Set<String>> validFileExtensions;

    private final File appDir;
    private final File configDefsDir;
    // NOTE: these directories exist in the original user app, but their locations are given in 'services.xml'
    private final List<String> userIncludeDirs = new ArrayList<>();
    private final ApplicationMetaData metaData;
    private final boolean includeSourceFiles;

    private ApplicationDefinition applicationDefinition;
    private final List<FilesApplicationPackage> inherited;
    private DeploymentSpec deploymentSpec = null;

    private final ApplicationPackagePreprocessor preprocessor;

    /**
     * New package from given path on local file system. Retrieves config definition files from
     * the default location '$VESPA_HOME/share/vespa/configdefinitions'.
     *
     * @param appDir application package directory
     * @param preprocessedDir preprocessed application package output directory
     * @param metaData metadata for this application package
     * @param includeSourceFiles include files from source dirs
     */
    private FilesApplicationPackage(File appDir,
                                    Optional<File> preprocessedDir,
                                    Optional<ApplicationMetaData> metaData,
                                    boolean includeSourceFiles) {
        verifyAppDir(appDir);
        this.appDir = appDir;
        this.includeSourceFiles = includeSourceFiles;
        this.applicationDefinition = new ApplicationDefinition.XmlReader().read(getApplicationDefinition());
        this.inherited = applicationDefinition.resolveInherited();
        this.metaData = metaData.orElse(readMetaData(appDir));
        configDefsDir = applicationFile(CONFIG_DEFINITIONS_DIR);
        addUserIncludeDirs();
        this.preprocessor = new ApplicationPackagePreprocessor(this, preprocessedDir, includeSourceFiles);
    }

    @Override
    public ApplicationId getApplicationId() { return metaData.getApplicationId(); }

    @Override
    public List<NamedReader> getFiles(Path relativePath, String suffix, boolean recurse) {
        return getFiles(relativePath, "", suffix, recurse);
    }

    @Override
    public ApplicationFile getFile(Path path) {
        File file = (path.isRoot() ? appDir : applicationFile(path.getRelative()));
        return new FilesApplicationFile(path, file);
    }

    public ApplicationFile getFileInThis(Path path) {
        File file = (path.isRoot() ? appDir : fileUnder(appDir, Path.fromString(path.getRelative())));
        return new FilesApplicationFile(path, file);
    }

    @Override
    public ApplicationMetaData getMetaData() { return metaData; }

    private List<NamedReader> getFiles(Path relativePath, String namePrefix, String suffix, boolean recurse) {
        try {
            File dir = applicationFile(relativePath);
            if ( ! dir.isDirectory()) return List.of();

            Set<NamedReader> readers = new LinkedHashSet<>();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        if (recurse)
                            readers.addAll(getFiles(relativePath.append(file.getName()), namePrefix + "/" + file.getName(), suffix, recurse));
                    } else {
                        if (suffix == null || file.getName().endsWith(suffix))
                            readers.add(new NamedReader(file.getName(), new FileReader(file)));
                    }
                }
            }

            for (var inheritedPackage : inherited)
                readers.addAll(inheritedPackage.getFiles(relativePath, namePrefix, suffix, recurse));

            return List.copyOf(readers);
        }
        catch (IOException e) {
            throw new RuntimeException("Could not open (all) files in '" + relativePath + "'",e);
        }
    }

    private void verifyAppDir(File appDir) {
        Objects.requireNonNull(appDir, "Path cannot be null");
        if ( ! appDir.exists())
            throw new IllegalArgumentException("Path '" + appDir + "' does not exist");
        if ( ! appDir.isDirectory())
            throw new IllegalArgumentException("Path '" + appDir + "' is not a directory");
        if (! appDir.canRead())
            throw new IllegalArgumentException("Cannot read from application directory '" + appDir + "'");
    }

    @Override
    public Reader getHosts() {
        try {
            File hostsFile = applicationFile(HOSTS);
            if (!hostsFile.exists()) return null;
            return new FileReader(hostsFile);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Optional<Reader> getApplicationDefinition() { return asOptionalReader(getFileInThis(APPLICATION_DEFINITION_FILE)); }

    @Override
    public Optional<Reader> getDeployment() { return asOptionalReader(getFile(DEPLOYMENT_FILE)); }

    @Override
    public Optional<Reader> getValidationOverrides() { return asOptionalReader(getFile(VALIDATION_OVERRIDES)); }

    private Optional<Reader> asOptionalReader(ApplicationFile file) {
        try {
            if ( ! file.exists()) return Optional.empty();
            return Optional.of(file.createReader());
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<String> getUserIncludeDirs() {
        return Collections.unmodifiableList(userIncludeDirs);
    }

    public void addUserIncludeDirs() {
        Document services;
        try {
            services = Xml.getDocument(getServices());
        } catch (Exception e) {
            return; // This method does not validate that services.xml exists, or that it is valid xml.
        }
        NodeList includeNodes = services.getElementsByTagName(IncludeDirs.INCLUDE);

        for (int i=0; i < includeNodes.getLength(); i++) {
            Node includeNode = includeNodes.item(i);
            addIncludeDir(includeNode);
        }
    }

    private void addIncludeDir(Node includeNode) {
        if (! (includeNode instanceof Element include)) return;
        if (! include.hasAttribute(IncludeDirs.DIR)) return;
        String dir = include.getAttribute(IncludeDirs.DIR);
        validateIncludeDir(dir);
        IncludeDirs.validateFilesInIncludedDir(dir, include.getParentNode(), this);
        userIncludeDirs.add(dir);
    }

    @Override
    public void validateIncludeDir(String dirName) {
        IncludeDirs.validateIncludeDir(dirName, this);
    }

    @Override
    public Collection<NamedReader> getSchemas() {
        Set<NamedReader> ret = new LinkedHashSet<>();
        try {
            for (File f : getSchemaFiles()) {
                ret.add(new NamedReader(f.getName(), new FileReader(f)));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Couldn't get schema contents.", e);
        }
        return ret;
    }

    /**
     * Creates a reader for a config definition
     *
     * @param defPath the path to the application package
     * @return the reader of this config definition
     */
    private Reader retrieveConfigDefReaderFromThis(File defPath) {
        try {
            return new NamedReader(defPath.getPath(), new FileReader(defPath));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read config definition file '" + defPath + "'", e);
        }
    }

    @Override
    public Map<ConfigDefinitionKey, UnparsedConfigDefinition> getAllExistingConfigDefs() {
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> defs = new LinkedHashMap<>();
        addAllDefsFromConfigDirInThis(defs, configDefsDir);
        if (includeSourceFiles) { // allow running from source, assuming mvn file project layout
            addAllDefsFromConfigDirInThis(defs, new File("src/main/resources/configdefinitions"));
            addAllDefsFromConfigDirInThis(defs, new File("src/test/resources/configdefinitions"));
        }
        addAllDefsFromBundles(defs, getBundles());

        for (var inheritedPackage : inherited) {
            inheritedPackage.addAllDefsFromConfigDirInThis(defs, configDefsDir);
            inheritedPackage.addAllDefsFromBundles(defs, getBundles());
        }
        return defs;
    }

    private void addAllDefsFromBundles(Map<ConfigDefinitionKey, UnparsedConfigDefinition> defs, List<Bundle> bundles) {
        for (Bundle bundle : bundles) {
            for (final Bundle.DefEntry def : bundle.getDefEntries()) {
                final ConfigDefinitionKey defKey = new ConfigDefinitionKey(def.defName, def.defNamespace);
                if (!defs.containsKey(defKey)) {
                    defs.put(defKey, new UnparsedConfigDefinition() {
                        @Override
                        public ConfigDefinition parse() {
                            DefParser parser = new DefParser(defKey.getName(), new StringReader(def.contents));
                            return ConfigDefinitionBuilder.createConfigDefinition(parser.getTree());
                        }

                        @Override
                        public String getUnparsedContent() {
                            return def.contents;
                        }
                    });
                }
            }
        }
    }

    private void addAllDefsFromConfigDirInThis(Map<ConfigDefinitionKey, UnparsedConfigDefinition> defs, File configDefsDir) {
        if (! configDefsDir.isDirectory()) return;

        for (File def : configDefsDir.listFiles((File dir, String name) -> name.matches(".*\\.def"))) {
            ConfigDefinitionKey key;
            try {
                key = ConfigUtils.createConfigDefinitionKeyFromDefFile(def);
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + def, e);
            }
            if (key.getNamespace().isEmpty())
                throw new IllegalArgumentException("Config definition '" + def + "' has no namespace");

            if (defs.containsKey(key)) continue; // first take precedence
            defs.put(key, new UnparsedConfigDefinition() {
                @Override
                public ConfigDefinition parse() {
                    DefParser parser = new DefParser(key.getName(), retrieveConfigDefReaderFromThis(def));
                    return ConfigDefinitionBuilder.createConfigDefinition(parser.getTree());
                }

                @Override
                public String getUnparsedContent() {
                    try (Reader reader = retrieveConfigDefReaderFromThis(def)) {
                        return IOUtils.readAll(reader);
                    } catch (IOException e) {
                        throw new RuntimeException("Error reading config definition '" + def + "'", e);
                    }
                }
            });
        }
    }

    @Override
    public Reader getServices() {
        try {
            return new FileReader(applicationFile(SERVICES).getPath());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public List<File> getSchemaFiles() {
        List<File> schemaFiles = new ArrayList<>();

        File sdDir = applicationFile(SEARCH_DEFINITIONS_DIR.getRelative());
        if (sdDir.isDirectory())
            schemaFiles.addAll(List.of(sdDir.listFiles((dir, name) -> validSchemaFilename(name))));

        sdDir = applicationFile(SCHEMAS_DIR.getRelative());
        if (sdDir.isDirectory())
            schemaFiles.addAll(List.of(sdDir.listFiles((dir, name) -> validSchemaFilename(name))));

        return schemaFiles;
    }

    // Only for use by deploy processor
    public List<Bundle> getBundles() {
        return Bundle.getBundles(applicationFile(COMPONENT_DIR));
    }

    private List<ComponentInfo> getComponentsInfo(File appDir) {
        return getBundles().stream()
                           .map(bundle -> new ComponentInfo(Path.fromString(COMPONENT_DIR).append(bundle.getFile().getName()).getRelative()))
                           .toList();
    }

    @Override
    public List<ComponentInfo> getComponentsInfo(Version vespaVersion) {
        return getComponentsInfo(appDir);
    }

    public File getAppDir() {
        try {
            return appDir.getCanonicalFile();
        }
        catch (IOException e) {
            throw new RuntimeException("Could not access " + appDir, e);
        }
    }

    private ApplicationMetaData readMetaData(File appDir) {
        String originalAppDir = preprocessed.equals(appDir.getName()) ? appDir.getParentFile().getName() : appDir.getName();
        ApplicationMetaData defaultMetaData = new ApplicationMetaData(0L,
                                                                      false,
                                                                      ApplicationId.from(TenantName.defaultName(),
                                                                                         ApplicationName.from(originalAppDir),
                                                                                         InstanceName.defaultName()),
                                                                      "",
                                                                      0L,
                                                                      0L);
        File metaFile = applicationFile(META_FILE_NAME);
        if ( ! metaFile.exists()) {
            return defaultMetaData;
        }
        try (FileReader reader = new FileReader(metaFile)) {
            return ApplicationMetaData.fromJsonString(IOUtils.readAll(reader));
        } catch (Exception e) {
            // Not a big deal, return default
            return defaultMetaData;
        }
    }

    /**
     * Reads a ranking expression from file to a string and returns it.
     *
     * @param name the name of the file to return,
     *             relative to the search definition directory in the application package
     * @return the content of a ranking expression file
     * @throws IllegalArgumentException if the file was not found or could not be read
     */
    @Override
    public Reader getRankingExpression(String name) {
        try {
            return IOUtils.createReader(expressionFileNameToFile(name), "utf-8");
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read ranking expression file '" + name + "'", e);
        }
    }

    private File expressionFileNameToFile(String name) {
        if (new File(name).isAbsolute())
            throw new IllegalArgumentException("Absolute path to ranking expression file is not allowed: " + name);

        Path path = Path.fromString(name);
        File expressionFile = applicationFile(SCHEMAS_DIR.append(path));
        if ( ! expressionFile.exists()) {
            expressionFile = applicationFile(SEARCH_DEFINITIONS_DIR.append(path));
        }
        return expressionFile;
    }

    @Override
    public File getFileReference(Path pathRelativeToAppDir) {
        return applicationFile(pathRelativeToAppDir.getRelative());
    }

    @Override
    public void validateXML() throws IOException {
        validateXMLFor(Optional.empty());
    }

    @Override
    public void validateXMLFor(Optional<Version> vespaVersion) throws IOException {
        Version modelVersion = vespaVersion.orElse(Vtag.currentVersion);
        ApplicationPackageXmlFilesValidator validator = ApplicationPackageXmlFilesValidator.create(appDir, modelVersion);
        validator.checkApplication();
        validator.checkIncludedDirs(this);
    }

    @Override
    public void writeMetaData() {
        File metaFile = applicationFile(META_FILE_NAME);
        IOUtils.writeFile(metaFile, metaData.asJsonBytes());
    }

    @Override
    public DeploymentSpec getDeploymentSpec() {
        if (deploymentSpec != null) return deploymentSpec;
        return deploymentSpec = parseDeploymentSpec(false);
    }

    @Override
    public ApplicationPackage preprocess(Zone zone, DeployLogger logger) throws IOException {
        return preprocessor.preprocess(zone);
    }

    File applicationFile(String path) {
        return applicationFile(Path.fromString(path));
    }

    /**
     * Returns this file from the first (depth first, left right) application package in the inheritance hierarchy
     * where it exists, or from this if it doesn't exist in any.
     */
    File applicationFile(Path path) {
        File file = fileUnder(appDir, path);
        if (file.exists()) return file;
        for (var inheritedPackage : inherited) {
            file = inheritedPackage.applicationFile(path);
            if (file.exists()) return file;
        }
        return fileUnder(appDir, path);
    }

    /* Validates that files in application dir and subdirectories have a known extension */
    public void validateFileExtensions() {
        validFileExtensions.forEach((subDir, __) -> validateInDir(subDir.toFile().toPath()));
    }

    private void validateInDir(java.nio.file.Path subDir) {
        java.nio.file.Path path = appDir.toPath().resolve(subDir);
        File subDirectory = path.toFile();
        if ( ! subDirectory.exists() || ! subDirectory.isDirectory()) return;

        try (var filesInPath = Files.list(path)) {
            filesInPath.forEach(filePath -> {
                if (filePath.toFile().isDirectory())
                    validateInDir(appDir.toPath().relativize(filePath));
                else
                    validateFileExtensions(filePath);
            });
        } catch (IOException e) {
            throw new RuntimeException("Unable to list files in '" + subDirectory + "'", e);
        }
    }

    private void validateFileExtensions(java.nio.file.Path pathToFile) {
        Set<String> allowedExtensions = findAllowedExtensions(appDir.toPath().relativize(pathToFile).getParent());
        String fileName = pathToFile.toFile().getName();
        if (allowedExtensions.stream().noneMatch(fileName::endsWith)) {
            String message = "File in application package with unknown extension: " +
                             appDir.toPath().relativize(pathToFile.getParent()).resolve(fileName) +
                             ", please delete or move file to another directory.";
            throw new IllegalArgumentException(message);
        }
    }

    private Set<String> findAllowedExtensions(java.nio.file.Path relativeDirectory) {
        Set<String> validExtensions = new HashSet<>();
        validExtensions.add(".gitignore");

        // Special case, since subdirs in schemas/ can have any name
        if (isSchemasSubDir(relativeDirectory))
            validExtensions.add(RANKPROFILE_NAME_SUFFIX);
        else
            validExtensions.addAll(validFileExtensions.entrySet().stream()
                                                      .filter(entry -> entry.getKey()
                                                                            .equals(Path.fromString(relativeDirectory.toString())))
                                                      .map(Map.Entry::getValue)
                                                      .findFirst()
                                                      .orElse(Set.of()));
        return validExtensions;
    }

    private boolean isSchemasSubDir(java.nio.file.Path relativeDirectory) {
        java.nio.file.Path schemasPath = SCHEMAS_DIR.toFile().toPath().getName(0);
        java.nio.file.Path searchDefinitionsPath = SEARCH_DEFINITIONS_DIR.toFile().toPath().getName(0);
        if (List.of(schemasPath, searchDefinitionsPath).contains(relativeDirectory)) return false;

        return (relativeDirectory.startsWith(schemasPath + "/")
                || relativeDirectory.startsWith(searchDefinitionsPath + "/"));
    }

    @Override
    public String toString() {
        return "application package '" + appDir + "'";
    }

    static File fileUnder(File root, Path path) {
        File file = new File(root, path.getRelative());
        if ( ! file.getAbsolutePath().startsWith(root.getAbsolutePath()))
            throw new IllegalArgumentException(file + " is not a child of " + root);
        return file;
    }

    static {
        // Note: Directories intentionally not validated: MODELS_DIR (custom models can contain files with any extension)

        // TODO: Files that according to doc (https://docs.vespa.ai/en/reference/schema-reference.html)
        //       can be anywhere in the application package:
        //       - constant tensors (.json, .json.lz4)
        //       - onnx model files (.onnx)
        validFileExtensions = Map.ofEntries(
                Map.entry(Path.fromString(COMPONENT_DIR), Set.of(".jar")),
                Map.entry(CONSTANTS_DIR, Set.of(".json", ".json.lz4")),
                Map.entry(Path.fromString(DOCPROCCHAINS_DIR), Set.of(".xml")),
                Map.entry(PAGE_TEMPLATES_DIR, Set.of(".xml")),
                Map.entry(Path.fromString(PROCESSORCHAINS_DIR), Set.of(".xml")),
                Map.entry(QUERY_PROFILES_DIR, Set.of(".xml")),
                Map.entry(QUERY_PROFILE_TYPES_DIR, Set.of(".xml")),
                Map.entry(Path.fromString(ROUTINGTABLES_DIR), Set.of(".xml")),
                Map.entry(RULES_DIR, Set.of(RULES_NAME_SUFFIX)),
                // Note: Might have rank profiles in subdirs: [schema-name]/[rank-profile].profile
                Map.entry(SCHEMAS_DIR, Set.of(SD_NAME_SUFFIX, RANKEXPRESSION_NAME_SUFFIX, RANKPROFILE_NAME_SUFFIX)),
                Map.entry(Path.fromString(SEARCHCHAINS_DIR), Set.of(".xml")),
                // Note: Might have rank profiles in subdirs: [schema-name]/[rank-profile].profile
                Map.entry(SEARCH_DEFINITIONS_DIR, Set.of(SD_NAME_SUFFIX, RANKEXPRESSION_NAME_SUFFIX, RANKPROFILE_NAME_SUFFIX)),
                Map.entry(SECURITY_DIR, Set.of(".pem")));
    }

    /** Creates from a directory with source files included */
    public static FilesApplicationPackage fromFile(File appDir) {
        return fromFile(appDir, false);
    }

    /**
     * Returns an application package object based on the given application dir
     *
     * @param includeSourceFiles read files from source directories /src/main and src/test in addition
     *                           to the application package location. This is useful during development
     *                           to be able to run tests without a complete build first.
     * @return an Application package instance
     */
    public static FilesApplicationPackage fromFile(File appDir, boolean includeSourceFiles) {
        return new Builder(appDir).preprocessedDir(fileUnder(appDir, Path.fromString(preprocessed)))
                                  .includeSourceFiles(includeSourceFiles)
                                  .build();
    }

    /** Creates package from a local directory, typically deploy app   */
    public static FilesApplicationPackage fromFileWithDeployData(File appDir, DeployData deployData) {
        return fromFileWithDeployData(appDir, deployData, false);
    }

    /** Creates package from a local directory, typically deploy app   */
    public static FilesApplicationPackage fromFileWithDeployData(File appDir,
                                                                 DeployData deployData,
                                                                 boolean includeSourceFiles) {
        return new Builder(appDir).includeSourceFiles(includeSourceFiles).deployData(deployData).build();
    }

    /**
     * Builder for {@link com.yahoo.config.model.application.provider.FilesApplicationPackage}. Use
     * this to create instances in a flexible manner.
     */
    public static class Builder {

        private final File appDir;
        private Optional<File> preprocessedDir = Optional.empty();
        private Optional<ApplicationMetaData> metaData = Optional.empty();
        private boolean includeSourceFiles = false;

        public Builder(File appDir) {
            this.appDir = appDir;
        }

        public Builder preprocessedDir(File preprocessedDir) {
            this.preprocessedDir = Optional.ofNullable(preprocessedDir);
            return this;
        }

        public Builder deployData(DeployData deployData) {
            this.metaData = Optional.of(deployData.toMetaData(appDir));
            return this;
        }

        public Builder includeSourceFiles(boolean includeSourceFiles) {
            this.includeSourceFiles = includeSourceFiles;
            return this;
        }

        public FilesApplicationPackage build() {
            return new FilesApplicationPackage(appDir, preprocessedDir, metaData, includeSourceFiles);
        }

    }

}
