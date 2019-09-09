// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.google.common.base.Joiner;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.model.application.provider.PreGeneratedFileRegistry;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.serialization.AllocatedHostsSerializer;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionBuilder;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an application residing in zookeeper.
 *
 * @author Tony Vaagenes
 */
public class ZKApplicationPackage implements ApplicationPackage {

    private ZKApplication zkApplication;

    private final Map<Version, PreGeneratedFileRegistry> fileRegistryMap = new HashMap<>();
    private final Optional<AllocatedHosts> allocatedHosts;
    private static final Version legacyVersion = new Version(0, 0, 0);

    public static final String fileRegistryNode = "fileregistry";
    public static final String allocatedHostsNode = "allocatedHosts";
    private final ApplicationMetaData metaData;

    public ZKApplicationPackage(ConfigCurator zk, Path sessionPath, Optional<NodeFlavors> nodeFlavors) {
        verifyAppPath(zk, sessionPath);
        zkApplication = new ZKApplication(zk, sessionPath);
        metaData = readMetaDataFromLiveApp(zkApplication);
        importFileRegistries();
        allocatedHosts = importAllocatedHosts(nodeFlavors);
    }

    private Optional<AllocatedHosts> importAllocatedHosts(Optional<NodeFlavors> nodeFlavors) {
        if ( ! zkApplication.exists(ZKApplicationPackage.allocatedHostsNode)) return Optional.empty();
        return Optional.of(readAllocatedHosts(nodeFlavors));
    }

    /**
     * Reads allocated hosts at the given node.
     *
     * @return the allocated hosts at this node or empty if there is no data at this path
     */
    private AllocatedHosts readAllocatedHosts(Optional<NodeFlavors> nodeFlavors) {
        try {
            return AllocatedHostsSerializer.fromJson(zkApplication.getBytes(ZKApplicationPackage.allocatedHostsNode), nodeFlavors);
        } catch (Exception e) {
            throw new RuntimeException("Unable to read allocated hosts", e);
        }
    }

    private void importFileRegistries() {
        List<String> fileRegistryNodes = zkApplication.getChildren(ZKApplicationPackage.fileRegistryNode);
        if (fileRegistryNodes.isEmpty()) {
            fileRegistryMap.put(legacyVersion, importFileRegistry(ZKApplicationPackage.fileRegistryNode));
        } else {
            fileRegistryNodes.forEach(version ->
                        fileRegistryMap.put(Version.fromString(version),
                                            importFileRegistry(Joiner.on("/").join(ZKApplicationPackage.fileRegistryNode, version))));
        }
    }

    private PreGeneratedFileRegistry importFileRegistry(String fileRegistryNode) {
        try {
            return PreGeneratedFileRegistry.importRegistry(zkApplication.getDataReader(fileRegistryNode));
        } catch (Exception e) {
            throw new RuntimeException("Could not determine which files to distribute. " +
                                       "Please try redeploying the application", e);
        }
    }

    private ApplicationMetaData readMetaDataFromLiveApp(ZKApplication liveApp) {
        String metaDataString = liveApp.getData(ConfigCurator.META_ZK_PATH);
        if (metaDataString == null || metaDataString.isEmpty()) {
            return null;
        }
        return ApplicationMetaData.fromJsonString(liveApp.getData(ConfigCurator.META_ZK_PATH));
    }

    @Override
    public ApplicationMetaData getMetaData() {
        return metaData;
    }

    private static void verifyAppPath(ConfigCurator zk, Path appPath) {
        if (!zk.exists(appPath.getAbsolute()))
            throw new RuntimeException("App with path " + appPath + " does not exist");
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getApplicationName() {
        return metaData.getApplicationId().application().value();
    }

    @Override
    public ApplicationId getApplicationId() { return metaData.getApplicationId(); }

    @Override
    public Reader getServices() {
		return getUserAppData(SERVICES);
    }

    @Override
    public Reader getHosts() {
        if (zkApplication.exists(ConfigCurator.USERAPP_ZK_SUBPATH, HOSTS))
        	return getUserAppData(HOSTS);
        return null;
    }

    @Override
    public List<NamedReader> searchDefinitionContents() {
        List<NamedReader> ret = new ArrayList<>();
        for (String sd : zkApplication.getChildren(ConfigCurator.USERAPP_ZK_SUBPATH+"/"+SEARCH_DEFINITIONS_DIR)) {
            if (sd.endsWith(ApplicationPackage.SD_NAME_SUFFIX)) {
                ret.add(new NamedReader(sd, new StringReader(zkApplication.getData(ConfigCurator.USERAPP_ZK_SUBPATH+"/"+SEARCH_DEFINITIONS_DIR, sd))));
            }
        }
        return ret;
    }

    @Override
    public Optional<AllocatedHosts> getAllocatedHosts() {
        return allocatedHosts;
    }

    @Override
    public Map<Version, FileRegistry> getFileRegistries() {
        return Collections.unmodifiableMap(fileRegistryMap);
    }

    private Optional<PreGeneratedFileRegistry> getPreGeneratedFileRegistry(Version vespaVersion) {
        // Assumes at least one file registry, which we always have.
        Optional<PreGeneratedFileRegistry> fileRegistry = Optional.ofNullable(fileRegistryMap.get(vespaVersion));
        if (fileRegistry.isEmpty()) {
            fileRegistry = Optional.of(fileRegistryMap.values().iterator().next());
        }
        return fileRegistry;
    }

    @Override
    public List<NamedReader> getSearchDefinitions() {
        return searchDefinitionContents();
    }

    private Reader retrieveConfigDefReader(String def) {
        try {
            return zkApplication.getDataReader(ConfigCurator.DEFCONFIGS_ZK_SUBPATH, def);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not retrieve config definition " + def + ".", e);
        }
    }

    @Override
    public Map<ConfigDefinitionKey, UnparsedConfigDefinition> getAllExistingConfigDefs() {
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> ret = new LinkedHashMap<>();

        List<String> allDefs = zkApplication.getChildren(ConfigCurator.DEFCONFIGS_ZK_SUBPATH);

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
        return zkApplication.getAllDataFromDirectory(ConfigCurator.USERAPP_ZK_SUBPATH + '/' + relativePath.getRelative(), suffix, recurse);
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
        if (zkApplication.exists(ConfigCurator.USERAPP_ZK_SUBPATH, file))
            return Optional.of(getUserAppData(file));
        else
            return Optional.empty();
    }

    @Override
    public List<ComponentInfo> getComponentsInfo(Version vespaVersion) {
        List<ComponentInfo> components = new ArrayList<>();
        PreGeneratedFileRegistry fileRegistry = getPreGeneratedFileRegistry(vespaVersion).get();
        for (String path : fileRegistry.getPaths()) {
            if (path.startsWith(ApplicationPackage.COMPONENT_DIR + File.separator) && path.endsWith(".jar")) {
                ComponentInfo component = new ComponentInfo(path);
                components.add(component);
            }
        }
        return components;
    }

    private Reader getUserAppData(String node) {
        return zkApplication.getDataReader(ConfigCurator.USERAPP_ZK_SUBPATH, node);
    }

    @Override
    public Reader getRankingExpression(String name) {
        return zkApplication.getDataReader(ConfigCurator.USERAPP_ZK_SUBPATH+"/"+SEARCH_DEFINITIONS_DIR, name);
    }

    @Override
    public File getFileReference(Path pathRelativeToAppDir) {
        String fileName = zkApplication.getData(ConfigCurator.USERAPP_ZK_SUBPATH + "/" + pathRelativeToAppDir.getRelative());
        // File does not exist: Manufacture a non-existing file
        return new File(Objects.requireNonNullElseGet(fileName, pathRelativeToAppDir::getRelative));
    }

    @Override
    public void validateIncludeDir(String dirName) {
        String fullPath = ConfigCurator.USERAPP_ZK_SUBPATH + "/" + dirName;
        if ( ! zkApplication.exists(fullPath)) {
            throw new IllegalArgumentException("Cannot include directory '" + dirName +
                                               "', as it does not exist in ZooKeeper!");
        }
    }

}
