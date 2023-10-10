// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.yahoo.search.predicate.serialization.SerializationHelper;
import org.eclipse.collections.api.map.primitive.LongObjectMap;
import org.eclipse.collections.api.tuple.primitive.LongObjectPair;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An index mapping keys of type Long to lists of postings of generic data.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class SimpleIndex {

    private final LongObjectMap<Entry> dictionary;

    public SimpleIndex(LongObjectMap<Entry> dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * Retrieves a posting list for a given key
     *
     * @param key the key to lookup
     * @return list of postings
     */
    public Entry getPostingList(long key) {
        return dictionary.get(key);
    }

    public void writeToOutputStream(DataOutputStream out) throws IOException {
        out.writeInt(dictionary.size());
        for (LongObjectPair<Entry> pair : dictionary.keyValuesView()) {
            out.writeLong(pair.getOne());
            Entry entry = pair.getTwo();
            SerializationHelper.writeIntArray(entry.docIds, out);
            SerializationHelper.writeIntArray(entry.dataRefs, out);
        }
    }

    public static SimpleIndex fromInputStream(DataInputStream in) throws IOException {
        int nEntries = in.readInt();
        LongObjectHashMap<Entry> dictionary = new LongObjectHashMap<>(nEntries);
        for (int i = 0; i < nEntries; i++) {
            long key = in.readLong();
            int[] docIds = SerializationHelper.readIntArray(in);
            int[] dataRefs = SerializationHelper.readIntArray(in);
            dictionary.put(key, new Entry(docIds, dataRefs));
        }
        dictionary.compact();
        return new SimpleIndex(dictionary);
    }

    public static class Entry {
        public final int[] docIds;
        public final int[] dataRefs;

        private Entry(int[] docIds, int[] dataRefs) {
            this.docIds = docIds;
            this.dataRefs = dataRefs;
        }
    }

    public static class Builder {
        private final HashMap<Long, List<Posting>> dictionaryBuilder = new HashMap<>();
        private int entryCount;

        /**
         * Inserts an object with an id for a key.
         * @param key Key to map from
         * @param posting Entry for the posting list
         */
        public void insert(long key, Posting posting) {
            dictionaryBuilder.computeIfAbsent(key, (k) -> new ArrayList<>()).add(posting);
            ++entryCount;
        }

        public SimpleIndex build() {
            LongObjectHashMap<Entry> dictionary = new LongObjectHashMap<>();
            for (Map.Entry<Long, List<Posting>> entry : dictionaryBuilder.entrySet()) {
                List<Posting> postings = entry.getValue();
                Collections.sort(postings);
                int size = postings.size();
                int[] docIds = new int[size];
                int[] dataRefs = new int[size];
                for (int i = 0; i < size; i++) {
                    Posting posting = postings.get(i);
                    docIds[i] = posting.getId();
                    dataRefs[i] = posting.getDataRef();
                }
                dictionary.put(entry.getKey(), new Entry(docIds, dataRefs));
            }
            dictionary.compact();
            return new SimpleIndex(dictionary);
        }

        public int getEntryCount() { return entryCount; }
        public int getKeyCount() { return dictionaryBuilder.size(); }
    }

}
