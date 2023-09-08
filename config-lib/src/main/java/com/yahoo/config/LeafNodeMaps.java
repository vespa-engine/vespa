// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author gjoranv
 */
public class LeafNodeMaps {

    /**
     * Converts a map of String→NODE to String→REAL, where REAL is the underlying value type.
     */
    public static <NODE extends LeafNode<REAL>, REAL>
    Map<String, REAL> asValueMap(Map<String, NODE> input)
    {
        Map<String, REAL> ret = new LinkedHashMap<>();
        for(String key : input.keySet()) {
            ret.put(key, input.get(key).value());
        }
        return Collections.unmodifiableMap(ret);
    }

    /**
     * Converts a map of String→REAL to String→NODE, where REAL is the underlying value type.
     */
    @SuppressWarnings("unchecked")
    public static <NODE extends LeafNode<REAL>, REAL>
    Map<String, NODE> asNodeMap(Map<String, REAL> input, NODE defaultNode)
    {
        Map<String, NODE> ret = new LinkedHashMap<>();
        for(String key : input.keySet()) {
            NODE node = (NODE)defaultNode.clone();
            node.value = input.get(key);
            ret.put(key, node);
        }
        return Collections.unmodifiableMap(ret);
    }


    /**
     * Special case for file type, since FileNode param type (FileReference) is not same as type (String) in config builder
     */
    public static Map<String, FileNode> asFileNodeMap(Map<String, String> stringMap) {
        Map<String, FileNode> fileNodeMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : stringMap.entrySet()) {
            fileNodeMap.put(e.getKey(), new FileNode(e.getValue()));
        }
        return Collections.unmodifiableMap(fileNodeMap);
    }

    public static Map<String, PathNode> asPathNodeMap(Map<String, FileReference> fileReferenceMap) {
        Map<String, PathNode> pathNodeMap = new LinkedHashMap<>();
        for (Map.Entry<String, FileReference> e : fileReferenceMap.entrySet()) {
            pathNodeMap.put(e.getKey(), new PathNode(e.getValue()));
        }
        return Collections.unmodifiableMap(pathNodeMap);
    }

    public static Map<String, UrlNode> asUrlNodeMap(Map<String, UrlReference> urlReferenceMap) {
        return Collections.unmodifiableMap(
                    urlReferenceMap.entrySet().stream().collect(
                        Collectors.toMap(Map.Entry::getKey, e -> new UrlNode(e.getValue()))
                    ));
    }

    public static Map<String, ModelNode> asModelNodeMap(Map<String, ModelReference> modelReferenceMap) {
        return Collections.unmodifiableMap(
                modelReferenceMap.entrySet().stream().collect(
                        Collectors.toMap(Map.Entry::getKey, e -> new ModelNode(e.getValue()))
                ));
    }

}
