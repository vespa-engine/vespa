// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.json.Jackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USERAPP_ZK_SUBPATH;

/**
 * @author Ulf Lilleengen
 * @author Vegard Havdal
 */
class ZKApplicationFile extends ApplicationFile {

    private static final Logger log = Logger.getLogger("ZKApplicationFile");
    private final ZKApplication zkApp;
    private final ObjectMapper mapper = Jackson.mapper();

    public ZKApplicationFile(Path path, ZKApplication app) {
        super(path);
        this.zkApp = app;
    }

    @Override
    public boolean isDirectory() {
        Path zkPath = getZKPath(path);
        if (zkApp.exists(zkPath)) {
            String data = zkApp.getData(zkPath);
            return data == null || data.isEmpty() || ! zkApp.getChildren(zkPath).isEmpty();
        }
        return false;
    }

    @Override
    public boolean exists() {
        try {
            Path zkPath = getZKPath(path);
            return zkApp.exists(zkPath);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public ApplicationFile delete() {
        if (!listFiles().isEmpty()) {
            throw new RuntimeException("Can't delete, directory not empty: " + this);
        }
        zkApp.deleteRecurse(getZKPath(path));
        writeMetaFile(null, ContentStatusDeleted);
        return this;
    }

    @Override
    public Reader createReader() throws FileNotFoundException {
        Path zkPath = getZKPath(path);
        if ( ! zkApp.exists(zkPath)) throw new FileNotFoundException("No such path: " + path);

        return new StringReader(zkApp.getData(zkPath));
    }

    @Override
    public InputStream createInputStream() throws FileNotFoundException {
        Path zkPath = getZKPath(path);
        if ( ! zkApp.exists(zkPath)) throw new FileNotFoundException("No such path: " + path);

        return new ByteArrayInputStream(zkApp.getBytes(zkPath));
    }

    @Override
    public ApplicationFile createDirectory() {
        Path zkPath = getZKPath(path);
        if (isDirectory()) return this;
        if (exists()) {
            throw new IllegalArgumentException("Unable to create directory, file exists: " + path);
        }
        zkApp.create(zkPath);
        writeMetaFile(null, ContentStatusNew);
        return this;
    }

    @Override
    public ApplicationFile writeFile(Reader input) {
        Path zkPath = getZKPath(path);
        try {
            String data = IOUtils.readAll(input);
            String status = ContentStatusNew;
            if (zkApp.exists(zkPath)) {
                status = ContentStatusChanged;
            }
            zkApp.putData(zkPath, data);
            writeMetaFile(data, status);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public ApplicationFile appendFile(String value) {
        Path zkPath = getZKPath(path);
        String status = ContentStatusNew;
        if (zkApp.exists(zkPath)) {
            status = ContentStatusChanged;
        }
        String existingData = zkApp.getData(zkPath);
        if (existingData == null)
            existingData = "";
        zkApp.putData(zkPath, existingData + value);
        writeMetaFile(value, status);
        return this;
    }

    @Override
    public List<ApplicationFile> listFiles(PathFilter filter) {
        Path userPath = getZKPath(path);
        List<ApplicationFile> ret = new ArrayList<>();
        for (String zkChild : zkApp.getChildren(userPath)) {
            Path childPath = path.append(zkChild);
            // Ignore dot-files.
            if (!childPath.getName().startsWith(".") && filter.accept(childPath)) {
                ret.add(new ZKApplicationFile(childPath, zkApp));
            }
        }
        return ret;
    }

    private static Path getZKPath(Path path) {
        if (path.isRoot()) {
            return Path.fromString(USERAPP_ZK_SUBPATH);
        }
        return Path.fromString(USERAPP_ZK_SUBPATH).append(path);
    }

    private void writeMetaFile(String input, String status) {
        Path metaPath = getZKPath(getMetaPath());
        StringWriter writer = new StringWriter();
        try {
            mapper.writeValue(writer, new MetaData(status, input == null ? "" : ConfigUtils.getMd5(input)));
            log.log(Level.FINE, () -> "Writing meta file to " + metaPath);
            zkApp.putData(metaPath, writer.toString());
        } catch (IOException e) {
            throw new RuntimeException("Error writing meta file to " + metaPath, e);
        }
    }

    public MetaData getMetaData() {
        Path metaPath = getZKPath(getMetaPath());
        log.log(Level.FINE, () -> "Getting metadata for " + metaPath);
        if (!zkApp.exists(getZKPath(path))) {
            if (zkApp.exists(metaPath)) {
                return getMetaDataFromZk(metaPath);
            } else {
                return null;
            }
        }
        if (zkApp.exists(metaPath)) {
            return getMetaDataFromZk(metaPath);
        }
        return new MetaData(ContentStatusNew, isDirectory() ? "" : ConfigUtils.getMd5(zkApp.getData(getZKPath(path))));
    }

    private MetaData getMetaDataFromZk(Path metaPath) {
        try {
            return mapper.readValue(zkApp.getBytes(metaPath), MetaData.class);
        } catch (IOException e) {
            return null;
        }
    }

    @Override public long getSize() { return zkApp.getSize(getZKPath(path)); }

    @Override
    public int compareTo(ApplicationFile other) {
        if (other == this) return 0;
        return this.getPath().getName().compareTo((other).getPath().getName());
    }

}
