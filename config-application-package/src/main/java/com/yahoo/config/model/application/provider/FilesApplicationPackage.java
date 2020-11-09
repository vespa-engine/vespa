// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.application.ConfigDefinitionDir;
import com.yahoo.config.application.Xml;
import com.yahoo.config.application.XmlPreProcessor;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.io.HexDump;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionBuilder;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.text.Lowercase.toLowerCase;


/**
 * Application package derived from local files, ie. during deploy.
 * Construct using {@link com.yahoo.config.model.application.provider.FilesApplicationPackage#fromFile(java.io.File)} or
 * {@link com.yahoo.config.model.application.provider.FilesApplicationPackage#fromFileWithDeployData(java.io.File, DeployData)}.
 *
 * @author Vegard Havdal
 */
public class FilesApplicationPackage implements ApplicationPackage {

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

    private static final Logger log = Logger.getLogger(FilesApplicationPackage.class.getName());
    private static final String META_FILE_NAME = ".applicationMetaData";

    private final File appDir;
    private final File preprocessedDir;
    private final File configDefsDir;
    private final AppSubDirs appSubDirs;
    // NOTE: these directories exist in the original user app, but their locations are given in 'services.xml'
    private final List<String> userIncludeDirs = new ArrayList<>();
    private final ApplicationMetaData metaData;
    private final boolean includeSourceFiles;

    /** Creates from a directory with source files included */
    public static FilesApplicationPackage fromFile(File appDir) {
        return fromFile(appDir, false);
    }

    /**
     * Returns an application package object based on the given application dir
     *
     * @param appDir application package directory
     * @param includeSourceFiles read files from source directories /src/main and src/test in addition
     *                           to the application package location. This is useful during development
     *                           to be able to run tests without a complete build first.
     * @return an Application package instance
     */
    public static FilesApplicationPackage fromFile(File appDir, boolean includeSourceFiles) {
        return new Builder(appDir).preprocessedDir(new File(appDir, preprocessed))
                                  .includeSourceFiles(includeSourceFiles)
                                  .build();
    }

    /** Creates package from a local directory, typically deploy app   */
    public static FilesApplicationPackage fromFileWithDeployData(File appDir, DeployData deployData) {
        return fromFileWithDeployData(appDir, deployData, false);
    }

    /** Creates package from a local directory, typically deploy app   */
    public static FilesApplicationPackage fromFileWithDeployData(File appDir, DeployData deployData,
                                                                 boolean includeSourceFiles) {
        return new Builder(appDir).includeSourceFiles(includeSourceFiles).deployData(deployData).build();
    }

    private static ApplicationMetaData metaDataFromDeployData(File appDir, DeployData deployData) {
        return new ApplicationMetaData(deployData.getDeployedByUser(),
                                       deployData.getDeployedFromDir(),
                                       deployData.getDeployTimestamp(),
                                       deployData.isInternalRedeploy(),
                                       deployData.getApplicationId(),
                                       computeCheckSum(appDir),
                                       deployData.getGeneration(),
                                       deployData.getCurrentlyActiveGeneration());
    }

    /**
     * New package from given path on local file system. Retrieves config definition files from
     * the default location '$VESPA_HOME/share/vespa/configdefinitions'.
     *
     * @param appDir application package directory
     * @param preprocessedDir preprocessed application package output directory
     * @param metaData metadata for this application package
     * @param includeSourceFiles include files from source dirs
     */
    @SuppressWarnings("deprecation")
    private FilesApplicationPackage(File appDir, File preprocessedDir, ApplicationMetaData metaData, boolean includeSourceFiles) {
        verifyAppDir(appDir);
        this.includeSourceFiles = includeSourceFiles;
        this.appDir = appDir;
        this.preprocessedDir = preprocessedDir;
        appSubDirs = new AppSubDirs(appDir);
        configDefsDir = new File(appDir, ApplicationPackage.CONFIG_DEFINITIONS_DIR);
        addUserIncludeDirs();
        this.metaData = metaData;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getApplicationName() {
        return metaData.getApplicationId().application().value();
    }

    @Override
    public ApplicationId getApplicationId() { return metaData.getApplicationId(); }

    @Override
    public List<NamedReader> getFiles(Path relativePath, String suffix, boolean recurse) {
        return getFiles(relativePath, "", suffix, recurse);
    }

    @Override
    public ApplicationFile getFile(Path path) {
        File file = (path.isRoot() ? appDir : new File(appDir, path.getRelative()));
        return new FilesApplicationFile(path, file);
    }

    @Override
    public ApplicationMetaData getMetaData() {
        return metaData;
    }

    private List<NamedReader> getFiles(Path relativePath, String namePrefix, String suffix, boolean recurse) {
        try {
            List<NamedReader> readers=new ArrayList<>();
            File dir = new File(appDir, relativePath.getRelative());
            if ( ! dir.isDirectory()) return readers;

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
            return readers;
        }
        catch (IOException e) {
            throw new RuntimeException("Could not open (all) files in '" + relativePath + "'",e);
        }
    }

    private void verifyAppDir(File appDir) {
        if (appDir==null || !appDir.isDirectory()) {
            throw new IllegalArgumentException("Path '" + appDir + "' is not a directory.");
        }
        if (! appDir.canRead()){
            throw new IllegalArgumentException("Cannot read from application directory '" + appDir + "'");
        }
    }

    @Override
    public Reader getHosts() {
        try {
            File hostsFile = getHostsFile();
            if (!hostsFile.exists()) return null;
            return new FileReader(hostsFile);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String getHostSource() {
        return getHostsFile().getPath();
    }

    @SuppressWarnings("deprecation")
    private File getHostsFile() {
        return new File(appDir, ApplicationPackage.HOSTS);
    }

    @Override
    public String getServicesSource() {
        return getServicesFile().getPath();
    }

    @SuppressWarnings("deprecation")
    private File getServicesFile() {
        return new File(appDir, ApplicationPackage.SERVICES);
    }

    @Override
    public Optional<Reader> getDeployment() { return optionalFile(DEPLOYMENT_FILE); }

    @Override
    public Optional<Reader> getValidationOverrides() { return optionalFile(VALIDATION_OVERRIDES); }

    private Optional<Reader> optionalFile(Path filePath) {
        try {
            return Optional.of(getFile(filePath).createReader());
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
        if (! (includeNode instanceof Element))
            return;
        Element include = (Element) includeNode;
        if (! include.hasAttribute(IncludeDirs.DIR))
            return;
        String dir = include.getAttribute(IncludeDirs.DIR);
        validateIncludeDir(dir);
        IncludeDirs.validateFilesInIncludedDir(dir, include.getParentNode(), this);
        log.log(Level.FINE, "Adding user include dir '" + dir + "'");
        userIncludeDirs.add(dir);
    }

    @Override
    public void validateIncludeDir(String dirName) {
        IncludeDirs.validateIncludeDir(dirName, this);
    }

    @Override
    public Collection<NamedReader> searchDefinitionContents() {
        Set<NamedReader> ret = new LinkedHashSet<>();
        try {
            for (File f : getSearchDefinitionFiles()) {
                ret.add(new NamedReader(f.getName(), new FileReader(f)));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Couldn't get search definition contents.", e);
        }
        return ret;
    }

    /**
     * Creates a reader for a config definition
     *
     * @param defPath the path to the application package
     * @return the reader of this config definition
     */
    private Reader retrieveConfigDefReader(File defPath) {
        try {
            return new NamedReader(defPath.getPath(), new FileReader(defPath));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read config definition file '" + defPath + "'", e);
        }
    }

    @Override
    public Map<ConfigDefinitionKey, UnparsedConfigDefinition> getAllExistingConfigDefs() {
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> defs = new LinkedHashMap<>();
        addAllDefsFromConfigDir(defs, configDefsDir);
        if (includeSourceFiles) { // allow running from source, assuming mvn file project layout
            addAllDefsFromConfigDir(defs, new File("src/main/resources/configdefinitions"));
            addAllDefsFromConfigDir(defs, new File("src/test/resources/configdefinitions"));
        }
        addAllDefsFromBundles(defs, FilesApplicationPackage.getComponents(appDir));
        return defs;
    }

    private void addAllDefsFromBundles(Map<ConfigDefinitionKey, UnparsedConfigDefinition> defs, List<Component> components) {
        for (Component component : components) {
            Bundle bundle = component.getBundle();
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

    private void addAllDefsFromConfigDir(Map<ConfigDefinitionKey, UnparsedConfigDefinition> defs, File configDefsDir) {
        if (! configDefsDir.isDirectory()) return;

        log.log(Level.FINE, "Getting all config definitions from '" + configDefsDir + "'");
        for (File def : configDefsDir.listFiles((File dir, String name) -> name.matches(".*\\.def"))) {
            String[] nv = def.getName().split("\\.def");
            ConfigDefinitionKey key;
            try {
                key = ConfigUtils.createConfigDefinitionKeyFromDefFile(def);
            } catch (IOException e) {
                e.printStackTrace(); // TODO: Fix
                break;
            }
            if (key.getNamespace().isEmpty())
                throw new IllegalArgumentException("Config definition '" + def + "' has no namespace");

            if (defs.containsKey(key)) {
                if (nv[0].contains(".")) {
                    log.log(Level.INFO, "Two config definitions found for the same name and namespace: " + key +
                                           ". The file '" + def + "' will take precedence");
                } else {
                    log.log(Level.INFO, "Two config definitions found for the same name and namespace: " + key +
                                           ". Skipping '" + def + "', as it does not contain namespace in filename");
                    continue; // skip
                }
            }

            defs.put(key, new UnparsedConfigDefinition() {
                @Override
                public ConfigDefinition parse() {
                    DefParser parser = new DefParser(key.getName(), retrieveConfigDefReader(def));
                    return ConfigDefinitionBuilder.createConfigDefinition(parser.getTree());
                }

                @Override
                public String getUnparsedContent() {
                    return readConfigDefinition(def);
                }
            });
        }
    }

    private String readConfigDefinition(File defPath) {
        try (Reader reader = retrieveConfigDefReader(defPath)) {
            return IOUtils.readAll(reader);
        } catch (IOException e) {
            throw new RuntimeException("Error reading config definition '" + defPath + "'", e);
        }
    }

    @Override
    public Reader getServices() {
        try {
            return new FileReader(getServicesSource());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    static List<File> getSearchDefinitionFiles(File appDir) {
        List<File> schemaFiles = new ArrayList<>();

        File sdDir = new File(appDir, ApplicationPackage.SEARCH_DEFINITIONS_DIR.getRelative());
        if (sdDir.isDirectory())
            schemaFiles.addAll(Arrays.asList(sdDir.listFiles((dir, name) -> name.matches(".*\\" + ApplicationPackage.SD_NAME_SUFFIX))));

        sdDir = new File(appDir, ApplicationPackage.SCHEMAS_DIR.getRelative());
        if (sdDir.isDirectory())
            schemaFiles.addAll(Arrays.asList(sdDir.listFiles((dir, name) -> name.matches(".*\\" + ApplicationPackage.SD_NAME_SUFFIX))));

        return schemaFiles;
    }

    public List<File> getSearchDefinitionFiles() {
        return getSearchDefinitionFiles(appDir);
    }

    // Only for use by deploy processor
    public static List<Component> getComponents(File appDir) {
        List<Component> components = new ArrayList<>();
        for (Bundle bundle : Bundle.getBundles(new File(appDir, ApplicationPackage.COMPONENT_DIR))) {
            components.add(new Component(bundle, new ComponentInfo(new File(ApplicationPackage.COMPONENT_DIR, bundle.getFile().getName()).getPath())));
        }
        return components;
    }

    private static List<ComponentInfo> getComponentsInfo(File appDir) {
        List<ComponentInfo> components = new ArrayList<>();
        for (Bundle bundle : Bundle.getBundles(new File(appDir, ApplicationPackage.COMPONENT_DIR))) {
            components.add(new ComponentInfo(new File(ApplicationPackage.COMPONENT_DIR, bundle.getFile().getName()).getPath()));
        }
        return components;
    }

    @Override
    public List<ComponentInfo> getComponentsInfo(Version vespaVersion) {
        return getComponentsInfo(appDir);
    }

    /**
     * Returns a list of all components in this package.
     *
     * @return A list of components.
     */
    public List<Component> getComponents() {
        return getComponents(appDir);
    }

    public File getAppDir() throws IOException {
        return appDir.getCanonicalFile();
    }

    private static ApplicationMetaData readMetaData(File appDir) {
        ApplicationMetaData defaultMetaData = new ApplicationMetaData("n/a",
                                                                      "n/a",
                                                                      0L,
                                                                      false,
                                                                      ApplicationId.from(TenantName.defaultName(),
                                                                                         ApplicationName.from(appDir.getName()),
                                                                                         InstanceName.defaultName()),
                                                                      "",
                                                                      0L,
                                                                      0L);
        File metaFile = new File(appDir, META_FILE_NAME);
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
     * Represents a component in the application package. Immutable.
     */
    public static class Component {

        public final ComponentInfo info;
        private final Bundle bundle;

        public Component(Bundle bundle, ComponentInfo info) {
            this.bundle = bundle;
            this.info = info;
        }

        public List<Bundle.DefEntry> getDefEntries() {
            return bundle.getDefEntries();
        }

        public Bundle getBundle() {
            return bundle;
        }

    } // class Component

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

        File sdDir = new File(appDir, ApplicationPackage.SCHEMAS_DIR.getRelative());
        File expressionFile = new File(sdDir, name);
        if ( ! expressionFile.exists()) {
            sdDir = new File(appDir, ApplicationPackage.SEARCH_DEFINITIONS_DIR.getRelative());
            expressionFile = new File(sdDir, name);
        }
        return expressionFile;
    }

    @Override
    public File getFileReference(Path pathRelativeToAppDir) {
        return new File(appDir, pathRelativeToAppDir.getRelative());
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
    public void writeMetaData() throws IOException {
        File metaFile = new File(appDir, META_FILE_NAME);
        IOUtils.writeFile(metaFile, metaData.asJsonBytes());
    }

    @Override
    public Collection<NamedReader> getSearchDefinitions() {
        return searchDefinitionContents();
    }

    private void preprocessXML(File destination, File inputXml, Zone zone) throws IOException {
        if ( ! inputXml.exists()) return;
        try {
            Document document = new XmlPreProcessor(appDir,
                                                    inputXml,
                                                    metaData.getApplicationId().instance(),
                                                    zone.environment(),
                                                    zone.region()).run();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            try (FileOutputStream outputStream = new FileOutputStream(destination)) {
                transformer.transform(new DOMSource(document), new StreamResult(outputStream));
            }
        } catch (TransformerException |ParserConfigurationException | SAXException e) {
            // TODO: 2020-11-09, debugging what seems like missing or empty file while preprocessing. Remove afterwards
            if (inputXml.canRead()) {
                log.log(Level.WARNING, "Error preprocessing " + inputXml.getAbsolutePath() + ", file content: " + IOUtils.readFile(inputXml));
                java.nio.file.Path dir = inputXml.getParentFile().toPath();
                log.log(Level.INFO, "Files in " + dir + ":" + Files.list(dir).map(java.nio.file.Path::toString).collect(Collectors.joining(",")));
            }
            throw new RuntimeException("Error preprocessing " + inputXml.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public ApplicationPackage preprocess(Zone zone, DeployLogger logger) throws IOException {
        IOUtils.recursiveDeleteDir(preprocessedDir);
        IOUtils.copyDirectory(appDir, preprocessedDir, -1, (dir, name) -> ! name.equals(preprocessed) &&
                                                                                         ! name.equals(SERVICES) &&
                                                                                         ! name.equals(HOSTS) &&
                                                                                         ! name.equals(CONFIG_DEFINITIONS_DIR));
        preprocessXML(new File(preprocessedDir, SERVICES), getServicesFile(), zone);
        preprocessXML(new File(preprocessedDir, HOSTS), getHostsFile(), zone);
        FilesApplicationPackage preprocessed = FilesApplicationPackage.fromFile(preprocessedDir, includeSourceFiles);
        preprocessed.copyUserDefsIntoApplication();
        return preprocessed;
    }

    private void copyUserDefsIntoApplication() {
        File destination = appSubDirs.configDefs();
        destination.mkdir();
        ConfigDefinitionDir defDir = new ConfigDefinitionDir(destination);
        // Copy the user's def files from components.
        List<Bundle> bundlesAdded = new ArrayList<>();
        for (FilesApplicationPackage.Component component : FilesApplicationPackage.getComponents(appSubDirs.root())) {
            Bundle bundle = component.getBundle();
            defDir.addConfigDefinitionsFromBundle(bundle, bundlesAdded);
            bundlesAdded.add(bundle);
        }
    }

    /**
     * Computes an md5 hash of the contents of the application package
     *
     * @return an md5sum of the application package
     */
    private static String computeCheckSum(File appDir) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            for (File file : appDir.listFiles((dir, name) -> !name.equals(ApplicationPackage.EXT_DIR) && !name.startsWith("."))) {
                addPathToDigest(file, "", md5, true, false);
            }
            return toLowerCase(HexDump.toHexString(md5.digest()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Adds the given path to the digest, or does nothing if path is neither file nor dir
     *
     * @param path path to add to message digest
     * @param suffix only files with this suffix are considered
     * @param digest the {link @MessageDigest} to add the file paths to
     * @param recursive whether to recursively find children in the paths
     * @param fullPathNames whether to include the full paths in checksum or only the names
     * @throws java.io.IOException if adding path to digest fails when reading files from path
     */
    private static void addPathToDigest(File path, String suffix, MessageDigest digest, boolean recursive, boolean fullPathNames) throws IOException {
        if (!path.exists()) return;
        if (fullPathNames) {
            digest.update(path.getPath().getBytes(Utf8.getCharset()));
        } else {
            digest.update(path.getName().getBytes(Utf8.getCharset()));
        }
        if (path.isFile()) {
            FileInputStream is = new FileInputStream(path);
            addToDigest(is, digest);
            is.close();
        } else if (path.isDirectory()) {
            final File[] files = path.listFiles();
            if (files != null) {
                for (File elem : files) {
                    if ((elem.isDirectory() && recursive) || elem.getName().endsWith(suffix)) {
                        addPathToDigest(elem, suffix, digest, recursive, fullPathNames);
                    }
                }
            }
        }
    }

    private static final int MD5_BUFFER_SIZE = 65536;

    private static void addToDigest(InputStream is, MessageDigest digest) throws IOException {
        if (is == null) return;
        byte[] buffer = new byte[MD5_BUFFER_SIZE];
        int i;
        do {
            i = is.read(buffer);
            if (i > 0) {
                digest.update(buffer, 0, i);
            }
        } while(i != -1);
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
            this.metaData = Optional.of(metaDataFromDeployData(appDir, deployData));
            return this;
        }

        public Builder includeSourceFiles(boolean includeSourceFiles) {
            this.includeSourceFiles = includeSourceFiles;
            return this;
        }

        public FilesApplicationPackage build() {
            return new FilesApplicationPackage(appDir, preprocessedDir.orElse(new File(appDir, preprocessed)),
                                               metaData.orElse(readMetaData(appDir)), includeSourceFiles);
        }

    }

}
