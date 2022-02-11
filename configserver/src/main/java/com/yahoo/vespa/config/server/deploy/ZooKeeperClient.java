// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.serialization.AllocatedHostsSerializer;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.server.filedistribution.FileDBRegistry;
import com.yahoo.vespa.config.server.zookeeper.ZKApplicationPackage;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static com.yahoo.config.application.api.ApplicationPackage.*;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.DEFCONFIGS_ZK_SUBPATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.META_ZK_PATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USERAPP_ZK_SUBPATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USER_DEFCONFIGS_ZK_SUBPATH;

/**
 * Reads and writes application package to and from ZooKeeper.
 *
 * @author hmusum
 */
public class ZooKeeperClient {

    private final Curator curator;
    private final DeployLogger logger;
    private final Path sessionPath; // session id

    private static final ApplicationFile.PathFilter xmlFilter = path -> path.getName().endsWith(".xml");

    public ZooKeeperClient(Curator curator, DeployLogger logger, Path sessionPath) {
        this.curator = curator;
        this.logger = logger;
        this.sessionPath = sessionPath;
    }

    /**
     * Sets up basic node structure in ZooKeeper and purges old data.
     * This is the first operation on ZK during deploy.
     */
    void initialize() {
        curator.create(sessionPath);

        for (String subPath : Arrays.asList(DEFCONFIGS_ZK_SUBPATH,
                                            USER_DEFCONFIGS_ZK_SUBPATH,
                                            USERAPP_ZK_SUBPATH,
                                            ZKApplicationPackage.fileRegistryNode)) {
            // TODO: The replaceFirst below is hackish.
            curator.create(getZooKeeperAppPath().append(subPath.replaceFirst("/", "")));
        }
    }

    /**
     * Writes def files and user config into ZK.
     *
     * @param app the application package to feed to zookeeper
     */
    void writeApplicationPackage(ApplicationPackage app) {
        try {
            writeUserDefs(app);
            writeSomeOf(app);
            writeSchemas(app);
            writeUserIncludeDirs(app, app.getUserIncludeDirs());
            writeMetadata(app.getMetaData());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write vespa model to config server(s) " + System.getProperty("configsources") + "\n" +
                                            "Please ensure that config server is started " +
                                            "and check the vespa log for configserver errors. ", e);
        }
    }

    private void writeSchemas(ApplicationPackage app) throws IOException {
        Collection<NamedReader> schemas = app.getSchemas();
        if (schemas.isEmpty()) return;

        Path zkPath = getZooKeeperAppPath(USERAPP_ZK_SUBPATH).append(SCHEMAS_DIR);
        curator.create(zkPath);
        // Ensures that ranking expressions and other files are also written
        writeDir(app.getFile(ApplicationPackage.SEARCH_DEFINITIONS_DIR), zkPath, true);
        writeDir(app.getFile(ApplicationPackage.SCHEMAS_DIR), zkPath, true);
        for (NamedReader sd : schemas) {
            curator.set(zkPath.append(sd.getName()), Utf8.toBytes(com.yahoo.io.IOUtils.readAll(sd.getReader())));
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
            if (file.isDirectory()) {
                curator.create(path.append(name));
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
            curator.set(zkPath.append(file.getPath().getName()), baos.toByteArray());
        }
    }

    private void writeUserIncludeDirs(ApplicationPackage applicationPackage, List<String> userIncludeDirs) throws IOException {
        for (String userInclude : userIncludeDirs) {
            ApplicationFile dir = applicationPackage.getFile(Path.fromString(userInclude));
            final List<ApplicationFile> files = dir.listFiles();
            if (files == null || files.isEmpty()) {
                curator.create(getZooKeeperAppPath(USERAPP_ZK_SUBPATH + "/" + userInclude));
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
            writeConfigDefinition(key.getName(), key.getNamespace(), getZooKeeperAppPath(USER_DEFCONFIGS_ZK_SUBPATH), contents);
            writeConfigDefinition(key.getName(), key.getNamespace(), getZooKeeperAppPath(DEFCONFIGS_ZK_SUBPATH), contents);
        }
        logger.log(Level.FINE, configDefs.size() + " user config definitions");
    }

    private void writeConfigDefinition(String name, String namespace, Path path, String data) {
        curator.set(path.append(namespace + "." + name), Utf8.toBytes(data));
    }

    private void write(Version vespaVersion, FileRegistry fileRegistry) {
        String exportedRegistry = FileDBRegistry.exportRegistry(fileRegistry);
        curator.set(getZooKeeperAppPath(ZKApplicationPackage.fileRegistryNode).append(vespaVersion.toFullString()),
                    Utf8.toBytes(exportedRegistry));
    }

    /**
     * Feeds application metadata to zookeeper. Used by config model to create config
     * for application metadata
     *
     * @param metaData The application metadata.
     */
    private void writeMetadata(ApplicationMetaData metaData) {
        curator.set(getZooKeeperAppPath(META_ZK_PATH), metaData.asJsonBytes());
    }

    void cleanupZooKeeper() {
        try {
            List.of(DEFCONFIGS_ZK_SUBPATH, USER_DEFCONFIGS_ZK_SUBPATH, USERAPP_ZK_SUBPATH)
                .forEach(path -> curator.delete(getZooKeeperAppPath(path)));
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
        curator.set(sessionPath.append(ZKApplicationPackage.allocatedHostsNode),
                    AllocatedHostsSerializer.toJson(hosts));
    }

    public void write(Map<Version, FileRegistry> fileRegistryMap) {
        for (Map.Entry<Version, FileRegistry> versionFileRegistryEntry : fileRegistryMap.entrySet()) {
            write(versionFileRegistryEntry.getKey(), versionFileRegistryEntry.getValue());
        }
    }

}
