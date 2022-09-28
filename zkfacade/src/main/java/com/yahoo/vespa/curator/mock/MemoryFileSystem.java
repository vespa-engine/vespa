// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.mock;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple in-memory "file system" useful for Curator caching/mocking.
 *
 * @author bratseth
 */
class MemoryFileSystem extends FileSystem {

    private Node root = new Node(null, "");

    @Override
    public FileSystemProvider provider() {
        throw new UnsupportedOperationException("Not implemented in MemoryFileSystem");
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singleton(Paths.get("/"));
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException("Not implemented in MemoryFileSystem");
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Collections.emptySet();
    }

    @Override
    public Path getPath(String first, String... more) {
        return Paths.get(first, more);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException("Not implemented in MemoryFileSystem");
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("Not implemented in MemoryFileSystem");
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Not implemented in MemoryFileSystem");
    }

    /** Returns the root of this file system */
    public Node root() { return root; }

    /** Replaces the directory root. This is used to implement transactional changes to the file system */
    public void replaceRoot(Node newRoot) { this.root = newRoot; }

    /**
     * Lists the entire content of this file system as a multiline string.
     * Useful for debugging.
     */
    public String dumpState() { 
        StringBuilder b = new StringBuilder();
        root.writeRecursivelyTo(b, "");
        return b.toString();
    }

    /**
     * A node in this file system. Nodes may have children (a "directory"),
     * content (a "file"), or both.
     */
    public static class Node implements Cloneable {

        /** The parent of this node, or null if this is the root */
        private final Node parent;

        /** The local name of this node */
        private final String name;

        /** The content of this node, never null. This buffer is effectively immutable. */
        private byte[] content;

        /** Optional TTL. Currently not in use. */
        private Long ttl;

        private final AtomicInteger version = new AtomicInteger(0);

        private Map<String, Node> children = Collections.synchronizedMap(new LinkedHashMap<>());

        private Node(Node parent, String name) {
            this(parent, name, new byte[0]);
        }

        private Node(Node parent, String name, byte[] content) {
            this.parent = parent;
            this.name = name;
            this.content = Arrays.copyOf(content, content.length);
        }

        /** Returns a copy of the content of this node */
        public byte[] getContent() { return Arrays.copyOf(content, content.length); }

        /** Replaces the content of this file */
        public void setContent(byte[] content) {
            this.content = Arrays.copyOf(content, content.length);
            this.version.incrementAndGet();
        }

        /** Set optional TTL */
        public void setTtl(long ttl) { this.ttl = ttl; }

        public int version() { return version.get(); }

        /**
         * Returns the node given by the path.
         *
         * @param  create if true, any missing directories are created
         * @return the node, or null if it does not exist
         */
        public Node getNode(Path path, boolean create) {
            if (path.getNameCount() == 0 || path.toString().isEmpty()) return this;
            String childName = path.getName(0).toString();
            Node child = children.get(childName);
            if (child == null) {
                if (create)
                    child = add(childName);
                else
                    return null;
            }
            // invariant: child exists

            if (path.getNameCount() == 1)
                return child;
            else
                return child.getNode(path.subpath(1, path.getNameCount()), create);
        }

        /** Returns the parent of this, or null if it is the root */
        public Node parent() { return parent; }

        public boolean isRoot() { return parent == null; }

        /** Returns the local name of this node */
        public String name() { return name; }

        /** Adds an empty node to this and returns it. If it already exists this does nothing but return the node */
        public Node add(String name) {
            if (children.containsKey(name)) return children.get(name);

            Node child = new Node(this, name);
            children.put(name, child);
            return child;
        }

        /**
         * Adds a node to this. If it already exists it is replaced.
         *
         * @return the node which was replaced by this, or null if none
         */
        public Node add(Node node) {
            return children.put(node.name(), node);
        }

        /**
         * Removes the given child node of this, whether or not it has children.
         * If it does not exists, this does nothing.
         *
         * @return the removed node, or null if none
         */
        public Node remove(String name) {
            return children.remove(name);
        }

        /** Returns an unmodifiable map of the immediate children of this indexed by their local name */
        public Map<String, Node> children() { return Collections.unmodifiableMap(children); }

        /** 
         * Dumps the content of this and all children recursively to the given string builder.
         * Useful for debugging.
         * 
         * @param b the builder to write to
         * @param pathPrefix, the path to this node, never ending by "/"
         */
        public void writeRecursivelyTo(StringBuilder b, String pathPrefix) {
            String path = ( pathPrefix.equals("/") ? "" : pathPrefix ) + "/" + name;
            b.append(path);
            if (content.length > 0)
                b.append(" [content: ").append(content.length).append(" bytes]");
            b.append("\n");
            
            for (Node child : children.values())
                child.writeRecursivelyTo(b, path);
        }

        @Override
        public String toString() {
            return "directory '" + name() + "'";
        }

        /** Returns a deep copy of this node and all nodes below it */
        public Node clone() {
            try {
                Node clone = (Node)super.clone();
                Map<String, Node> cloneChildren = new HashMap<>();
                for (Map.Entry<String, Node> child : this.children.entrySet()) {
                    cloneChildren.put(child.getKey(), child.getValue().clone());
                }
                clone.children = cloneChildren;
                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException("Won't happen");
            }
        }

    }

}
