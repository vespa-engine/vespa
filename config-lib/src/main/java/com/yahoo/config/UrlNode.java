// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a 'url' in a {@link ConfigInstance}, which will be downloaded
 * and made available as a {@link File}. Stored in the config builder as a
 * {@link UrlReference} to identify fields of this type for special handling
 * in the ConfigPayloadApplier.
 *
 * @author lesters
 */
public class UrlNode extends LeafNode<File> {

    private final UrlReference url;

    public UrlNode() {
        url = null;
    }

    public UrlNode(UrlReference url) {
        super(true);
        this.url = url;
        this.value = new File(url.value());
    }

    public UrlNode(String url) {
        this(new UrlReference(url));
    }

    public File value() {
        return value;
    }

    @Override
    public String toString() {
        return (value == null) ? "(null)" : '"' + getValue() + '"';
    }

    @Override
    public String getValue() {
        return value.toString();
    }

    @Override
    protected boolean doSetValue(String value) {
        throw new UnsupportedOperationException("doSetValue should not be necessary since the library anymore!");
    }

    public UrlReference getUrlReference() {
        return url;
    }

    public static List<UrlReference> toUrlReferences(List<UrlNode> urlNodes) {
        return urlNodes.stream().map(UrlNode::getUrlReference).collect(Collectors.toList());
    }

    public static Map<String, UrlReference> toUrlReferenceMap(Map<String, UrlNode> urlNodeMap) {
        return urlNodeMap.entrySet().stream().collect(
                Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getUrlReference()));
    }

    @Override
    void serialize(String name, Serializer serializer) {
        serializer.serialize(name, url.value());
    }

    @Override
    void serialize(Serializer serializer) {
        serializer.serialize(url.value());
    }

}
