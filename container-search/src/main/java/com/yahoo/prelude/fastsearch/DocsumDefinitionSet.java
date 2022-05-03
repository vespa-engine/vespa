// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.Schema;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.data.access.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.prelude.ConfigurationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yahoo.data.access.Type.OBJECT;

/**
 * A set of docsum definitions
 *
 * @author bratseth
 * @author Bjørn Borud
 */
public final class DocsumDefinitionSet {

    public static final int SLIME_MAGIC_ID = 0x55555555;

    private final Map<String, DocsumDefinition> definitionsByName;

    public DocsumDefinitionSet(Schema schema) {
        this(schema.documentSummaries().values());
    }

    public DocsumDefinitionSet(Collection<DocumentSummary> docsumDefinitions) {
        this.definitionsByName = docsumDefinitions.stream()
                                                  .map(summary -> new DocsumDefinition(summary))
                                                  .collect(Collectors.toUnmodifiableMap(summary -> summary.name(),
                                                                                        summary -> summary));
    }

    /**
     * Returns the summary definition of the given name, or the default if not found.
     *
     * @throws ConfigurationException if the requested summary class is not found and there is none called "default"
     */
    public DocsumDefinition getDocsum(String summaryClass) {
        if (summaryClass == null)
            summaryClass = "default";
        DocsumDefinition ds = definitionsByName.get(summaryClass);
        if (ds == null)
            ds = definitionsByName.get("default");
        if (ds == null)
            throw new ConfigurationException("Fetched hit with summary class " + summaryClass +
                                             ", but this summary class is not in current summary config (" + this + ")" +
                                             " (that is, you asked for something unknown, and no default was found)");
        return ds;
    }

    /** Do we have a summary definition with the given name */
    public boolean hasDocsum(String summaryClass) {
        if (summaryClass == null)
            summaryClass = "default";
        return definitionsByName.containsKey(summaryClass);
    }

    /**
     * Makes data available for decoding for the given hit.
     *
     * @param summaryClass the requested summary class
     * @param data docsum data from backend
     * @param hit the Hit corresponding to this document summary
     * @return Error message or null on success.
     * @throws ConfigurationException if the summary class of this hit is missing
     */
    public String lazyDecode(String summaryClass, byte[] data, FastHit hit) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        long docsumClassId = buffer.getInt();
        if (docsumClassId != SLIME_MAGIC_ID) {
            throw new IllegalArgumentException("Only expecting SchemaLess docsums - summary class:" + summaryClass + " hit:" + hit);
        }
        DocsumDefinition docsumDefinition = getDocsum(summaryClass);
        Slime value = BinaryFormat.decode(buffer.array(), buffer.arrayOffset()+buffer.position(), buffer.remaining());
        Inspector docsum = new SlimeAdapter(value.get());
        if (docsum.type() != OBJECT) {
            return "Hit " + hit + " failed: " + docsum.asString();
        }
        hit.addSummary(docsumDefinition, docsum);
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, DocsumDefinition> e : definitionsByName.entrySet() ) {
            if (sb.length() != 0) {
                sb.append(",");
            }
            sb.append("[").append(e.getKey()).append(",").append(e.getValue().name()).append("]");
        }
        return sb.toString();
    }

    public int size() {
        return definitionsByName.size();
    }

}
