// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.model.application.provider.PreGeneratedFileRegistry;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.serialization.AllocatedHostsSerializer;
import com.yahoo.io.reader.NamedReader;
import java.util.logging.Level;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.config.server.zookeeper.ZKApplicationPackage;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.yahoo.config.application.api.ApplicationPackage.*;
import static com.yahoo.vespa.config.server.zookeeper.ConfigCurator.DEFCONFIGS_ZK_SUBPATH;
import static com.yahoo.vespa.config.server.zookeeper.ConfigCurator.META_ZK_PATH;
import static com.yahoo.vespa.config.server.zookeeper.ConfigCurator.USERAPP_ZK_SUBPATH;
import static com.yahoo.vespa.config.server.zookeeper.ConfigCurator.USER_DEFCONFIGS_ZK_SUBPATH;

/**
 * A class used for reading and writing application data to zookeeper.
 *
 * @author hmusum
 */
public class ZooKeeperClient {

    private final ConfigCurator configCurator;
    private final DeployLogger logger;
    private final Path sessionPath; // session id

    private static final ApplicationFile.PathFilter xmlFilter = path -> path.getName().endsWith(".xml");

    public ZooKeeperClient(ConfigCurator configCurator, DeployLogger logger, Path sessionPath) {
        this.configCurator = configCurator;
        this.logger = logger;
        this.sessionPath = sessionPath;
    }

    /**
     * Sets up basic node structure in ZooKeeper and purges old data.
     * This is the first operation on ZK during deploy.
     */
    void initialize() {
        if ( ! configCurator.exists(sessionPath.getAbsolute()))
            configCurator.createNode(sessionPath.getAbsolute());

        for (String subPath : Arrays.asList(DEFCONFIGS_ZK_SUBPATH,
                                            USER_DEFCONFIGS_ZK_SUBPATH,
                                            USERAPP_ZK_SUBPATH,
                                            ZKApplicationPackage.fileRegistryNode)) {
            // TODO: The replaceFirst below is hackish.
            configCurator.createNode(getZooKeeperAppPath().getAbsolute(),
                                     subPath.replaceFirst("/", ""));
        }
    }

    /**
     * Writes def files and user config into ZK.
     *
     * @param app the application package to feed to zookeeper
     */
    void write(ApplicationPackage app) {
        try {
            writeUserDefs(app);
            writeSomeOf(app);
            writeSearchDefinitions(app);
            writeUserIncludeDirs(app, app.getUserIncludeDirs());
            write(app.getMetaData());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write vespa model to config server(s) " + System.getProperty("configsources") + "\n" +
                                            "Please ensure that config server is started " +
                                            "and check the vespa log for configserver errors. ", e);
        }
    }

    private void writeSearchDefinitions(ApplicationPackage app) throws IOException {
        Collection<NamedReader> sds = app.getSearchDefinitions();
        if (sds.isEmpty()) return;

        // TODO: Change to SCHEMAS_DIR after March 2020
        // TODO: When it does also check RankExpressionFile.sendTo
        Path zkPath = getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(ApplicationPackage.SEARCH_DEFINITIONS_DIR);
        configCurator.createNode(zkPath.getAbsolute());
        // Ensures that ranking expressions and other files are also written
        writeDir(app.getFile(ApplicationPackage.SEARCH_DEFINITIONS_DIR), zkPath, false);
        writeDir(app.getFile(ApplicationPackage.SCHEMAS_DIR), zkPath, false);
        for (NamedReader sd : sds) {
            configCurator.putData(zkPath.getAbsolute(), sd.getName(), com.yahoo.io.IOUtils.readAll(sd.getReader()));
            sd.getReader().close();
        }
    }

    /**
     * Puts some of the application package files into ZK - see write(app).
     *
     * @param app the application package to use as input.
     * @throws java.io.IOException if not able to write to Zookeeper
     */
    private void writeSomeOf(ApplicationPackage app) throws IOException {
        // TODO: We should have a way of doing this which doesn't require repeating all the content
        writeFile(app.getFile(Path.fromString(SERVICES)), getZooKeeperAppPath(USERAPP_ZK_SUBPATH));
        writeFile(app.getFile(Path.fromString(HOSTS)), getZooKeeperAppPath(USERAPP_ZK_SUBPATH));
        writeFile(app.getFile(Path.fromString(DEPLOYMENT_FILE.getName())), getZooKeeperAppPath(USERAPP_ZK_SUBPATH));
        writeFile(app.getFile(Path.fromString(VALIDATION_OVERRIDES.getName())), getZooKeeperAppPath(USERAPP_ZK_SUBPATH));
        writeDir(app.getFile(RULES_DIR),
                 getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(RULES_DIR),
                 (path) -> path.getName().endsWith(ApplicationPackage.RULES_NAME_SUFFIX),
                 true);
        writeDir(app.getFile(QUERY_PROFILES_DIR),
                 getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(QUERY_PROFILES_DIR),
                 xmlFilter, true);
        writeDir(app.getFile(PAGE_TEMPLATES_DIR),
                 getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(PAGE_TEMPLATES_DIR),
                 xmlFilter, true);
        writeDir(app.getFile(Path.fromString(SEARCHCHAINS_DIR)),
                 getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(SEARCHCHAINS_DIR),
                 xmlFilter, true);
        writeDir(app.getFile(Path.fromString(DOCPROCCHAINS_DIR)),
                 getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(DOCPROCCHAINS_DIR),
                 xmlFilter, true);
        writeDir(app.getFile(Path.fromString(ROUTINGTABLES_DIR)),
                 getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(ROUTINGTABLES_DIR),
                 xmlFilter, true);
        writeDir(app.getFile(MODELS_GENERATED_REPLICATED_DIR),
                 getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(MODELS_GENERATED_REPLICATED_DIR),
                 true);
        writeDir(app.getFile(SECURITY_DIR),
                 getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(SECURITY_DIR),
                 true);
    }

    private void writeDir(ApplicationFile file, Path zooKeeperAppPath, boolean recurse) throws IOException {
        writeDir(file, zooKeeperAppPath, (__) -> true, recurse);
    }

    private void writeDir(ApplicationFile dir, Path path, ApplicationFile.PathFilter filenameFilter, boolean recurse) throws IOException {
        if ( ! dir.isDirectory()) return;
        for (ApplicationFile file : listFiles(dir, filenameFilter)) {
            String name = file.getPath().getName();
            if (name.startsWith(".")) continue; //.svn , .git ...
            if ("CVS".equals(name)) continue;
            if (file.isDirectory()) {
                configCurator.createNode(path.append(name).getAbsolute());
                if (recurse) {
                    writeDir(file, path.append(name), filenameFilter, recurse);
                }
            } else {
                writeFile(file, path);
            }
        }
    }

    /**
     * Like {@link ApplicationFile#listFiles(com.yahoo.config.application.api.ApplicationFile.PathFilter)}
     * with slightly different semantics: Never filter out directories.
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

    private void writeFile(ApplicationFile file, Path zkPath) throws IOException {
        if ( ! file.exists()) return;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream inputStream = file.createInputStream()) {
            inputStream.transferTo(baos);
            baos.flush();
            configCurator.putData(zkPath.append(file.getPath().getName()).getAbsolute(), baos.toByteArray());
        }
    }

    private void writeUserIncludeDirs(ApplicationPackage applicationPackage, List<String> userIncludeDirs) throws IOException {
        // User defined include directories
        for (String userInclude : userIncludeDirs) {
            ApplicationFile dir = applicationPackage.getFile(Path.fromString(userInclude));
            final List<ApplicationFile> files = dir.listFiles();
            if (files == null || files.isEmpty()) {
                configCurator.createNode(getZooKeeperAppPath(USERAPP_ZK_SUBPATH + "/" + userInclude).getAbsolute());
            }
            writeDir(dir,
                     getZooKeeperAppPath(USERAPP_ZK_SUBPATH + "/" + userInclude),
                     xmlFilter, true);
        }
    }

    /**
     * Feeds all user-defined .def file from the application package into ZooKeeper (both into
     * /defconfigs and /userdefconfigs
     */
    private void writeUserDefs(ApplicationPackage applicationPackage) {
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> configDefs = applicationPackage.getAllExistingConfigDefs();
        for (Map.Entry<ConfigDefinitionKey, UnparsedConfigDefinition> entry : configDefs.entrySet()) {
            ConfigDefinitionKey key = entry.getKey();
            String contents = entry.getValue().getUnparsedContent();
            writeConfigDefinition(key.getName(), key.getNamespace(), getZooKeeperAppPath(USER_DEFCONFIGS_ZK_SUBPATH).getAbsolute(), contents);
            writeConfigDefinition(key.getName(), key.getNamespace(), getZooKeeperAppPath(DEFCONFIGS_ZK_SUBPATH).getAbsolute(), contents);
        }
        logger.log(Level.FINE, configDefs.size() + " user config definitions");
    }

    private void writeConfigDefinition(String name, String namespace, String path, String data) {
        configCurator.putDefData(namespace + "." + name, path, com.yahoo.text.Utf8.toBytes(data));
    }

    private void write(Version vespaVersion, FileRegistry fileRegistry) {
        String exportedRegistry = PreGeneratedFileRegistry.exportRegistry(fileRegistry);
        configCurator.putData(getZooKeeperAppPath(ZKApplicationPackage.fileRegistryNode).getAbsolute(),
                              vespaVersion.toFullString(),
                              exportedRegistry);
    }

    /**
     * Feeds application metadata to zookeeper. Used by vespamodel to create config
     * for application metadata (used by ApplicationStatusHandler)
     *
     * @param metaData The application metadata.
     */
    private void write(ApplicationMetaData metaData) {
        configCurator.putData(getZooKeeperAppPath(META_ZK_PATH).getAbsolute(), metaData.asJsonBytes());
    }

    void cleanupZooKeeper() {
        try {
            List.of(DEFCONFIGS_ZK_SUBPATH, USER_DEFCONFIGS_ZK_SUBPATH, USERAPP_ZK_SUBPATH)
                .forEach(path -> configCurator.deleteRecurse(getZooKeeperAppPath(path).getAbsolute()));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not clean up in zookeeper: " + Exceptions.toMessageString(e));
            //Might be called in an exception handler before re-throw, so do not throw here.
        }
    }

    /**
     * Gets a full ZK application path
     *
     * @return a String with the full ZK application path
     */
    private Path getZooKeeperAppPath() {
        return getZooKeeperAppPath(null);
    }

    /**
     * Gets a full ZK application path
     *
     * @param trailingPath trailing part of path to be appended to ZK app path
     * @return a String with the full ZK application path including trailing path, if set
     */
    private Path getZooKeeperAppPath(String trailingPath) {
        if (trailingPath == null) return sessionPath;

        return sessionPath.append(trailingPath);
    }

    public void write(AllocatedHosts hosts) throws IOException {
        configCurator.putData(sessionPath.append(ZKApplicationPackage.allocatedHostsNode).getAbsolute(),
                              AllocatedHostsSerializer.toJson(hosts));
    }

    public void write(Map<Version, FileRegistry> fileRegistryMap) {
        for (Map.Entry<Version, FileRegistry> versionFileRegistryEntry : fileRegistryMap.entrySet()) {
            write(versionFileRegistryEntry.getKey(), versionFileRegistryEntry.getValue());
        }
    }

}
