// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.path.Path;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author lulf
 * @author vegardh
 * @since 5.1
 */
class ZKApplicationFile extends ApplicationFile {

    private static final Logger log = Logger.getLogger("ZKApplicationFile");
    private final ZKLiveApp zkApp;
    private final ObjectMapper mapper = new ObjectMapper();

    public ZKApplicationFile(Path path, ZKLiveApp app) {
        super(path);
        this.zkApp = app;
    }

    @Override
    public boolean isDirectory() {
        String zkPath = getZKPath(path);
        if (zkApp.exists(zkPath)) {
            String data = zkApp.getData(zkPath);
            if (data == null || data.isEmpty() || !zkApp.getChildren(zkPath).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean exists() {
        try {
            String zkPath = getZKPath(path);
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
        String zkPath = getZKPath(path);
        String data = zkApp.getData(zkPath);
        if (data == null) {
            throw new FileNotFoundException("No such path: " + path);
        }
        return new StringReader(data);
    }

    @Override
    public InputStream createInputStream() throws FileNotFoundException {
        String zkPath = getZKPath(path);
        byte[] data = zkApp.getBytes(zkPath);
        if (data == null) {
            throw new FileNotFoundException("No such path: " + path);
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public ApplicationFile createDirectory() {
        String zkPath = getZKPath(path);
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
        String zkPath = getZKPath(path);
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
        String zkPath = getZKPath(path);
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
        String userPath = getZKPath(path);
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

    private static String getZKPath(Path path) {
        if (path.isRoot()) {
            return ConfigCurator.USERAPP_ZK_SUBPATH;
        }
        return ConfigCurator.USERAPP_ZK_SUBPATH + "/" + path.getRelative();
    }

    private void writeMetaFile(String input, String status) {
        String metaPath = getZKPath(getMetaPath());
        StringWriter writer = new StringWriter();
        try {
            mapper.writeValue(writer, new MetaData(status, input == null ? "" : ConfigUtils.getMd5(input)));
            log.log(LogLevel.DEBUG, "Writing meta file to " + metaPath);
            zkApp.putData(metaPath, writer.toString());
        } catch (IOException e) {
            throw new RuntimeException("Error writing meta file to " + metaPath, e);
        }
    }

    public MetaData getMetaData() {
        String metaPath = getZKPath(getMetaPath());
        log.log(LogLevel.DEBUG, "Getting metadata for " + metaPath);
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

    private MetaData getMetaDataFromZk(String metaPath) {
        try {
            return mapper.readValue(zkApp.getBytes(metaPath), MetaData.class);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public int compareTo(ApplicationFile other) {
        if (other == this) return 0;
        return this.getPath().getName().compareTo((other).getPath().getName());
    }

}
