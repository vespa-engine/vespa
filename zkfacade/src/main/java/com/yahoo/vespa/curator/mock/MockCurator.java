// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.mock;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.yahoo.collections.Pair;
import com.yahoo.concurrent.Lock;
import com.yahoo.concurrent.Locks;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.CompletionTimeoutException;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.recipes.CuratorLockException;
import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.ACLBackgroundPathAndBytesable;
import org.apache.curator.framework.api.ACLCreateModeBackgroundPathAndBytesable;
import org.apache.curator.framework.api.ACLPathAndBytesable;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.BackgroundPathAndBytesable;
import org.apache.curator.framework.api.BackgroundPathable;
import org.apache.curator.framework.api.BackgroundVersionable;
import org.apache.curator.framework.api.ChildrenDeletable;
import org.apache.curator.framework.api.CreateBackgroundModeACLable;
import org.apache.curator.framework.api.CreateBuilder;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.api.DeleteBuilder;
import org.apache.curator.framework.api.ExistsBuilder;
import org.apache.curator.framework.api.ExistsBuilderMain;
import org.apache.curator.framework.api.GetACLBuilder;
import org.apache.curator.framework.api.GetChildrenBuilder;
import org.apache.curator.framework.api.GetDataBuilder;
import org.apache.curator.framework.api.GetDataWatchBackgroundStatable;
import org.apache.curator.framework.api.PathAndBytesable;
import org.apache.curator.framework.api.Pathable;
import org.apache.curator.framework.api.ProtectACLCreateModePathAndBytesable;
import org.apache.curator.framework.api.SetACLBuilder;
import org.apache.curator.framework.api.SetDataBackgroundVersionable;
import org.apache.curator.framework.api.SetDataBuilder;
import org.apache.curator.framework.api.SyncBuilder;
import org.apache.curator.framework.api.UnhandledErrorListener;
import org.apache.curator.framework.api.WatchPathable;
import org.apache.curator.framework.api.Watchable;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionBridge;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.framework.api.transaction.TransactionCheckBuilder;
import org.apache.curator.framework.api.transaction.TransactionCreateBuilder;
import org.apache.curator.framework.api.transaction.TransactionDeleteBuilder;
import org.apache.curator.framework.api.transaction.TransactionSetDataBuilder;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.listen.Listenable;
import org.apache.curator.framework.recipes.atomic.AtomicStats;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.utils.*;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.yahoo.vespa.curator.mock.MemoryFileSystem.Node;

/**
 * <p>A <b>non thread safe</b> mock of the curator API.
 * The methods are implemented lazily, due to laziness.
 * You may trigger an UnsupportedOperationException, and in some cases a NullPointerException in using
 * this, which means additional functionality is needed.
 * Due to the "fluent API" style of Curator managing to break JavaDoc at a fundamental level, there is no
 * documentation on the contract of each method. The behavior here is deduced by observing what using code exists
 * and peeking at the Curator code. It may be incorrect in some corner cases.</p>
 * 
 * <p>Contains some code from PathUtils in ZooKeeper, licensed under the Apache 2.0 license.</p>
 *
 * @author bratseth
 */
public class MockCurator extends Curator {

    public boolean timeoutOnLock = false;
    public boolean throwExceptionOnLock = false;
    private boolean shouldTimeoutOnEnter = false;
    private int monotonicallyIncreasingNumber = 0;
    private final boolean stableOrdering;
    private String zooKeeperEnsembleConnectionSpec = "";
    private final Locks<String> locks = new Locks<>(Long.MAX_VALUE, TimeUnit.DAYS);

    /** The file system used by this mock to store zookeeper files and directories */
    private final MemoryFileSystem fileSystem = new MemoryFileSystem();

    /** Atomic counters. A more accurate mock would store these as files in the file system */
    private final Map<String, MockAtomicCounter> atomicCounters = new HashMap<>();

    /** Listeners to changes to a particular path */
    private final ListenerMap listeners = new ListenerMap();

    private final CuratorFramework curatorFramework;

    /** Creates a mock curator with stable ordering */
    @Inject
    public MockCurator() {
        this(true);
    }

    /**
     * Creates a mock curator
     *
     * @param stableOrdering if true children of a node are returned in the same order each time they are queries.
     *                       This is not what ZooKeeper does.
     */
    public MockCurator(boolean stableOrdering) {
        super("", "", (retryPolicy) -> null);
        this.stableOrdering = stableOrdering;
        curatorFramework = new MockCuratorFramework();
        curatorFramework.start();
    }

    /**
     * Lists the entire content of this curator instance as a multiline string.
     * Useful for debugging.
     */
    public String dumpState() { return fileSystem.dumpState(); }

    /** Returns a started curator framework */
    public CuratorFramework framework() { return curatorFramework; }

    /** Returns an atomic counter in this, or empty if no such counter is created */
    public Optional<DistributedAtomicLong> counter(String path) {
        return Optional.ofNullable(atomicCounters.get(path));
    }
    
    /**
     * Sets the ZooKeeper ensemble connection spec, which must be on the form
     * host1:port,host2:port ...
     */
    public void setZooKeeperEnsembleConnectionSpec(String ensembleSpec) {
        this.zooKeeperEnsembleConnectionSpec = ensembleSpec;
    }

    @Override
    public String zooKeeperEnsembleConnectionSpec() {
        return zooKeeperEnsembleConnectionSpec;
    }

    // ----- Start of adaptor methods from Curator to the mock file system -----

    /** Creates a node below the given directory root */
    private String createNode(String pathString, byte[] content, boolean createParents, CreateMode createMode, Node root, Listeners listeners)
            throws KeeperException.NodeExistsException, KeeperException.NoNodeException {
        validatePath(pathString);
        Path path = Path.fromString(pathString);
        if (path.isRoot()) return "/"; // the root already exists
        Node parent = root.getNode(Paths.get(path.getParentPath().toString()), createParents);
        String name = nodeName(path.getName(), createMode);

        if (parent == null)
            throw new KeeperException.NoNodeException(path.getParentPath().toString());
        if (parent.children().containsKey(path.getName()))
            throw new KeeperException.NodeExistsException(path.toString());

        parent.add(name).setContent(content);
        String nodePath = "/" + path.getParentPath().toString() + "/" + name;
        listeners.notify(Path.fromString(nodePath), content, PathChildrenCacheEvent.Type.CHILD_ADDED);
        return nodePath;
    }

    /** Deletes a node below the given directory root */
    private void deleteNode(String pathString, boolean deleteChildren, Node root, Listeners listeners)
            throws KeeperException.NoNodeException, KeeperException.NotEmptyException {
        validatePath(pathString);
        Path path = Path.fromString(pathString);
        Node parent = root.getNode(Paths.get(path.getParentPath().toString()), false);
        if (parent == null) throw new KeeperException.NoNodeException(path.toString());
        Node node = parent.children().get(path.getName());
        if (node == null) throw new KeeperException.NoNodeException(path.getName() + " under " + parent);
        if ( ! node.children().isEmpty() && ! deleteChildren)
            throw new KeeperException.NotEmptyException(path.toString());
        parent.remove(path.getName());
        listeners.notify(path, new byte[0], PathChildrenCacheEvent.Type.CHILD_REMOVED);
    }

    /** Returns the data of a node */
    private byte[] getData(String pathString, Node root) throws KeeperException.NoNodeException {
        validatePath(pathString);
        return getNode(pathString, root).getContent();
    }

    /** sets the data of an existing node */
    private void setData(String pathString, byte[] content, Node root, Listeners listeners)
            throws KeeperException.NoNodeException {
        validatePath(pathString);
        getNode(pathString, root).setContent(content);
        listeners.notify(Path.fromString(pathString), content, PathChildrenCacheEvent.Type.CHILD_UPDATED);
    }

    private List<String> getChildren(String path, Node root) throws KeeperException.NoNodeException {
        validatePath(path);
        Node node = root.getNode(Paths.get(path), false);
        if (node == null) throw new KeeperException.NoNodeException(path);
        List<String> children = new ArrayList<>(node.children().keySet());
        if (! stableOrdering)
            Collections.shuffle(children);
        return children;
    }

    private boolean exists(String path, Node root) {
        validatePath(path);
        Node parent = root.getNode(Paths.get(Path.fromString(path).getParentPath().toString()), false);
        if (parent == null) return false;
        Node node = parent.children().get(Path.fromString(path).getName());
        return node != null;
    }

    /** Returns a node or throws the appropriate exception if it doesn't exist */
    private Node getNode(String pathString, Node root) throws KeeperException.NoNodeException {
        validatePath(pathString);
        Path path = Path.fromString(pathString);
        Node parent = root.getNode(Paths.get(path.getParentPath().toString()), false);
        if (parent == null) throw new KeeperException.NoNodeException(path.toString());
        Node node = parent.children().get(path.getName());
        if (node == null) throw new KeeperException.NoNodeException(path.toString());
        return node;
    }

    private String nodeName(String baseName, CreateMode createMode) {
        switch (createMode) {
            case PERSISTENT: case EPHEMERAL: return baseName;
            case PERSISTENT_SEQUENTIAL: case EPHEMERAL_SEQUENTIAL: return baseName + monotonicallyIncreasingNumber++;
            default: throw new UnsupportedOperationException(createMode + " support not implemented in MockCurator");
        }
    }

    /** Validates a path using the same rules as ZooKeeper */
    public static String validatePath(String path) throws IllegalArgumentException {
        if (path == null) throw new IllegalArgumentException("Path cannot be null");
        if (path.length() == 0) throw new IllegalArgumentException("Path length must be > 0");
        if (path.charAt(0) != '/') throw new IllegalArgumentException("Path must start with / character");
        if (path.length() == 1) return path; // done checking - it's the root
        if (path.charAt(path.length() - 1) == '/')
            throw new IllegalArgumentException("Path must not end with / character");

        String reason = null;
        char lastc = '/';
        char chars[] = path.toCharArray();
        char c;
        for (int i = 1; i < chars.length; lastc = chars[i], i++) {
            c = chars[i];

            if (c == 0) {
                reason = "null character not allowed @" + i;
                break;
            } else if (c == '/' && lastc == '/') {
                reason = "empty node name specified @" + i;
                break;
            } else if (c == '.' && lastc == '.') {
                if (chars[i-2] == '/' && ((i + 1 == chars.length) || chars[i+1] == '/')) {
                    reason = "relative paths not allowed @" + i;
                    break;
                }
            } else if (c == '.') {
                if (chars[i-1] == '/' && ((i + 1 == chars.length) || chars[i+1] == '/')) {
                    reason = "relative paths not allowed @" + i;
                    break;
                }
            } else if (c > '\u0000' && c < '\u001f' || c > '\u007f' && c < '\u009F'
                    || c > '\ud800' && c < '\uf8ff' || c > '\ufff0' && c < '\uffff') {
                reason = "invalid charater @" + i;
                break;
            }
        }

        if (reason != null)
            throw new IllegalArgumentException("Invalid path string \"" + path + "\" caused by " + reason);
        return path;
    }

    // ----- Mock of Curator recipes accessed through our Curator interface -----

    @Override
    public DistributedAtomicLong createAtomicCounter(String path) {
        MockAtomicCounter counter = atomicCounters.get(path);
        if (counter == null) {
            counter = new MockAtomicCounter(path);
            atomicCounters.put(path, counter);
        }
        return counter;
    }

    /** Create a mutex which ensures exclusive access within this single vm */
    @Override
    public InterProcessLock createMutex(String path) {
        return new MockLock(path);
    }

    public MockCurator timeoutBarrierOnEnter(boolean shouldTimeout) {
        shouldTimeoutOnEnter = shouldTimeout;
        return this;
    }

    @Override
    public CompletionWaiter getCompletionWaiter(Path parentPath, int numMembers, String id) {
        return new MockCompletionWaiter();
    }

    @Override
    public CompletionWaiter createCompletionWaiter(Path parentPath, String waiterNode, int numMembers, String id) {
        return new MockCompletionWaiter();
    }

    @Override
    public DirectoryCache createDirectoryCache(String path, boolean cacheData, boolean dataIsCompressed, ExecutorService executorService) {
        return new MockDirectoryCache(Path.fromString(path));
    }

    @Override
    public FileCache createFileCache(String path, boolean dataIsCompressed) {
        return new MockFileCache(Path.fromString(path));
    }

    /**
     * Invocation of changes to the file system state is abstracted through this to allow transactional
     * changes to notify on commit
     */
    private abstract class Listeners {

        /** Translating method */
        public final void notify(Path path, byte[] data, PathChildrenCacheEvent.Type type) {
            String pathString = "/" + path.toString(); // this silly path class strips the leading "/" :-/
            PathChildrenCacheEvent event = new PathChildrenCacheEvent(type, new ChildData(pathString, null, data));
            notify(path, event);
        }

        public abstract void notify(Path path, PathChildrenCacheEvent event);

    }

    /** The regular listener implementation which notifies registered file and directory listeners */
    private class ListenerMap extends Listeners {

        private final Map<Path, PathChildrenCacheListener> directoryListeners = new HashMap<>();
        private final Map<Path, NodeCacheListener> fileListeners = new HashMap<>();

        public void add(Path path, PathChildrenCacheListener listener) {
            directoryListeners.put(path, listener);
        }

        public void add(Path path, NodeCacheListener listener) {
            fileListeners.put(path, listener);
        }

        @Override
        public void notify(Path path, PathChildrenCacheEvent event) {
            try {
                // Snapshot directoryListeners in case notification leads to new directoryListeners added
                Set<Map.Entry<Path, PathChildrenCacheListener>> directoryLlistenerSnapshot = new HashSet<>(directoryListeners.entrySet());
                for (Map.Entry<Path, PathChildrenCacheListener> listener : directoryLlistenerSnapshot) {
                    if (path.isChildOf(listener.getKey()))
                        listener.getValue().childEvent(curatorFramework, event);
                }

                // Snapshot directoryListeners in case notification leads to new directoryListeners added
                Set<Map.Entry<Path, NodeCacheListener>> fileListenerSnapshot = new HashSet<>(fileListeners.entrySet());
                for (Map.Entry<Path, NodeCacheListener> listener : fileListenerSnapshot) {
                    if (path.equals(listener.getKey()))
                        listener.getValue().nodeChanged();
                }
            }
            catch (Exception e) {
                e.printStackTrace(); // TODO: Remove
                throw new RuntimeException("Exception notifying listeners", e);
            }
        }

    }

    private class MockCompletionWaiter implements CompletionWaiter {

        @Override
        public void awaitCompletion(Duration timeout) {
            if (shouldTimeoutOnEnter) {
                throw new CompletionTimeoutException("");
            }
        }

        @Override
        public void notifyCompletion() {
        }

    }

    /** A lock which works inside a single vm */
    private class MockLock extends InterProcessSemaphoreMutex {

        private final String path;
        
        private Lock lock = null;
        
        public MockLock(String path) {
            super(curatorFramework, path);
            this.path = path;
        }

        @Override
        public boolean acquire(long timeout, TimeUnit unit) {
            if (throwExceptionOnLock)
                throw new CuratorLockException("Thrown by mock");
            if (timeoutOnLock) return false;
            
            try {
                lock = locks.lock(path, timeout, unit);
                return true;
            }
            catch (UncheckedTimeoutException e) {
                return false;
            }
        }

        @Override
        public void acquire() {
            if (throwExceptionOnLock)
                throw new CuratorLockException("Thrown by mock");

            lock = locks.lock(path);
        }

        @Override
        public void release() { 
            if (lock != null)
                lock.close();
        }

    }

    private class MockAtomicCounter extends DistributedAtomicLong {

        private boolean initialized = false;
        private MockLongValue value = new MockLongValue(0); // yes, uninitialized returns 0  :-/

        public MockAtomicCounter(String path) {
            super(curatorFramework, path, retryPolicy);
        }

        @Override
        public boolean initialize(Long value) {
            if (initialized) return false;
            this.value = new MockLongValue(value);
            initialized = true;
            return true;
        }

        @Override
        public AtomicValue<Long> get() {
            if (value == null) return new MockLongValue(0);
            return value;
        }

        public AtomicValue<Long> add(Long delta) throws Exception {
            return trySet(value.postValue() + delta);
        }

        public AtomicValue<Long> subtract(Long delta) throws Exception {
            return trySet(value.postValue() - delta);
        }

        @Override
        public AtomicValue<Long> increment() {
            return trySet(value.postValue() + 1);
        }

        public AtomicValue<Long> decrement() throws Exception {
            return trySet(value.postValue() - 1);
        }

        @Override
        public AtomicValue<Long> trySet(Long longval) {
            value = new MockLongValue(longval);
            return value;
        }

        public void forceSet(Long newValue) throws Exception {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public AtomicValue<Long> compareAndSet(Long expectedValue, Long newValue) throws Exception {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

    }

    private class MockLongValue implements AtomicValue<Long> {

        private AtomicLong value = new AtomicLong();

        public MockLongValue(long value) {
            this.value.set(value);
        }

        @Override
        public boolean succeeded() {
            return true;
        }

        public void setValue(long value) {
            this.value.set(value);
        }

        @Override
        public Long preValue() {
            return value.get();
        }

        @Override
        public Long postValue() {
            return value.get();
        }

        @Override
        public AtomicStats getStats() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

    }

    private class MockDirectoryCache implements DirectoryCache {

        /** The path this is caching and listening to */
        private Path path;

        public MockDirectoryCache(Path path) {
            this.path = path;
        }

        @Override
        public void start() {}

        @Override
        public void addListener(PathChildrenCacheListener listener) {
            listeners.add(path, listener);
        }

        @Override
        public List<ChildData> getCurrentData() {
            List<ChildData> childData = new ArrayList<>();
            for (String childName : getChildren(path)) {
                Path childPath = path.append(childName);
                childData.add(new ChildData(childPath.getAbsolute(), null, getData(childPath).get()));
            }
            return childData;
        }

        private void collectData(Node parent, Path parentPath, List<ChildData> data) {
            for (Node child : parent.children().values()) {
                Path childPath = parentPath.append(child.name());
                data.add(new ChildData("/" + childPath.toString(), null, child.getContent()));
            }
        }

        @Override
        public void close() {}

    }

    private class MockFileCache implements FileCache {

        /** The path this is caching and listening to */
        private Path path;

        public MockFileCache(Path path) {
            this.path = path;
        }

        @Override
        public void start() {}

        @Override
        public void addListener(NodeCacheListener listener) {
            listeners.add(path, listener);
        }

        @Override
        public ChildData getCurrentData() {
            Node node = fileSystem.root().getNode(Paths.get(path.toString()), false);
            if (node == null) return null;
            return new ChildData("/" + path.toString(), null, node.getContent());
        }

        @Override
        public void close() {}

    }

    // ----- The rest of this file is adapting the Curator (non-recipe) API to the  -----
    // ----- file system methods above.                                             -----
    // ----- There's nothing to see unless you are interested in an illustration of -----
    // ----- the folly of fluent API's or, more generally, mankind.                 -----

    private abstract class MockBackgroundACLPathAndBytesableBuilder<T> implements PathAndBytesable<T>, ProtectACLCreateModePathAndBytesable<T> {

        public BackgroundPathAndBytesable<T> withACL(List<ACL> list) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public PathAndBytesable<T> inBackground() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public PathAndBytesable<T> inBackground(Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public PathAndBytesable<T> inBackground(BackgroundCallback backgroundCallback) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public PathAndBytesable<T> inBackground(BackgroundCallback backgroundCallback, Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public PathAndBytesable<T> inBackground(BackgroundCallback backgroundCallback, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public PathAndBytesable<T> inBackground(BackgroundCallback backgroundCallback, Object o, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public ACLBackgroundPathAndBytesable<T> withMode(CreateMode createMode) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ACLCreateModeBackgroundPathAndBytesable<String> withProtection() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public T forPath(String s, byte[] bytes) throws Exception {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public T forPath(String s) throws Exception {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

    }

    private class MockCreateBuilder extends MockBackgroundACLPathAndBytesableBuilder<String> implements CreateBuilder {

        private boolean createParents = false;
        private CreateMode createMode = CreateMode.PERSISTENT;

        @Override
        public ProtectACLCreateModePathAndBytesable<String> creatingParentsIfNeeded() {
            createParents = true;
            return this;
        }

        @Override
        public ACLCreateModeBackgroundPathAndBytesable<String> withProtection() {
            // Protection against the server crashing after creating the file but before returning to the client.
            // Not relevant for an in-memory mock, obviously
            return this;
        }

        public ACLBackgroundPathAndBytesable<String> withMode(CreateMode createMode) {
            this.createMode = createMode;
            return this;
        }

        @Override
        public CreateBackgroundModeACLable compressed() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ProtectACLCreateModePathAndBytesable<String> creatingParentContainersIfNeeded() {
            // TODO: Add proper support for container nodes, see https://issues.apache.org/jira/browse/ZOOKEEPER-2163.
            return creatingParentsIfNeeded();
        }

        @Override
        @Deprecated
        public ACLPathAndBytesable<String> withProtectedEphemeralSequential() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public String forPath(String s) throws Exception {
            return createNode(s, new byte[0], createParents, createMode, fileSystem.root(), listeners);
        }

        public String forPath(String s, byte[] bytes) throws Exception {
            return createNode(s, bytes, createParents, createMode, fileSystem.root(), listeners);
        }

    }

    private class MockBackgroundPathableBuilder<T> implements BackgroundPathable<T>, Watchable<BackgroundPathable<T>> {

        @Override
        public Pathable<T> inBackground() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public Pathable<T> inBackground(Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public Pathable<T> inBackground(BackgroundCallback backgroundCallback) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public Pathable<T> inBackground(BackgroundCallback backgroundCallback, Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public Pathable<T> inBackground(BackgroundCallback backgroundCallback, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public Pathable<T> inBackground(BackgroundCallback backgroundCallback, Object o, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public BackgroundPathable<T> watched() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public BackgroundPathable<T> usingWatcher(Watcher watcher) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public BackgroundPathable<T> usingWatcher(CuratorWatcher curatorWatcher) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public T forPath(String path) throws Exception {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

    }

    private class MockGetChildrenBuilder extends MockBackgroundPathableBuilder<List<String>> implements GetChildrenBuilder {

        @Override
        public WatchPathable<List<String>> storingStatIn(Stat stat) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public List<String> forPath(String path) throws Exception {
            return getChildren(path, fileSystem.root());
        }

    }

    private class MockExistsBuilder extends MockBackgroundPathableBuilder<Stat> implements ExistsBuilder {

        @Override
        public Stat forPath(String path) throws Exception {
            if (exists(path, fileSystem.root()))
                return new Stat(); // A more accurate mock should set the stat fields
            else
                return null;
        }

        @Override
        public ExistsBuilderMain creatingParentContainersIfNeeded() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }
    }

    private class MockDeleteBuilder extends MockBackgroundPathableBuilder<Void> implements DeleteBuilder {

        private boolean deleteChildren = false;

        @Override
        public BackgroundVersionable deletingChildrenIfNeeded() {
            deleteChildren = true;
            return this;
        }

        @Override
        public ChildrenDeletable guaranteed() {
            return this;
        }

        @Override
        public BackgroundPathable<Void> withVersion(int i) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public Void forPath(String pathString) throws Exception {
            deleteNode(pathString, deleteChildren, fileSystem.root(), listeners);
            return null;
        }

    }

    private class MockGetDataBuilder extends MockBackgroundPathableBuilder<byte[]> implements GetDataBuilder {

        @Override
        public GetDataWatchBackgroundStatable decompressed() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public WatchPathable<byte[]> storingStatIn(Stat stat) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public byte[] forPath(String path) throws Exception {
            return getData(path, fileSystem.root());
        }

    }

    private class MockSetDataBuilder extends MockBackgroundACLPathAndBytesableBuilder<Stat> implements SetDataBuilder {

        @Override
        public SetDataBackgroundVersionable compressed() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public BackgroundPathAndBytesable<Stat> withVersion(int i) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public Stat forPath(String path, byte[] bytes) throws Exception {
            setData(path, bytes, fileSystem.root(), listeners);
            return null;
        }

    }

    /** Allows addition of directoryListeners which are never called */
    private class MockListenable<T> implements Listenable<T> {

        @Override
        public void addListener(T t) {
        }

        @Override
        public void addListener(T t, Executor executor) {
        }

        @Override
        public void removeListener(T t) {
        }

    }

    private class MockCuratorTransactionFinal implements CuratorTransactionFinal {

        /** The new directory root in which the transactional changes are made */
        private Node newRoot;

        private boolean committed = false;

        private final DelayedListener delayedListener = new DelayedListener();

        public MockCuratorTransactionFinal() {
            newRoot = fileSystem.root().clone();
        }

        @Override
        public Collection<CuratorTransactionResult> commit() throws Exception {
            fileSystem.replaceRoot(newRoot);
            committed = true;
            delayedListener.commit();
            return null; // TODO
        }

        @Override
        public TransactionCreateBuilder create() {
            ensureNotCommitted();
            return new MockTransactionCreateBuilder();
        }

        @Override
        public TransactionDeleteBuilder delete() {
            ensureNotCommitted();
            return new MockTransactionDeleteBuilder();
        }

        @Override
        public TransactionSetDataBuilder setData() {
            ensureNotCommitted();
            return new MockTransactionSetDataBuilder();
        }

        @Override
        public TransactionCheckBuilder check() {
            ensureNotCommitted();
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        private void ensureNotCommitted() {
            if (committed) throw new IllegalStateException("transaction already committed");
        }

        private class MockTransactionCreateBuilder implements TransactionCreateBuilder {

            private CreateMode createMode = CreateMode.PERSISTENT;

            @Override
            public PathAndBytesable<CuratorTransactionBridge> withACL(List<ACL> list) {
                throw new UnsupportedOperationException("Not implemented in MockCurator");
            }

            @Override
            public ACLPathAndBytesable<CuratorTransactionBridge> compressed() {
                throw new UnsupportedOperationException("Not implemented in MockCurator");
            }

            @Override
            public ACLPathAndBytesable<CuratorTransactionBridge> withMode(CreateMode createMode) {
                this.createMode = createMode;
                return this;
            }

            @Override
            public CuratorTransactionBridge forPath(String s, byte[] bytes) throws Exception {
                createNode(s, bytes, false, createMode, newRoot, delayedListener);
                return new MockCuratorTransactionBridge();
            }

            @Override
            public CuratorTransactionBridge forPath(String s) throws Exception {
                createNode(s, new byte[0], false, createMode, newRoot, delayedListener);
                return new MockCuratorTransactionBridge();
            }

        }

        private class MockTransactionDeleteBuilder implements TransactionDeleteBuilder {

            @Override
            public Pathable<CuratorTransactionBridge> withVersion(int i) {
                throw new UnsupportedOperationException("Not implemented in MockCurator");
            }

            @Override
            public CuratorTransactionBridge forPath(String path) throws Exception {
                deleteNode(path, false, newRoot, delayedListener);
                return new MockCuratorTransactionBridge();
            }

        }

        private class MockTransactionSetDataBuilder implements TransactionSetDataBuilder {

            @Override
            public PathAndBytesable<CuratorTransactionBridge> compressed() {
                throw new UnsupportedOperationException("Not implemented in MockCurator");
            }

            @Override
            public PathAndBytesable<CuratorTransactionBridge> withVersion(int i) {
                throw new UnsupportedOperationException("Not implemented in MockCurator");
            }

            @Override
            public CuratorTransactionBridge forPath(String s, byte[] bytes) throws Exception {
                MockCurator.this.setData(s, bytes, newRoot, delayedListener);
                return new MockCuratorTransactionBridge();
            }

            @Override
            public CuratorTransactionBridge forPath(String s) throws Exception {
                MockCurator.this.setData(s, new byte[0], newRoot, delayedListener);
                return new MockCuratorTransactionBridge();
            }

        }

        private class MockCuratorTransactionBridge implements CuratorTransactionBridge {

            @Override
            public CuratorTransactionFinal and() {
                return MockCuratorTransactionFinal.this;
            }

        }

        /** A class which collects listen events and forwards them to the regular directoryListeners on commit */
        private class DelayedListener extends Listeners {

            private final List<Pair<Path, PathChildrenCacheEvent>> events = new ArrayList<>();

            @Override
            public void notify(Path path, PathChildrenCacheEvent event) {
                events.add(new Pair<>(path, event));
            }

            public void commit() {
                for (Pair<Path, PathChildrenCacheEvent> event : events)
                    listeners.notify(event.getFirst(), event.getSecond());
            }

        }

    }

    private class MockCuratorFramework implements CuratorFramework {

        private CuratorFrameworkState curatorState = CuratorFrameworkState.LATENT;

        @Override
        public void start() {
            curatorState = CuratorFrameworkState.STARTED;
        }

        @Override
        public void close() {
            curatorState = CuratorFrameworkState.STOPPED;
        }

        @Override
        public CuratorFrameworkState getState() {
            return curatorState;
        }

        @Override
        @Deprecated
        public boolean isStarted() {
            return curatorState == CuratorFrameworkState.STARTED;
        }

        @Override
        public CreateBuilder create() {
            return new MockCreateBuilder();
        }

        @Override
        public DeleteBuilder delete() {
            return new MockDeleteBuilder();
        }

        @Override
        public ExistsBuilder checkExists() {
            return new MockExistsBuilder();
        }

        @Override
        public GetDataBuilder getData() {
            return new MockGetDataBuilder();
        }

        @Override
        public SetDataBuilder setData() {
            return new MockSetDataBuilder();
        }

        @Override
        public GetChildrenBuilder getChildren() {
            return new MockGetChildrenBuilder();
        }

        @Override
        public GetACLBuilder getACL() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public SetACLBuilder setACL() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public CuratorTransaction inTransaction() {
            return new MockCuratorTransactionFinal();
        }

        @Override
        @Deprecated
        public void sync(String path, Object backgroundContextObject) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public void createContainers(String s) throws Exception {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public Listenable<ConnectionStateListener> getConnectionStateListenable() {
            return new MockListenable<>();
        }

        @Override
        public Listenable<CuratorListener> getCuratorListenable() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public Listenable<UnhandledErrorListener> getUnhandledErrorListenable() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        @Deprecated
        public CuratorFramework nonNamespaceView() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public CuratorFramework usingNamespace(String newNamespace) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public String getNamespace() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public CuratorZookeeperClient getZookeeperClient() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Deprecated
        @Override
        public EnsurePath newNamespaceAwareEnsurePath(String path) {
            return new EnsurePath(path);
        }

        @Override
        public void clearWatcherReferences(Watcher watcher) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public boolean blockUntilConnected(int i, TimeUnit timeUnit) throws InterruptedException {
            return true;
        }

        @Override
        public void blockUntilConnected() throws InterruptedException {

        }

        @Override
        public SyncBuilder sync() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }
        
    }

}
