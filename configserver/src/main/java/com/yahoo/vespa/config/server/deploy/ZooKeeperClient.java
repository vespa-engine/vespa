// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.provision.ProvisionInfo;
import com.yahoo.config.provision.Version;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.server.zookeeper.ZKApplicationPackage;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.application.provider.PreGeneratedFileRegistry;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.*;

/**
 * A class used for reading and writing application data to zookeeper.
 *
 * @author hmusum
 * @since 5.1
 */
public class ZooKeeperClient {

    private final ConfigCurator configCurator;
    private final DeployLogger logger;
    private final boolean trace;
    /* This is the generation that will be used for reading and writing application data. (1 more than last deployed application) */
    private final Path rootPath;

    static final ApplicationFile.PathFilter xmlFilter = new ApplicationFile.PathFilter() {
        @Override
        public boolean accept(Path path) {
            return path.getName().endsWith(".xml");
        }
    };

    public ZooKeeperClient(ConfigCurator configCurator, DeployLogger logger, boolean trace, Path rootPath) {
        this.configCurator = configCurator;
        this.logger = logger;
        this.trace = trace;
        this.rootPath = rootPath;
    }

    /**
     * Sets up basic node structure in ZooKeeper and purges old data.
     * This is the first operation on ZK during deploy-application.
     *
     * We have retries in this method because there have been cases of stray connection loss to ZK,
     * even though the user has started the config server.
     *
     */
    void setupZooKeeper() {
        int retries = 5;
        try {
            while (retries > 0) {
                try {
                    trace("Setting up ZooKeeper nodes for this application");
                    createZooKeeperNodes();
                    break;
                } catch (RuntimeException e) {
                    logger.log(LogLevel.FINE, "ZK init failed, retrying: " + e);
                    retries--;
                    if (retries == 0) {
                        throw e;
                    }
                    Thread.sleep(100);
                    // Not reconnecting, ZK is supposed to handle that automatically
                    // as long as the session doesn't expire. We'll see.
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize vespa model writing to config server(s) " +
                                            System.getProperty("configsources") + "\n" +
                                            "Please ensure that cloudconfig_server is started on the config server node(s), " +
                                            "and check the vespa log for configserver errors. ", e);
        }
    }

    /** Sets the app id and attempts to set up zookeeper. The app id must be ordered for purge to work OK. */
    private void createZooKeeperNodes() {
        if (!configCurator.exists(rootPath.getAbsolute())) {
            configCurator.createNode(rootPath.getAbsolute());
        }

        for (String subPath : Arrays.asList(
                ConfigCurator.DEFCONFIGS_ZK_SUBPATH,
                ConfigCurator.USER_DEFCONFIGS_ZK_SUBPATH,
                ConfigCurator.USERAPP_ZK_SUBPATH,
                ZKApplicationPackage.fileRegistryNode)) {
            // TODO The replaceFirst below is hackish.
            configCurator.createNode(getZooKeeperAppPath(null).getAbsolute(), subPath.replaceFirst("/", ""));
        }
    }

    /**
     * Feeds def files and user config into ZK.
     *
     * @param app the application package to feed to zookeeper
     */
    void feedZooKeeper(ApplicationPackage app) {
        trace("Feeding application config into ZooKeeper");
        // gives lots and lots of debug output: // BasicConfigurator.configure();
        try {
            trace("zk operations: " + configCurator.getNumberOfOperations());
            trace("zk operations: " + configCurator.getNumberOfOperations());
            trace("Feeding user def files into ZooKeeper");
            feedZKUserDefs(app);
            trace("zk operations: " + configCurator.getNumberOfOperations());
            trace("Feeding application package into ZooKeeper");
            // TODO 1200 zk operations done in the below method
            feedZKAppPkg(app);
            feedSearchDefinitions(app);
            feedZKUserIncludeDirs(app, app.getUserIncludeDirs());
            trace("zk operations: " + configCurator.getNumberOfOperations());
            trace("zk read operations: " + configCurator.getNumberOfReadOperations());
            trace("zk write operations: " + configCurator.getNumberOfWriteOperations());
            trace("Feeding sd from docproc bundle into ZooKeeper");
            trace("zk operations: " + configCurator.getNumberOfOperations());
            trace("Write application metadata into ZooKeeper");
            feedZKAppMetaData(app.getMetaData());
            trace("zk operations: " + configCurator.getNumberOfOperations());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write vespa model to config server(s) " + System.getProperty("configsources") + "\n" +
                    "Please ensure that cloudconfig_server is started on the config server node(s), " +
                    "and check the vespa log for configserver errors. ", e);
        }
    }

    private void feedSearchDefinitions(ApplicationPackage app) throws IOException {
        Collection<NamedReader> sds = app.getSearchDefinitions();
        if (sds.isEmpty()) {
            return;
        }
        Path zkPath = getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH).append(ApplicationPackage.SEARCH_DEFINITIONS_DIR);
        configCurator.createNode(zkPath.getAbsolute());
        // Ensures that ranking expressions and other files are also fed.
        feedDirZooKeeper(app.getFile(ApplicationPackage.SEARCH_DEFINITIONS_DIR), zkPath, false);
        for (NamedReader sd : sds) {
            String name = sd.getName();
            Reader reader = sd.getReader();
            String data = com.yahoo.io.IOUtils.readAll(reader);
            reader.close();
            configCurator.putData(zkPath.getAbsolute(), name, data);
        }
    }

    /**
     * Puts the application package files into ZK
     *
     * @param app The application package to use as input.
     * @throws java.io.IOException  if not able to write to Zookeeper
     */
    void feedZKAppPkg(ApplicationPackage app) throws IOException {
        ApplicationFile.PathFilter srFilter = new ApplicationFile.PathFilter() {
            @Override
            public boolean accept(Path path) {
                return path.getName().endsWith(ApplicationPackage.RULES_NAME_SUFFIX);
            }
        };
        // Copy app package files and subdirs into zk
        // TODO: We should have a way of doing this which doesn't require repeating all the content
        feedFileZooKeeper(app.getFile(Path.fromString(ApplicationPackage.SERVICES)),
                          getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH));
        feedFileZooKeeper(app.getFile(Path.fromString(ApplicationPackage.HOSTS)),
                          getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH));
        feedFileZooKeeper(app.getFile(Path.fromString(ApplicationPackage.DEPLOYMENT_FILE.getName())),
                          getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH));
        feedFileZooKeeper(app.getFile(Path.fromString(ApplicationPackage.VALIDATION_OVERRIDES.getName())),
                          getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH));

        feedDirZooKeeper(app.getFile(Path.fromString(ApplicationPackage.TEMPLATES_DIR)),
                         getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH),
                         true);
        feedDirZooKeeper(app.getFile(ApplicationPackage.RULES_DIR),
                         getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH).append(ApplicationPackage.RULES_DIR),
                         srFilter, true);
        feedDirZooKeeper(app.getFile(ApplicationPackage.QUERY_PROFILES_DIR),
                         getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH).append(ApplicationPackage.QUERY_PROFILES_DIR),
                         xmlFilter, true);
        feedDirZooKeeper(app.getFile(ApplicationPackage.PAGE_TEMPLATES_DIR),
                         getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH).append(ApplicationPackage.PAGE_TEMPLATES_DIR),
                         xmlFilter, true);
        feedDirZooKeeper(app.getFile(Path.fromString(ApplicationPackage.SEARCHCHAINS_DIR)),
                         getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH).append(ApplicationPackage.SEARCHCHAINS_DIR),
                         xmlFilter, true);
        feedDirZooKeeper(app.getFile(Path.fromString(ApplicationPackage.DOCPROCCHAINS_DIR)),
                         getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH).append(ApplicationPackage.DOCPROCCHAINS_DIR),
                         xmlFilter, true);
        feedDirZooKeeper(app.getFile(Path.fromString(ApplicationPackage.ROUTINGTABLES_DIR)),
                         getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH).append(ApplicationPackage.ROUTINGTABLES_DIR),
                         xmlFilter, true);
    }

    private void feedDirZooKeeper(ApplicationFile file, Path zooKeeperAppPath, boolean recurse) throws IOException {
        feedDirZooKeeper(file, zooKeeperAppPath, new ApplicationFile.PathFilter() {
            @Override
            public boolean accept(Path path) {
                return true;
            }
        }, recurse);
    }

    private void feedDirZooKeeper(ApplicationFile dir, Path path, ApplicationFile.PathFilter filenameFilter, boolean recurse) throws IOException {
        if (!dir.isDirectory()) {
            logger.log(LogLevel.FINE, dir.getPath().getAbsolute()+" is not a directory. Not feeding the files into ZooKeeper.");
            return;
        }
        for (ApplicationFile file: listFiles(dir, filenameFilter)) {
            String name = file.getPath().getName();
            if (name.startsWith(".")) continue; //.svn , .git ...
            if ("CVS".equals(name)) continue;
            if (file.isDirectory()) {
                configCurator.createNode(path.append(name).getAbsolute());
                if (recurse) {
                    feedDirZooKeeper(file, path.append(name), filenameFilter, recurse);
                }
            } else {
                feedFileZooKeeper(file, path);
            }
        }
    }

    /**
     * Like {@link ApplicationFile#listFiles(com.yahoo.config.application.api.ApplicationFile.PathFilter)} with a slightly different semantic. Never filter out directories.
     */
    private List<ApplicationFile> listFiles(ApplicationFile dir, ApplicationFile.PathFilter filter) {
        List<ApplicationFile> rawList = dir.listFiles();
        List<ApplicationFile> ret = new ArrayList<>();
        if (rawList != null) {
            for (ApplicationFile f : rawList) {
                if (f.isDirectory()) {
                    ret.add(f);
                } else {
                    if (filter.accept(f.getPath())) {
                        ret.add(f);
                    }
                }
            }
        }
        return ret;
    }

    private void feedFileZooKeeper(ApplicationFile file, Path zkPath) throws IOException {
        if (!file.exists()) {
            return;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream inputStream = file.createInputStream()) {
            IOUtils.copy(inputStream, baos);
            baos.flush();
            configCurator.putData(zkPath.append(file.getPath().getName()).getAbsolute(), baos.toByteArray());
        }
    }

    private void feedZKUserIncludeDirs(ApplicationPackage applicationPackage, List<String> userIncludeDirs) throws IOException {
        // User defined include directories
        for (String userInclude : userIncludeDirs) {
            ApplicationFile dir = applicationPackage.getFile(Path.fromString(userInclude));
            final List<ApplicationFile> files = dir.listFiles();
            if (files == null || files.isEmpty()) {
                configCurator.createNode(getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH + "/" + userInclude).getAbsolute());
            }
            feedDirZooKeeper(dir,
                    getZooKeeperAppPath(ConfigCurator.USERAPP_ZK_SUBPATH + "/" + userInclude),
                    xmlFilter, true);
        }
    }

    /**
     * Feeds all user-defined .def file from the application package into ZooKeeper (both into
     * /defconfigs and /userdefconfigs
     */
    private void feedZKUserDefs(ApplicationPackage applicationPackage) {
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> configDefs = applicationPackage.getAllExistingConfigDefs();
        for (Map.Entry<ConfigDefinitionKey, UnparsedConfigDefinition> entry : configDefs.entrySet()) {
            ConfigDefinitionKey key = entry.getKey();
            String contents = entry.getValue().getUnparsedContent();
            feedDefToZookeeper(key.getName(), key.getNamespace(), getZooKeeperAppPath(ConfigCurator.USER_DEFCONFIGS_ZK_SUBPATH).getAbsolute(), contents);
            feedDefToZookeeper(key.getName(), key.getNamespace(), getZooKeeperAppPath(ConfigCurator.DEFCONFIGS_ZK_SUBPATH).getAbsolute(), contents);
        }
        logger.log(LogLevel.FINE, configDefs.size() + " user config definitions");
    }

    private void feedDefToZookeeper(String name, String namespace, String path, String data) {
        feedDefToZookeeper(name, namespace, "", path, com.yahoo.text.Utf8.toBytes(data));
    }

    private void feedDefToZookeeper(String name, String namespace, String version, String path, byte[] data) {
        configCurator.putDefData(
                ("".equals(namespace)) ? name : (namespace + "." + name),
                version,
                path,
                data);
    }

    private void feedZKFileRegistry(Version vespaVersion, FileRegistry fileRegistry) {
        trace("Feeding file registry data into ZooKeeper");
        String exportedRegistry = PreGeneratedFileRegistry.exportRegistry(fileRegistry);

        configCurator.putData(getZooKeeperAppPath(null).append(ZKApplicationPackage.fileRegistryNode).getAbsolute(),
                vespaVersion.toSerializedForm(),
                exportedRegistry);
    }

    /**
     * Feeds application metadata to zookeeper. Used by vespamodel to create config
     * for application metadata (used by ApplicationStatusHandler)
     *
     * @param metaData The application metadata.
     */
    private void feedZKAppMetaData(ApplicationMetaData metaData) {
        configCurator.putData(getZooKeeperAppPath(ConfigCurator.META_ZK_PATH).getAbsolute(), metaData.asJsonString());
    }

    void cleanupZooKeeper() {
        trace("Exception occurred. Cleaning up ZooKeeper");
        try {
            for (String subPath : Arrays.asList(
                    ConfigCurator.DEFCONFIGS_ZK_SUBPATH,
                    ConfigCurator.USER_DEFCONFIGS_ZK_SUBPATH,
                    ConfigCurator.USERAPP_ZK_SUBPATH)) {
                configCurator.deleteRecurse(getZooKeeperAppPath(null).append(subPath).getAbsolute());
            }
        } catch (Exception e) {
            logger.log(LogLevel.WARNING, "Could not clean up in zookeeper");
            //Might be called in an exception handler before re-throw, so do not throw here.
        }
    }

    /**
     * Gets a full ZK app path based on id set in Admin object
     *
     *
     * @param trailingPath trailing part of path to be appended to ZK app path
     * @return a String with the full ZK application path including trailing path, if set
     */
    Path getZooKeeperAppPath(String trailingPath) {
        if (trailingPath != null) {
            return rootPath.append(trailingPath);
        } else {
            return rootPath;
        }
    }

    void trace(String msg) {
        if (trace) {
            logger.log(LogLevel.FINE, msg);
        }
    }

    private void feedProvisionInfo(Version version, ProvisionInfo info) throws IOException {
        byte[] json = info.toJson();
        configCurator.putData(rootPath.append(ZKApplicationPackage.allocatedHostsNode).append(version.toSerializedForm()).getAbsolute(), json);
    }

    public void feedZKFileRegistries(Map<Version, FileRegistry> fileRegistryMap) {
        for (Map.Entry<Version, FileRegistry> versionFileRegistryEntry : fileRegistryMap.entrySet()) {
            feedZKFileRegistry(versionFileRegistryEntry.getKey(), versionFileRegistryEntry.getValue());
        }
    }

    public void feedProvisionInfos(Map<Version, ProvisionInfo> provisionInfoMap) throws IOException {
        for (Map.Entry<Version, ProvisionInfo> versionProvisionInfoEntry : provisionInfoMap.entrySet()) {
            feedProvisionInfo(versionProvisionInfoEntry.getKey(), versionProvisionInfoEntry.getValue());
        }
    }
}
