// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.mock;

import com.yahoo.collections.Pair;
import com.yahoo.concurrent.Lock;
import com.yahoo.concurrent.Locks;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.CompletionTimeoutException;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MemoryFileSystem.Node;
import com.yahoo.vespa.curator.recipes.CuratorLockException;
import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.WatcherRemoveCuratorFramework;
import org.apache.curator.framework.api.ACLBackgroundPathAndBytesable;
import org.apache.curator.framework.api.ACLCreateModeBackgroundPathAndBytesable;
import org.apache.curator.framework.api.ACLCreateModePathAndBytesable;
import org.apache.curator.framework.api.ACLCreateModeStatBackgroundPathAndBytesable;
import org.apache.curator.framework.api.ACLPathAndBytesable;
import org.apache.curator.framework.api.ACLableExistBuilderMain;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.BackgroundPathAndBytesable;
import org.apache.curator.framework.api.BackgroundPathable;
import org.apache.curator.framework.api.BackgroundVersionable;
import org.apache.curator.framework.api.ChildrenDeletable;
import org.apache.curator.framework.api.CreateBackgroundModeStatACLable;
import org.apache.curator.framework.api.CreateBuilder;
import org.apache.curator.framework.api.CreateBuilder2;
import org.apache.curator.framework.api.CreateBuilderMain;
import org.apache.curator.framework.api.CreateProtectACLCreateModePathAndBytesable;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.api.DeleteBuilder;
import org.apache.curator.framework.api.DeleteBuilderMain;
import org.apache.curator.framework.api.ErrorListenerPathAndBytesable;
import org.apache.curator.framework.api.ErrorListenerPathable;
import org.apache.curator.framework.api.ExistsBuilder;
import org.apache.curator.framework.api.GetACLBuilder;
import org.apache.curator.framework.api.GetChildrenBuilder;
import org.apache.curator.framework.api.GetConfigBuilder;
import org.apache.curator.framework.api.GetDataBuilder;
import org.apache.curator.framework.api.GetDataWatchBackgroundStatable;
import org.apache.curator.framework.api.PathAndBytesable;
import org.apache.curator.framework.api.Pathable;
import org.apache.curator.framework.api.ProtectACLCreateModeStatPathAndBytesable;
import org.apache.curator.framework.api.ReconfigBuilder;
import org.apache.curator.framework.api.RemoveWatchesBuilder;
import org.apache.curator.framework.api.SetACLBuilder;
import org.apache.curator.framework.api.SetDataBackgroundVersionable;
import org.apache.curator.framework.api.SetDataBuilder;
import org.apache.curator.framework.api.SyncBuilder;
import org.apache.curator.framework.api.UnhandledErrorListener;
import org.apache.curator.framework.api.VersionPathAndBytesable;
import org.apache.curator.framework.api.WatchPathable;
import org.apache.curator.framework.api.Watchable;
import org.apache.curator.framework.api.WatchesBuilder;
import org.apache.curator.framework.api.transaction.CuratorMultiTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionBridge;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.framework.api.transaction.TransactionCheckBuilder;
import org.apache.curator.framework.api.transaction.TransactionCreateBuilder;
import org.apache.curator.framework.api.transaction.TransactionCreateBuilder2;
import org.apache.curator.framework.api.transaction.TransactionDeleteBuilder;
import org.apache.curator.framework.api.transaction.TransactionOp;
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
import org.apache.curator.framework.schema.SchemaSet;
import org.apache.curator.framework.state.ConnectionStateErrorPolicy;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryForever;
import org.apache.curator.utils.EnsurePath;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.quorum.flexible.QuorumVerifier;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A mock implementation of{@link CuratorFramework} for testing purposes.
 *
 * @author mpolden
 */
public class MockCuratorFramework implements CuratorFramework  {

    private final boolean shouldTimeoutOnEnter;
    private final boolean stableOrdering;
    private final Locks<String> locks = new Locks<>(Long.MAX_VALUE, TimeUnit.DAYS);

    /** The file system used by this mock to store zookeeper files and directories */
    private final MemoryFileSystem fileSystem = new MemoryFileSystem();

    /** Atomic counters. A more accurate mock would store these as files in the file system */
    private final Map<String, MockAtomicCounter> atomicCounters = new ConcurrentHashMap<>();

    /** Listeners to changes to a particular path */
    private final ListenerMap listeners = new ListenerMap();

    public final MockListenable<ConnectionStateListener> connectionStateListeners = new MockListenable<>();

    private CuratorFrameworkState curatorState = CuratorFrameworkState.LATENT;
    private int monotonicallyIncreasingNumber = 0;

    public MockCuratorFramework(boolean stableOrdering, boolean shouldTimeoutOnEnter) {
        this.stableOrdering = stableOrdering;
        this.shouldTimeoutOnEnter = shouldTimeoutOnEnter;
    }

    public Map<String, MockAtomicCounter> atomicCounters() {
        return Collections.unmodifiableMap(atomicCounters);
    }

    public MemoryFileSystem fileSystem() {
        return fileSystem;
    }

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
    public ReconfigBuilder reconfig() { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public GetConfigBuilder getConfig() { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public CuratorTransaction inTransaction() {
        return new MockCuratorTransactionFinal();
    }

    @Override
    public CuratorMultiTransaction transaction() { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public TransactionOp transactionOp() { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public RemoveWatchesBuilder watches() { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public WatchesBuilder watchers() { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public WatcherRemoveCuratorFramework newWatcherRemoveCuratorFramework() {
        class MockWatcherRemoveCuratorFramework extends MockCuratorFramework implements WatcherRemoveCuratorFramework {

            public MockWatcherRemoveCuratorFramework(boolean stableOrdering, boolean shouldTimeoutOnEnter) {
                super(stableOrdering, shouldTimeoutOnEnter);
            }

            @Override
            public void removeWatchers() {}
        }
        return new MockWatcherRemoveCuratorFramework(true, false);
    }

    @Override
    public ConnectionStateErrorPolicy getConnectionStateErrorPolicy() { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public QuorumVerifier getCurrentConfig() { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public SchemaSet getSchemaSet() { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public CompletableFuture<Void> postSafeNotify(Object monitorHolder) { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    public CompletableFuture<Void> runSafe(Runnable runnable) { throw new UnsupportedOperationException("Not implemented in MockCurator"); }

    @Override
    @Deprecated
    public void sync(String path, Object backgroundContextObject) {
        throw new UnsupportedOperationException("Not implemented in MockCurator");
    }

    @Override
    public void createContainers(String s) {
        throw new UnsupportedOperationException("Not implemented in MockCurator");
    }

    @Override
    public Listenable<ConnectionStateListener> getConnectionStateListenable() {
        return connectionStateListeners;
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
    public boolean blockUntilConnected(int i, TimeUnit timeUnit) {
        return true;
    }

    @Override
    public void blockUntilConnected() {
    }

    @Override
    public SyncBuilder sync() {
        throw new UnsupportedOperationException("Not implemented in MockCurator");
    }

    // ----- Factory methods for mocks */

    public InterProcessLock createMutex(String path) {
        return new MockLock(path);
    }

    public MockAtomicCounter createAtomicCounter(String path) {
        return atomicCounters.computeIfAbsent(path, (k) -> new MockAtomicCounter(path));
    }

    public Curator.CompletionWaiter createCompletionWaiter() {
        return new MockCompletionWaiter();
    }

    public Curator.DirectoryCache createDirectoryCache(String path) {
        return new MockDirectoryCache(Path.fromString(path));
    }

    public Curator.FileCache createFileCache(String path) {
        return new MockFileCache(Path.fromString(path));
    }

    // ----- Start of adaptor methods from Curator to the mock file system -----

    /** Creates a node below the given directory root */
    private String createNode(String pathString, byte[] content, boolean createParents, Stat stat, CreateMode createMode, MemoryFileSystem.Node root, Listeners listeners, Long ttl)
            throws KeeperException.NodeExistsException, KeeperException.NoNodeException {
        validatePath(pathString);
        Path path = Path.fromString(pathString);
        if (path.isRoot()) return "/"; // the root already exists
        MemoryFileSystem.Node parent = root.getNode(Paths.get(path.getParentPath().toString()), createParents);
        String name = nodeName(path.getName(), createMode);

        if (parent == null)
            throw new KeeperException.NoNodeException(path.getParentPath().toString());
        if (parent.children().containsKey(path.getName()))
            throw new KeeperException.NodeExistsException(path.toString());

        MemoryFileSystem.Node node = parent.add(name);
        node.setContent(content);
        if (List.of(CreateMode.PERSISTENT_WITH_TTL, CreateMode.PERSISTENT_SEQUENTIAL_WITH_TTL).contains(createMode)
                && ttl != null)
            node.setTtl(ttl);
        String nodePath = "/" + path.getParentPath().toString() + "/" + name;
        listeners.notify(Path.fromString(nodePath), content, PathChildrenCacheEvent.Type.CHILD_ADDED);
        if (stat != null) stat.setVersion(node.version());
        return nodePath;
    }

    /** Deletes a node below the given directory root */
    private void deleteNode(String pathString, boolean deleteChildren, int version, MemoryFileSystem.Node root, Listeners listeners)
            throws KeeperException {
        validatePath(pathString);
        Path path = Path.fromString(pathString);
        MemoryFileSystem.Node parent = root.getNode(Paths.get(path.getParentPath().toString()), false);
        if (parent == null) throw new KeeperException.NoNodeException(path.toString());
        MemoryFileSystem.Node node = parent.children().get(path.getName());
        if (node == null) throw new KeeperException.NoNodeException(path.getName() + " under " + parent);
        if (version != -1 && version != node.version())
            throw new KeeperException.BadVersionException("expected version " + version + ", but was " + node.version());
        if ( ! node.children().isEmpty() && ! deleteChildren)
            throw new KeeperException.NotEmptyException(path.toString());
        parent.remove(path.getName());
        listeners.notify(path, new byte[0], PathChildrenCacheEvent.Type.CHILD_REMOVED);
    }

    /** Returns the data of a node */
    private byte[] getData(String pathString, Stat stat, MemoryFileSystem.Node root) throws KeeperException.NoNodeException {
        validatePath(pathString);
        return getNode(pathString, stat, root).getContent();
    }

    /** sets the data of an existing node */
    private void setData(String pathString, byte[] content, int version, Stat stat, MemoryFileSystem.Node root, Listeners listeners)
            throws KeeperException {
        validatePath(pathString);
        Node node = getNode(pathString, null, root);
        if (version != -1 && version != node.version())
            throw new KeeperException.BadVersionException("expected version " + version + ", but was " + node.version());
        node.setContent(content);
        if (stat != null) stat.setVersion(node.version());
        listeners.notify(Path.fromString(pathString), content, PathChildrenCacheEvent.Type.CHILD_UPDATED);
    }

    private List<String> getChildren(String path, MemoryFileSystem.Node root) throws KeeperException.NoNodeException {
        validatePath(path);
        MemoryFileSystem.Node node = root.getNode(Paths.get(path), false);
        if (node == null) throw new KeeperException.NoNodeException(path);
        List<String> children = new ArrayList<>(node.children().keySet());
        if (! stableOrdering)
            Collections.shuffle(children);
        return children;
    }

    /** Returns a node or throws the appropriate exception if it doesn't exist */
    private MemoryFileSystem.Node getNode(String pathString, Stat stat, MemoryFileSystem.Node root) throws KeeperException.NoNodeException {
        validatePath(pathString);
        Path path = Path.fromString(pathString);
        MemoryFileSystem.Node parent = root.getNode(Paths.get(path.getParentPath().toString()), false);
        if (parent == null) throw new KeeperException.NoNodeException(path.toString());
        MemoryFileSystem.Node node = parent.children().get(path.getName());
        if (node == null) throw new KeeperException.NoNodeException(path.toString());
        if (stat != null) stat.setVersion(node.version());
        return node;
    }

    private String nodeName(String baseName, CreateMode createMode) {
        switch (createMode) {
            case PERSISTENT: case EPHEMERAL: case PERSISTENT_WITH_TTL: return baseName;
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
        char[] chars = path.toCharArray();
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
                reason = "invalid character @" + i;
                break;
            }
        }

        if (reason != null)
            throw new IllegalArgumentException("Invalid path string \"" + path + "\" caused by " + reason);
        return path;
    }

    /**
     * Invocation of changes to the file system state is abstracted through this to allow transactional
     * changes to notify on commit
     */
    private abstract static class Listeners {

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

        private final Map<Path, PathChildrenCacheListener> directoryListeners = new ConcurrentHashMap<>();
        private final Map<Path, NodeCacheListener> fileListeners = new ConcurrentHashMap<>();

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
                Set<Map.Entry<Path, PathChildrenCacheListener>> directoryListenerSnapshot = new HashSet<>(directoryListeners.entrySet());
                for (Map.Entry<Path, PathChildrenCacheListener> listener : directoryListenerSnapshot) {
                    if (path.isChildOf(listener.getKey()))
                        listener.getValue().childEvent(MockCuratorFramework.this, event);
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

    private class MockCompletionWaiter implements Curator.CompletionWaiter {

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

        public boolean timeoutOnLock = false;
        public boolean throwExceptionOnLock = false;

        private final String path;

        private Lock lock = null;

        public MockLock(String path) {
            super(MockCuratorFramework.this, path);
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
            super(MockCuratorFramework.this, path, new RetryForever(1_000));
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

        public AtomicValue<Long> add(Long delta) {
            return trySet(value.postValue() + delta);
        }

        public AtomicValue<Long> subtract(Long delta) {
            return trySet(value.postValue() - delta);
        }

        @Override
        public AtomicValue<Long> increment() {
            return trySet(value.postValue() + 1);
        }

        public AtomicValue<Long> decrement() {
            return trySet(value.postValue() - 1);
        }

        @Override
        public AtomicValue<Long> trySet(Long longval) {
            value = new MockLongValue(longval);
            return value;
        }

        public void forceSet(Long newValue) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public AtomicValue<Long> compareAndSet(Long expectedValue, Long newValue) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

    }

    private static class MockLongValue implements AtomicValue<Long> {

        private final AtomicLong value = new AtomicLong();

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

    private class MockDirectoryCache implements Curator.DirectoryCache {

        /** The path this is caching and listening to */
        private final Path path;

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

        @Override
        public ChildData getCurrentData(Path fullPath) {
            if (!fullPath.getParentPath().equals(path)) {
                throw new IllegalArgumentException("Path '" + fullPath + "' is not a child path of '" + path + "'");
            }

            return getData(fullPath).map(bytes -> new ChildData(fullPath.getAbsolute(), null, bytes)).orElse(null);
        }

        @Override
        public void close() {}

        private List<String> getChildren(Path path) {
            try {
                return MockCuratorFramework.this.getChildren().forPath(path.getAbsolute());
            } catch (KeeperException.NoNodeException e) {
                return List.of();
            } catch (Exception e) {
                throw new RuntimeException("Could not get children of " + path.getAbsolute(), e);
            }
        }

        private Optional<byte[]> getData(Path path) {
            try {
                return Optional.of(MockCuratorFramework.this.getData().forPath(path.getAbsolute()));
            }
            catch (KeeperException.NoNodeException e) {
                return Optional.empty();
            }
            catch (Exception e) {
                throw new RuntimeException("Could not get data at " + path.getAbsolute(), e);
            }
        }

    }

    private class MockFileCache implements Curator.FileCache {

        /** The path this is caching and listening to */
        private final Path path;

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
            MemoryFileSystem.Node node = fileSystem.root().getNode(Paths.get(path.toString()), false);
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
    private abstract static class MockProtectACLCreateModeStatPathAndBytesable<String>
            implements ProtectACLCreateModeStatPathAndBytesable<String> {

        Stat stat = null;
        public BackgroundPathAndBytesable<String> withACL(List<ACL> list) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public BackgroundPathAndBytesable<String> withACL(List<ACL> list, boolean b) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public ProtectACLCreateModeStatPathAndBytesable<String> withMode(CreateMode createMode) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ACLCreateModeBackgroundPathAndBytesable<java.lang.String> withProtection() {
            return null;
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground() {
            return null;
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(Object o) {
            return null;
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(BackgroundCallback backgroundCallback) {
            return null;
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(BackgroundCallback backgroundCallback, Object o) {
            return null;
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(BackgroundCallback backgroundCallback, Executor executor) {
            return null;
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(BackgroundCallback backgroundCallback, Object o, Executor executor) {
            return null;
        }

        @Override
        public ACLBackgroundPathAndBytesable<String> storingStatIn(Stat stat) {
            this.stat = stat;
            return this;
        }

    }

    private class MockCreateBuilder implements CreateBuilder {

        private boolean createParents = false;
        private CreateMode createMode = CreateMode.PERSISTENT;
        private Long ttl;

        @Override
        public ProtectACLCreateModeStatPathAndBytesable<String> creatingParentsIfNeeded() {
            createParents = true;
            return new MockProtectACLCreateModeStatPathAndBytesable<>() {

                @Override
                public String forPath(String s, byte[] bytes) throws Exception {
                    return createNode(s, bytes, createParents, stat, createMode, fileSystem.root(), listeners, ttl);
                }

                @Override
                public String forPath(String s) throws Exception {
                    return createNode(s, new byte[0], createParents, stat, createMode, fileSystem.root(), listeners, ttl);
                }

            };
        }

        @Override
        public ProtectACLCreateModeStatPathAndBytesable<String> creatingParentContainersIfNeeded() {
            return new MockProtectACLCreateModeStatPathAndBytesable<>() {

                @Override
                public String forPath(String s, byte[] bytes) throws Exception {
                    return createNode(s, bytes, createParents, stat, createMode, fileSystem.root(), listeners, ttl);
                }

                @Override
                public String forPath(String s) throws Exception {
                    return createNode(s, new byte[0], createParents, stat, createMode, fileSystem.root(), listeners, ttl);
                }

            };
        }

        @Override
        @Deprecated
        public ACLPathAndBytesable<String> withProtectedEphemeralSequential() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ACLCreateModeStatBackgroundPathAndBytesable<String> withProtection() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public String forPath(String s) throws Exception {
            return createNode(s, new byte[0], createParents, null, createMode, fileSystem.root(), listeners, ttl);
        }

        public String forPath(String s, byte[] bytes) throws Exception {
            return createNode(s, bytes, createParents, null, createMode, fileSystem.root(), listeners, ttl);
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(BackgroundCallback backgroundCallback) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(BackgroundCallback backgroundCallback, Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(BackgroundCallback backgroundCallback, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<String> inBackground(BackgroundCallback backgroundCallback, Object o, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public CreateBuilderMain withTtl(long l) { this.ttl = l; return this; }

        @Override
        public CreateBuilder2 orSetData() {
            return null;
        }

        @Override
        public CreateBuilder2 orSetData(int i) {
            return null;
        }

        @Override
        public CreateBackgroundModeStatACLable compressed() {
            return null;
        }

        @Override
        public CreateProtectACLCreateModePathAndBytesable<String> storingStatIn(Stat stat) {
            return null;
        }

        @Override
        public BackgroundPathAndBytesable<String> withACL(List<ACL> list) {
            return this;
        }

        @Override
        public ACLBackgroundPathAndBytesable<String> withMode(CreateMode createMode) {
            this.createMode = createMode;
            return this;
        }

        @Override
        public BackgroundPathAndBytesable<String> withACL(List<ACL> list, boolean b) {
            return this;
        }

        @Override
        public CreateBuilder2 idempotent() { return null; }

    }

    private static class MockBackgroundPathableBuilder<T> implements BackgroundPathable<T>, Watchable<BackgroundPathable<T>> {

        @Override
        public ErrorListenerPathable<T> inBackground() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<T> inBackground(Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<T> inBackground(BackgroundCallback backgroundCallback) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<T> inBackground(BackgroundCallback backgroundCallback, Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<T> inBackground(BackgroundCallback backgroundCallback, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<T> inBackground(BackgroundCallback backgroundCallback, Object o, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public T forPath(String s) throws Exception {
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
            try {
                Stat stat = new Stat();
                getNode(path, stat, fileSystem.root());
                return stat;
            }
            catch (KeeperException.NoNodeException e) {
                return null;
            }
        }

        @Override
        public ACLableExistBuilderMain creatingParentsIfNeeded() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ACLableExistBuilderMain creatingParentContainersIfNeeded() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }
    }

    private class MockDeleteBuilder extends MockBackgroundPathableBuilder<Void> implements DeleteBuilder {

        private boolean deleteChildren = false;
        private int version = -1;

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
            version = i;
            return this;
        }

        public Void forPath(String pathString) throws Exception {
            deleteNode(pathString, deleteChildren, version, fileSystem.root(), listeners);
            return null;
        }

        @Override
        public DeleteBuilderMain quietly() {
            return this;
        }

        @Override
        public DeleteBuilderMain idempotent() { return this; }

    }

    private class MockGetDataBuilder extends MockBackgroundPathableBuilder<byte[]> implements GetDataBuilder {

        @Override
        public GetDataWatchBackgroundStatable decompressed() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        public byte[] forPath(String path) throws Exception {
            return getData(path, null, fileSystem.root());
        }

        @Override
        public ErrorListenerPathable<byte[]> inBackground() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<byte[]> inBackground(Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<byte[]> inBackground(BackgroundCallback backgroundCallback) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<byte[]> inBackground(BackgroundCallback backgroundCallback, Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<byte[]> inBackground(BackgroundCallback backgroundCallback, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathable<byte[]> inBackground(BackgroundCallback backgroundCallback, Object o, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public WatchPathable<byte[]> storingStatIn(Stat stat) {
            return new WatchPathable<byte[]>() {
                @Override
                public byte[] forPath(String path) throws Exception {
                    return getData(path, stat, fileSystem.root());
                }

                @Override
                public Pathable<byte[]> watched() {
                    return null;
                }

                @Override
                public Pathable<byte[]> usingWatcher(Watcher watcher) {
                    return null;
                }

                @Override
                public Pathable<byte[]> usingWatcher(CuratorWatcher watcher) {
                    return null;
                }
            };
        }
    }

    // extends MockBackgroundACLPathAndBytesableBuilder<Stat>
    private class MockSetDataBuilder implements SetDataBuilder {

        int version = -1;
        @Override
        public SetDataBackgroundVersionable compressed() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public BackgroundPathAndBytesable<Stat> withVersion(int i) {
            version = i;
            return this;
        }

        @Override
        public Stat forPath(String path, byte[] bytes) throws Exception {
            Stat stat = new Stat();
            setData(path, bytes, version, stat, fileSystem.root(), listeners);
            return stat;
        }

        @Override
        public Stat forPath(String path) throws Exception {
            Stat stat = new Stat();
            setData(path, new byte[0], version, stat, fileSystem.root(), listeners);
            return stat;
        }

        @Override
        public ErrorListenerPathAndBytesable<Stat> inBackground() {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<Stat> inBackground(Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<Stat> inBackground(BackgroundCallback backgroundCallback) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<Stat> inBackground(BackgroundCallback backgroundCallback, Object o) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<Stat> inBackground(BackgroundCallback backgroundCallback, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public ErrorListenerPathAndBytesable<Stat> inBackground(BackgroundCallback backgroundCallback, Object o, Executor executor) {
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        @Override
        public SetDataBuilder idempotent() { return this; }

    }

    /** Allows addition of directoryListeners which are never called */
    public static class MockListenable<T> implements Listenable<T> {

        public final List<T> listeners = new ArrayList<>();

        @Override
        public void addListener(T t) {
            listeners.add(t);
        }

        @Override
        public void addListener(T t, Executor executor) { throw new UnsupportedOperationException("not supported in mock curator"); }

        @Override
        public void removeListener(T t) {
            listeners.remove(t);
        }

    }

    private class MockCuratorTransactionFinal implements CuratorTransactionFinal {

        /** The new directory root in which the transactional changes are made */
        private MemoryFileSystem.Node newRoot;

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
        public TransactionCreateBuilder<CuratorTransactionBridge> create() {
            ensureNotCommitted();
            return new MockTransactionCreateBuilder();
        }

        @Override
        public TransactionDeleteBuilder<CuratorTransactionBridge> delete() {
            ensureNotCommitted();
            return new MockTransactionDeleteBuilder();
        }

        @Override
        public TransactionSetDataBuilder<CuratorTransactionBridge> setData() {
            ensureNotCommitted();
            return new MockTransactionSetDataBuilder();
        }

        @Override
        public TransactionCheckBuilder<CuratorTransactionBridge> check() {
            ensureNotCommitted();
            throw new UnsupportedOperationException("Not implemented in MockCurator");
        }

        private void ensureNotCommitted() {
            if (committed) throw new IllegalStateException("transaction already committed");
        }

        private class MockTransactionCreateBuilder implements TransactionCreateBuilder<CuratorTransactionBridge> {

            private CreateMode createMode = CreateMode.PERSISTENT;

            @Override
            public ACLCreateModePathAndBytesable<CuratorTransactionBridge> compressed() {
                throw new UnsupportedOperationException("Not implemented in MockCurator");
            }

            @Override
            public ACLPathAndBytesable<CuratorTransactionBridge> withMode(CreateMode createMode) {
                this.createMode = createMode;
                return this;
            }

            @Override
            public CuratorTransactionBridge forPath(String s, byte[] bytes) throws Exception {
                createNode(s, bytes, false, null, createMode, newRoot, delayedListener, null);
                return new MockCuratorTransactionBridge();
            }

            @Override
            public CuratorTransactionBridge forPath(String s) throws Exception {
                createNode(s, new byte[0], false, null, createMode, newRoot, delayedListener, null);
                return new MockCuratorTransactionBridge();
            }

            @Override
            public TransactionCreateBuilder2<CuratorTransactionBridge> withTtl(long l) {
                return this;
            }

            @Override
            public MockTransactionCreateBuilder withACL(List<ACL> list, boolean b) {
                return this;
            }

            @Override
            public MockTransactionCreateBuilder withACL(List<ACL> list) {
                return this;
            }
        }

        private class MockTransactionDeleteBuilder implements TransactionDeleteBuilder<CuratorTransactionBridge> {

            int version = -1;
            @Override
            public Pathable<CuratorTransactionBridge> withVersion(int i) {
                version = i;
                return this;
            }

            @Override
            public CuratorTransactionBridge forPath(String path) throws Exception {
                deleteNode(path, false, version, newRoot, delayedListener);
                return new MockCuratorTransactionBridge();
            }

        }

        private class MockTransactionSetDataBuilder implements TransactionSetDataBuilder<CuratorTransactionBridge> {

            int version = -1;
            @Override
            public VersionPathAndBytesable<CuratorTransactionBridge> compressed() {
                throw new UnsupportedOperationException("Not implemented in MockCurator");
            }

            @Override
            public PathAndBytesable<CuratorTransactionBridge> withVersion(int i) {
                version = i;
                return this;
            }

            @Override
            public CuratorTransactionBridge forPath(String s, byte[] bytes) throws Exception {
                MockCuratorFramework.this.setData(s, bytes, version, null, newRoot, delayedListener);
                return new MockCuratorTransactionBridge();
            }

            @Override
            public CuratorTransactionBridge forPath(String s) throws Exception {
                MockCuratorFramework.this.setData(s, new byte[0], version, null, newRoot, delayedListener);
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

}
