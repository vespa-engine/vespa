// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * @author tonytv
 */
public class FileDistributionManager {

    private final static boolean available;

    @SuppressWarnings({"UnusedDeclaration"}) // Needs to be here due to JNI
    private long nativeFileDistributionManager;

    private final File applicationDirectory;
    private final String appId;
    private final Lock lock;


    private native static void setup();

    private native void init(byte[] fileDbDirectory, byte[] zkServers);

    private native String addFileImpl(byte[] absolutePath);

    private native void setDeployedFilesImpl(byte[] host, byte[] appId, byte[][] fileReferences);

    private native void limitSendingOfDeployedFilesToImpl(byte[][] hostNames, byte[] appId);
    private native void limitFilesToImpl(byte[][] fileReferences);
    private native void removeDeploymentsThatHaveDifferentApplicationIdImpl(byte[][] asByteArrays, byte[] bytes);

    private native byte[] getProgressImpl(byte[] fileReference,
                                          byte[][] hostNames);

    private byte[][] getAsByteArrays(Collection<String> strings) {
        byte[][] byteArrays = new byte[strings.size()][];
        int i = 0;
        for (String string : strings) {
            byteArrays[i++] = string.getBytes();
        }
        return byteArrays;
    }

    @Override
    protected void finalize() throws Throwable {
        shutdown();
        super.finalize();
    }

    static {
        available = loadLibrary("filedistributionmanager");
        if (available)
            setup();
    }

    private static boolean loadLibrary(String name) {
        try {
            System.loadLibrary(name);
            return true;
        }
        catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /** Returns whether this functionality is available in this runtime */
    public static boolean isAvailable() { return available; }

    private byte[] absolutePath(File file) {
        return file.getAbsolutePath().getBytes();
    }

    private void ensureDirExists(File dir) {
        if (!dir.exists()) {
            throw new PathDoesNotExistException(dir.getPath());
        }
    }

    public FileDistributionManager(File fileDbDirectory, File applicationDirectory, String zkServers, String appId, Lock lock) {
        ensureDirExists(applicationDirectory);
        ensureDirExists(fileDbDirectory);

        this.applicationDirectory = applicationDirectory;
        this.appId = appId;
        this.lock = lock;

        init(fileDbDirectory.getPath().getBytes(), zkServers.getBytes());
    }

    public String addFile(String relativePath) {
        File path = new File(applicationDirectory, relativePath);
        if (!path.exists())
            throw new PathDoesNotExistException(path.getPath());

        try (LockGuard guard = new LockGuard(lock)) {
            return addFileImpl(absolutePath(path));
        }
    }

    public void setDeployedFiles(String hostName, Collection<String> fileReferences) {
        try (LockGuard guard = new LockGuard(lock)) {
            setDeployedFilesImpl(hostName.getBytes(), appId.getBytes(), getAsByteArrays(fileReferences));
        }
    }

    public void reloadDeployFileDistributor() {
        try (LockGuard guard = new LockGuard(lock)) {
            Runtime.getRuntime().exec("pkill -SIGUSR1 -x filedistributor");
        } catch (IOException e) {
            throw new RuntimeException("Failed to reinitialize the filedistributor", e);
        }
    }

    public void limitSendingOfDeployedFilesTo(Collection<String> hostNames) {
        try (LockGuard guard = new LockGuard(lock)) {
            limitSendingOfDeployedFilesToImpl(getAsByteArrays(hostNames), appId.getBytes());
        }
    }

    public byte[] getProgress(String fileReference,
                              List<String> hostNamesSortedAscending) {
        return getProgressImpl(fileReference.getBytes(), getAsByteArrays(hostNamesSortedAscending));
    }


    public native void shutdown();

    public void removeDeploymentsThatHaveDifferentApplicationId(Collection<String> targetHostnames) {
        try (LockGuard guard = new LockGuard(lock)) {
            removeDeploymentsThatHaveDifferentApplicationIdImpl(getAsByteArrays(targetHostnames), appId.getBytes());
        }
    }

    private static class LockGuard implements AutoCloseable {
        private final Lock lock;
        public LockGuard(Lock lock) {
            this.lock = lock;
            lock.lock();
        }

        @Override
        public void close() {
            lock.unlock();
        }
    }
}
