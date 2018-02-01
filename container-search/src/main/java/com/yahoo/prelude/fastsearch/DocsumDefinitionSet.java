// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Slime;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.prelude.ConfigurationException;
import com.yahoo.container.search.LegacyEmulationConfig;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A set of docsum definitions
 *
 * @author bratseth
 * @author Bjørn Borud
 */
public final class DocsumDefinitionSet {

    public static final int SLIME_MAGIC_ID = 0x55555555;
    private final static Logger log = Logger.getLogger(DocsumDefinitionSet.class.getName());

    private final HashMap<Long, DocsumDefinition> definitions = new HashMap<>();
    private final HashMap<String, DocsumDefinition> definitionsByName = new HashMap<>();
    private final LegacyEmulationConfig emulationConfig;

    public DocsumDefinitionSet(DocumentdbInfoConfig.Documentdb config) {
        this.emulationConfig = new LegacyEmulationConfig(new LegacyEmulationConfig.Builder());
        configure(config);
    }

    public DocsumDefinitionSet(DocumentdbInfoConfig.Documentdb config, LegacyEmulationConfig emulConfig) {
        this.emulationConfig = emulConfig;
        configure(config);
    }

    /** Returns a docsum definition by id
     * @param id document summary class id
     * @return a DocsumDefinition for the id, if found.
     */
    public final DocsumDefinition getDocsumDefinition(long id) {
        return definitions.get(new Long(id));
    }

    /**
     * Returns a docsum definition by name, or null if not found
     *
     * @param name the name of the summary class to use, or null to use the name "default"
     * @return the summary class found, or null if none
     */
    public final DocsumDefinition getDocsumDefinition(String name) {
        if (name == null)
            name="default";
        return definitionsByName.get(name);
    }

    /**
     * Makes data available for decoding for the given hit.
     *
     * @param summaryClass the requested summary class
     * @param data docsum data from backend
     * @param hit the Hit corresponding to this document summary
     * @throws ConfigurationException if the summary class of this hit is missing
     */
    public final void lazyDecode(String summaryClass, byte[] data, FastHit hit) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        long docsumClassId = buffer.getInt();
        if (docsumClassId != SLIME_MAGIC_ID) {
            throw new IllegalArgumentException("Only expecting SchemaLess docsums - summary class:" + summaryClass + " hit:" + hit);
        }
        DocsumDefinition docsumDefinition = lookupDocsum(summaryClass);
        Slime value = BinaryFormat.decode(buffer.array(), buffer.arrayOffset()+buffer.position(), buffer.remaining());
        hit.addSummary(docsumDefinition, new SlimeAdapter(value.get()));
    }

    private DocsumDefinition lookupDocsum(long docsumClassId) {
        DocsumDefinition docsumDefinition = getDocsumDefinition(docsumClassId);
        if (docsumDefinition == null) {
            throw new ConfigurationException("Received hit with summary id " + docsumClassId +
                    ", but this summary class is not in current summary config (" + toString() + ")" +
                    " (that is, the system is in an inconsistent state)");
        }
        return docsumDefinition;
    }

    private DocsumDefinition lookupDocsum(String summaryClass) {
        DocsumDefinition ds = definitionsByName.get(summaryClass);
        if (ds == null) {
            ds = definitionsByName.get("default");
        }
        if (ds == null) {
            throw new ConfigurationException("Fetched hit with summary class " + summaryClass +
                    ", but this summary class is not in current summary config (" + toString() + ")" +
                    " (that is, you asked for something unknown, and no default was found)");
        }
        return ds;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        Set<Map.Entry<Long, DocsumDefinition>> entrySet = definitions.entrySet();
        boolean first = true;
        for (Iterator<Map.Entry<Long, DocsumDefinition>> itr = entrySet.iterator(); itr.hasNext(); ) {
            if (!first) {
                sb.append(",");
            } else {
                first = false;
            }
            Map.Entry<Long, DocsumDefinition> entry = itr.next();
            sb.append("[").append(entry.getKey()).append(",").append(entry.getValue().getName()).append("]");
        }
        return sb.toString();
    }

    public int size() {
        return definitions.size();
    }

    private void configure(DocumentdbInfoConfig.Documentdb config) {
        for (int i = 0; i < config.summaryclass().size(); ++i) {
            DocumentdbInfoConfig.Documentdb.Summaryclass sc = config.summaryclass(i);
            DocsumDefinition docSumDef = new DocsumDefinition(sc, emulationConfig);
            definitions.put((long) sc.id(), docSumDef);
            definitionsByName.put(sc.name(), docSumDef);
        }
        if (definitions.size() == 0) {
            log.warning("No summary classes found in DocumentdbInfoConfig.Documentdb");
        }
    }
}
