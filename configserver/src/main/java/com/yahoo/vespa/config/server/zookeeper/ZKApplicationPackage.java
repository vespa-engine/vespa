// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.*;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.ProvisionInfo;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.io.reader.NamedReader;
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
import java.util.Optional;

/**
 * Represents an application residing in zookeeper.
 *
 * @author Tony Vaagenes
 */
public class ZKApplicationPackage implements ApplicationPackage {

    private ZKLiveApp liveApp;

    private final Map<com.yahoo.config.provision.Version, PreGeneratedFileRegistry> fileRegistryMap = new HashMap<>();
    private final Optional<ProvisionInfo> provisionInfo;
    private static final com.yahoo.config.provision.Version legacyVersion = com.yahoo.config.provision.Version.fromIntValues(0, 0, 0);

    public static final String fileRegistryNode = "fileregistry";
    public static final String allocatedHostsNode = "allocatedHosts";
    private final ApplicationMetaData metaData;

    public ZKApplicationPackage(ConfigCurator zk, Path appPath, Optional<NodeFlavors> nodeFlavors) {
        verifyAppPath(zk, appPath);
        liveApp = new ZKLiveApp(zk, appPath);
        metaData = readMetaDataFromLiveApp(liveApp);
        importFileRegistries(fileRegistryNode);
        provisionInfo = importProvisionInfos(allocatedHostsNode, nodeFlavors);
    }

    private Optional<ProvisionInfo> importProvisionInfos(String allocatedHostsPath, Optional<NodeFlavors> nodeFlavors) {
        if ( ! liveApp.exists(allocatedHostsPath)) return Optional.empty();
        Optional<ProvisionInfo> provisionInfo = readProvisionInfo(allocatedHostsPath, nodeFlavors);
        if ( ! provisionInfo.isPresent()) { // Read from legacy location. TODO: Remove when 6.142 is in production everywhere
            List<String> provisionInfoByVersionNodes = liveApp.getChildren(allocatedHostsPath);
            provisionInfo = merge(readProvisionInfosByVersion(provisionInfoByVersionNodes, nodeFlavors));
        }
        return provisionInfo;
    }
    
    private Map<Version, ProvisionInfo> readProvisionInfosByVersion(List<String> provisionInfoByVersionNodes, Optional<NodeFlavors> nodeFlavors) {
        Map<Version, ProvisionInfo> provisionInfoMap = new HashMap<>();
        provisionInfoByVersionNodes.stream()
                .forEach(versionStr -> {
                    Version version = Version.fromString(versionStr);
                    Optional<ProvisionInfo> provisionInfo = readProvisionInfo(Joiner.on("/").join(allocatedHostsNode, versionStr),
                                                                              nodeFlavors);
                    provisionInfo.ifPresent(info -> provisionInfoMap.put(version, info));
                });
        return provisionInfoMap;
    }

    private Optional<ProvisionInfo> merge(Map<Version, ProvisionInfo> provisionInfoMap) {
        // Merge the provision infos in any order. This is wrong but preserves current behavior (modulo order differences)
        if (provisionInfoMap.isEmpty()) return Optional.empty();
        
        Map<String, HostSpec> merged = new HashMap<>();
        for (Map.Entry<Version, ProvisionInfo> entry : provisionInfoMap.entrySet()) {
            for (HostSpec host : entry.getValue().getHosts())
                merged.put(host.hostname(), host);
        }
        return Optional.of(ProvisionInfo.withHosts(ImmutableSet.copyOf(merged.values())));
    }

    /** 
     * Reads provision info at the given node.
     * 
     * @return the provision info at this node or empty if there is no data at this path
     */
    private Optional<ProvisionInfo> readProvisionInfo(String provisionInfoPath, Optional<NodeFlavors> nodeFlavors) {
        try {
            byte[] data = liveApp.getBytes(provisionInfoPath);
            if (data.length == 0) return Optional.empty(); // TODO: Remove this line (and make return non-optional) when 6.142 is in production everywhere
            return Optional.of(ProvisionInfo.fromJson(data, nodeFlavors));
        } catch (Exception e) {
            throw new RuntimeException("Unable to read provision info", e);
        }
    }

    private void importFileRegistries(String fileRegistryNode) {
        List<String> fileRegistryNodes = liveApp.getChildren(fileRegistryNode);
        if (fileRegistryNodes.isEmpty()) {
            fileRegistryMap.put(legacyVersion, importFileRegistry(fileRegistryNode));
        } else {
            fileRegistryNodes.stream()
                    .forEach(version -> {
                        fileRegistryMap.put(com.yahoo.config.provision.Version.fromString(version),
                                            importFileRegistry(Joiner.on("/").join(fileRegistryNode, version)));
                    });
        }
    }

    private PreGeneratedFileRegistry importFileRegistry(String fileRegistryNode) {
        try {
            return PreGeneratedFileRegistry.importRegistry(liveApp.getDataReader(fileRegistryNode));
        } catch (Exception e) {
            throw new RuntimeException("Could not determine which files to distribute. " +
                                       "Please try redeploying the application", e);
        }
    }

    private ApplicationMetaData readMetaDataFromLiveApp(ZKLiveApp liveApp) {
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
    public String getApplicationName() {
        return metaData.getApplicationName();
    }

    @Override
    public Reader getServices() {
		return getUserAppData(SERVICES);
    }

    @Override
    public Reader getHosts() {
        if (liveApp.exists(ConfigCurator.USERAPP_ZK_SUBPATH,HOSTS))
        	return getUserAppData(HOSTS);
        return null;
    }

    @Override
    public List<NamedReader> searchDefinitionContents() {
        List<NamedReader> ret = new ArrayList<>();
        for (String sd : liveApp.getChildren(ConfigCurator.USERAPP_ZK_SUBPATH+"/"+SEARCH_DEFINITIONS_DIR)) {
            if (sd.endsWith(ApplicationPackage.SD_NAME_SUFFIX)) {
                ret.add(new NamedReader(sd, new StringReader(liveApp.getData(ConfigCurator.USERAPP_ZK_SUBPATH+"/"+SEARCH_DEFINITIONS_DIR, sd))));
            }
        }
        return ret;
    }

    public Optional<ProvisionInfo> getProvisionInfo() {
        return provisionInfo;
    }

    @Override
    public Map<com.yahoo.config.provision.Version, FileRegistry> getFileRegistryMap() {
        return Collections.unmodifiableMap(fileRegistryMap);
    }

    private Optional<PreGeneratedFileRegistry> getPreGeneratedFileRegistry(com.yahoo.config.provision.Version vespaVersion) {
        // Assumes at least one file registry, which we always have.
        Optional<PreGeneratedFileRegistry> fileRegistry = Optional.ofNullable(fileRegistryMap.get(vespaVersion));
        if (!fileRegistry.isPresent()) {
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
            return liveApp.getDataReader(ConfigCurator.DEFCONFIGS_ZK_SUBPATH, def);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not retrieve config definition " + def + ".", e);
        }
    }

    @Override
    public Map<ConfigDefinitionKey, UnparsedConfigDefinition> getAllExistingConfigDefs() {
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> ret = new LinkedHashMap<>();

        List<String> allDefs = liveApp.getChildren(ConfigCurator.DEFCONFIGS_ZK_SUBPATH);

        for (final String nodeName : allDefs) {
            final ConfigDefinitionKey key = ConfigUtils.createConfigDefinitionKeyFromZKString(nodeName);
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

    //Returns readers for all the children of a node.
    //The node is looked up relative to the location of the active application package
    //in zookeeper.
    @Override
    public List<NamedReader> getFiles(Path relativePath,String suffix,boolean recurse) {
        return liveApp.getAllDataFromDirectory(ConfigCurator.USERAPP_ZK_SUBPATH + '/' + relativePath.getRelative(), suffix, recurse);
    }

    @Override
    public ApplicationFile getFile(Path file) { // foo/bar/baz.json
        return new ZKApplicationFile(file, liveApp);
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
        if (liveApp.exists(ConfigCurator.USERAPP_ZK_SUBPATH, file))
            return Optional.of(getUserAppData(file));
        else
            return Optional.empty();
    }

    @Override
    public List<ComponentInfo> getComponentsInfo(com.yahoo.config.provision.Version vespaVersion) {
        List<ComponentInfo> components = new ArrayList<>();
        PreGeneratedFileRegistry fileRegistry = getPreGeneratedFileRegistry(vespaVersion).get();
        for (String path : fileRegistry.getPaths()) {
            if (path.startsWith(FilesApplicationPackage.COMPONENT_DIR + File.separator) && path.endsWith(".jar")) {
                ComponentInfo component = new ComponentInfo(path);
                components.add(component);
            }
        }
        return components;
    }

    private Reader getUserAppData(String node) {
        return liveApp.getDataReader(ConfigCurator.USERAPP_ZK_SUBPATH, node);
    }

    @Override
    public Reader getRankingExpression(String name) {
        return liveApp.getDataReader(ConfigCurator.USERAPP_ZK_SUBPATH+"/"+SEARCH_DEFINITIONS_DIR, name);
    }

    @Override
    public File getFileReference(Path pathRelativeToAppDir) {
        String fileName = liveApp.getData(ConfigCurator.USERAPP_ZK_SUBPATH + "/" + pathRelativeToAppDir.getRelative());
        return new File(fileName);
    }

    @Override
    public void validateIncludeDir(String dirName) {
        String fullPath = ConfigCurator.USERAPP_ZK_SUBPATH + "/" + dirName;
        if (!liveApp.exists(fullPath)) {
            throw new IllegalArgumentException("Cannot include directory '" + dirName +
                                               "', as it does not exist in ZooKeeper!");
        }
    }

}
