// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.google.common.base.Joiner;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.model.application.AbstractApplicationPackage;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.serialization.AllocatedHostsSerializer;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionBuilder;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.server.filedistribution.AddFileInterface;
import com.yahoo.vespa.config.server.filedistribution.FileDBRegistry;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.curator.Curator;

import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.DEFCONFIGS_ZK_SUBPATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USERAPP_ZK_SUBPATH;

/**
 * Represents an application residing in zookeeper.
 *
 * @author Tony Vaagenes
 */
public class ZKApplicationPackage extends AbstractApplicationPackage {

    private final ZKApplication zkApplication;

    private final Map<Version, FileRegistry> fileRegistryMap = new HashMap<>();
    private final Optional<AllocatedHosts> allocatedHosts;

    public static final String fileRegistryNode = "fileregistry";
    public static final String allocatedHostsNode = "allocatedHosts";
    private final ApplicationMetaData metaData;

    private DeploymentSpec deploymentSpec = null;

    public ZKApplicationPackage(AddFileInterface fileManager, Curator curator, Path sessionPath, int maxNodeSize) {
        verifyAppPath(curator, sessionPath);
        zkApplication = new ZKApplication(curator, sessionPath, maxNodeSize);
        metaData = readMetaDataFromActiveApp(zkApplication);
        importFileRegistries(fileManager);
        allocatedHosts = importAllocatedHosts();
    }

    // For testing
    ZKApplicationPackage(AddFileInterface fileManager, Curator curator, Path sessionPath) {
        this(fileManager, curator, sessionPath, 10 * 1024 * 1024);
    }

    private Optional<AllocatedHosts> importAllocatedHosts() {
        if ( ! zkApplication.exists(Path.fromString(allocatedHostsNode))) return Optional.empty();
        return Optional.of(readAllocatedHosts());
    }

    @Override
    public DeploymentSpec getDeploymentSpec() {
        if (deploymentSpec != null) return deploymentSpec;
        return deploymentSpec = parseDeploymentSpec(false);
    }

    /**
     * Reads allocated hosts at the given node.
     *
     * @return the allocated hosts at this node or empty if there is no data at this path
     */
    private AllocatedHosts readAllocatedHosts() {
        try {
            return AllocatedHostsSerializer.fromJson(zkApplication.getBytes(Path.fromString(allocatedHostsNode)));
        } catch (Exception e) {
            throw new RuntimeException("Unable to read allocated hosts", e);
        }
    }

    private void importFileRegistries(AddFileInterface fileManager) {
        List<String> perVersionFileRegistryNodes = zkApplication.getChildren(Path.fromString(fileRegistryNode));
        perVersionFileRegistryNodes
                .forEach(version -> fileRegistryMap.put(Version.fromString(version),
                                                        importFileRegistry(fileManager, Joiner.on("/").join(fileRegistryNode, version))));
    }

    private FileRegistry importFileRegistry(AddFileInterface fileManager, String fileRegistryNode) {
        try {
            return FileDBRegistry.create(fileManager, zkApplication.getDataReader(Path.fromString(fileRegistryNode)));
        } catch (Exception e) {
            throw new RuntimeException("Could not determine which files to distribute", e);
        }
    }

    private ApplicationMetaData readMetaDataFromActiveApp(ZKApplication activeApp) {
        Path metaPath = Path.fromString(ZKApplication.META_ZK_PATH);
        String metaDataString = activeApp.getData(metaPath);
        if (metaDataString == null || metaDataString.isEmpty()) {
            return null;
        }
        return ApplicationMetaData.fromJsonString(activeApp.getData(metaPath));
    }

    @Override
    public ApplicationMetaData getMetaData() {
        return metaData;
    }

    private static void verifyAppPath(Curator zk, Path appPath) {
        if (!zk.exists(appPath))
            throw new RuntimeException("App with path " + appPath + " does not exist");
    }

    @Override
    public ApplicationId getApplicationId() { return metaData.getApplicationId(); }

    @Override
    public Reader getServices() {
		return getUserAppData(SERVICES);
    }

    @Override
    public Reader getHosts() {
        if (zkApplication.exists(Path.fromString(USERAPP_ZK_SUBPATH).append(HOSTS)))
        	return getUserAppData(HOSTS);
        return null;
    }

    @Override
    public List<NamedReader> getSchemas() {
        List<NamedReader> schemas = new ArrayList<>();
        var sdDir = Path.fromString(USERAPP_ZK_SUBPATH).append(SCHEMAS_DIR);
        for (String sdName : zkApplication.getChildren(sdDir)) {
            if (validSchemaFilename(sdName)) {
                schemas.add(zkApplication.getNamedReader(sdName, sdDir.append(sdName)));
            }
        }
        return schemas;
    }

    @Override
    public Optional<AllocatedHosts> getAllocatedHosts() {
        return allocatedHosts;
    }

    @Override
    public Map<Version, FileRegistry> getFileRegistries() {
        return Collections.unmodifiableMap(fileRegistryMap);
    }

    private Optional<FileRegistry> getFileRegistry(Version vespaVersion) {
        // Assumes at least one file registry, which we always have.
        Optional<FileRegistry> fileRegistry = Optional.ofNullable(fileRegistryMap.get(vespaVersion));
        if (fileRegistry.isEmpty()) {
            fileRegistry = Optional.of(fileRegistryMap.values().iterator().next());
        }
        return fileRegistry;
    }

    private Reader retrieveConfigDefReader(String def) {
        try {
            return zkApplication.getNamedReader("configdefinition", Path.fromString(DEFCONFIGS_ZK_SUBPATH).append(def));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not retrieve config definition " + def, e);
        }
    }

    @Override
    public Map<ConfigDefinitionKey, UnparsedConfigDefinition> getAllExistingConfigDefs() {
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> ret = new LinkedHashMap<>();

        List<String> allDefs = zkApplication.getChildren(Path.fromString(DEFCONFIGS_ZK_SUBPATH));

        for (String nodeName : allDefs) {
            ConfigDefinitionKey key = ConfigUtils.createConfigDefinitionKeyFromZKString(nodeName);
            ret.put(key, new UnparsedConfigDefinition() {
                @Override
                public ConfigDefinition parse() {
                    DefParser parser = new DefParser(key.getName(), retrieveConfigDefReader(nodeName));
                    return ConfigDefinitionBuilder.createConfigDefinition(parser.getTree());
                }

                @Override
                public String getUnparsedContent() {
                    try {
                        return IOUtils.readAll(retrieveConfigDefReader(nodeName));
                    } catch (Exception e) {
                        throw new RuntimeException("Error retriving def file", e);
                    }
                }
            });
        }
        return ret;
    }

    /**
     * Returns readers for all the children of a node.
     * The node is looked up relative to the location of the active application package in zookeeper.
     */
    @Override
    public List<NamedReader> getFiles(Path relativePath, String suffix, boolean recurse) {
        return zkApplication.getAllDataFromDirectory(Path.fromString(USERAPP_ZK_SUBPATH).append(relativePath), suffix, recurse);
    }

    @Override
    public ApplicationFile getFile(Path file) {
        return new ZKApplicationFile(file, zkApplication);
    }

    @Override
    public String getHostSource() {
        return "zookeeper hosts file";
    }

    @Override
    public String getServicesSource() {
        return "zookeeper services file";
    }

    @Override
    public Optional<Reader> getDeployment() { return optionalFile(DEPLOYMENT_FILE.getName()); }

    @Override
    public Optional<Reader> getValidationOverrides() { return optionalFile(VALIDATION_OVERRIDES.getName()); }

    private Optional<Reader> optionalFile(String file) {
        if (zkApplication.exists(Path.fromString(USERAPP_ZK_SUBPATH).append(file)))
            return Optional.of(getUserAppData(file));
        else
            return Optional.empty();
    }

    private static Set<String> getPaths(FileRegistry fileRegistry) {
        Set<String> paths = new LinkedHashSet<>();
        synchronized (fileRegistry) {
            for (FileRegistry.Entry e : fileRegistry.export()) {
                paths.add(e.relativePath);
            }
        }
        return paths;
    }

    @Override
    public List<ComponentInfo> getComponentsInfo(Version vespaVersion) {
        List<ComponentInfo> components = new ArrayList<>();
        FileRegistry fileRegistry = getFileRegistry(vespaVersion).get();
        for (String path : getPaths(fileRegistry)) {
            if (path.startsWith(COMPONENT_DIR + File.separator) && path.endsWith(".jar")) {
                ComponentInfo component = new ComponentInfo(path);
                components.add(component);
            }
        }
        return components;
    }

    private Reader getUserAppData(String node) {
        return zkApplication.getDataReader(Path.fromString(USERAPP_ZK_SUBPATH).append(node));
    }

    @Override
    public Reader getRankingExpression(String name) {
        return zkApplication.getDataReader(Path.fromString(USERAPP_ZK_SUBPATH).append(SCHEMAS_DIR).append(name));
    }

    @Override
    public File getFileReference(Path pathRelativeToAppDir) {
        Path path = Path.fromString(USERAPP_ZK_SUBPATH).append(pathRelativeToAppDir);

        // File does not exist: Manufacture a non-existing file
        if ( ! zkApplication.exists(path)) return new File(pathRelativeToAppDir.getRelative());

        return new File(zkApplication.getData(path));
    }

    @Override
    public void validateIncludeDir(String dirName) {
        Path path = Path.fromString(USERAPP_ZK_SUBPATH).append(dirName);
        if ( ! zkApplication.exists(path)) {
            throw new IllegalArgumentException("Cannot include directory '" + dirName +
                                               "', as it does not exist in ZooKeeper!");
        }
    }

}
