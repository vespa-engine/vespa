// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.ObjectTraverser;
import com.yahoo.document.GlobalId;
import com.yahoo.fs4.QueryPacketData;
import com.yahoo.net.URI;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.data.access.Inspector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A regular hit from a Vespa backend
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class FastHit extends Hit {

    private static final GlobalId emptyGlobalId = new GlobalId(new byte[GlobalId.LENGTH]);

    /** The index of the content node this hit originated at */
    private int distributionKey = 0;

    /** The local identifier of the content store for this hit on the node it originated at */
    private int partId;

    /** The global id of this document in the backend node which produced it */
    private GlobalId globalId = emptyGlobalId;

    /** Full information pointing to the location of further data for this hit. Lazily set */
    private URI indexUri = null;

    private transient QueryPacketData queryPacketData = null;
    private transient CacheKey cacheKey = null;

    /**
     * Summaries added to this hit which are not yet decoded into fields.
     * Fields are resolved by returning the first non-null value found by
     * 1) the field value from the Map of fields in the Hit supertype, and
     * 2) each of the summaries, in the order of the list (which is the add order).
     * This ensures that values set from code overwrites any value received as
     * summary data.
     *
     * The reason we keep this rather than eagerly decoding into a the field map
     * is to reduce garbage collection and decoding cost, with the assumption
     * that most fields passes through the container with no processing most
     * of the time.
     */
    private List<SummaryData> summaries = new ArrayList<>(1);

    /** Removed field values, which should therefore not be returned if present in summary data */
    private Set<String> removedFields = null;

    /**
     * Creates an empty and temporarily invalid summary hit
     */
    public FastHit() { }

    // Note: This constructor is only used for tests, production use is always of the empty constructor
    public FastHit(String uri, double relevancy) {
        this(uri, relevancy, null);
    }

    // Note: This constructor is only used for tests, production use is always of the empty constructor
    public FastHit(String uri, double relevance, String source) {
        setId(uri);
        super.setField("uri", uri); // TODO: Remove on Vespa 7
        setRelevance(new Relevance(relevance));
        setSource(source);
        types().add("summary");
        setPartId(0);
    }

    /** Returns false - this is a concrete hit containing requested content */
    public boolean isMeta() { return false; }

    /**
     * Returns the explicitly set uri if available, returns "index:[source]/[partid]/[id]" otherwise
     *
     * @return uri of hit
     */
    @Override
    public URI getId() {
        URI uri = super.getId();
        if (uri != null) return uri;

        // TODO: Remove on Vespa 7, this should be one of the last vestiges of URL field magic
        if (fields().containsKey("uri")) {
            // trigger decoding
            Object o = getField("uri");
            setId(o.toString());
            return super.getId();
        }

        // Fallback to index:[source]/[partid]/[id]
        if (indexUri != null) return indexUri;
        indexUri = new URI("index:" + getSource() + "/" + getPartId() + "/" + asHexString(getGlobalId()));
        return indexUri;
    }

    /** Returns the global id of this document in the backend node which produced it */
    public GlobalId getGlobalId() { return globalId; }

    public void setGlobalId(GlobalId globalId) { this.globalId = globalId; }

    public int getPartId() { return partId; }

    /**
     * Sets the part id number, which specifies the node where this hit is
     * found. The row count is used to decode the part id into a column and a
     * row number: the number of n least significant bits required to hold the
     * highest row number are the row bits, the rest are column bits.
     */
    public void setPartId(int partId) { this.partId = partId; }

    /** Returns the index of the node this hit originated at */
    public int getDistributionKey() { return distributionKey; }

    /** Returns the index of the node this hit originated at */
    public void setDistributionKey(int distributionKey) { this.distributionKey = distributionKey; }

    /**
     * Add the binary data common for the query packet to a Vespa backend and a
     * summary fetch packet to a Vespa backend. This method can only be called
     * once for a single hit.
     *
     * @param queryPacketData binary data from a query packet resulting in this hit
     * @throws IllegalStateException if the method is called more than once
     * @throws NullPointerException if trying to set query packet data to null
     */
    public void setQueryPacketData(QueryPacketData queryPacketData) {
        if (this.queryPacketData != null)
            throw new IllegalStateException("Query packet data already set to "
                                            + this.queryPacketData + ", tried to set it to " + queryPacketData);
        if (queryPacketData == null)
            throw new NullPointerException("Query packet data reference can not be set to null.");
        this.queryPacketData = queryPacketData;
    }

    /** Returns a serial encoding of the query which produced this hit, ot null if not available. */
    public QueryPacketData getQueryPacketData() { return queryPacketData; }

    CacheKey getCacheKey() { return cacheKey; }

    void setCacheKey(CacheKey cacheKey) { this.cacheKey = cacheKey; }

    /** For internal use */
    public void addSummary(DocsumDefinition docsumDef, Inspector value) {
        summaries.add(new SummaryData(this, docsumDef, value));
    }

    /**
     * <p>Returns a field value from this Hit. The value is either a stored value from the Document represented by
     * this Hit, or a generated value added during later processing.</p>
     *
     * <p>The values available from the matching Document are a <i>subset</i> of the values set in the document,
     * determined by the {@link #getFilled() filled} status of this Hit. More fields may be requested by requesting
     * further filling.</p>
     *
     * <p>Lookups on names which does not exists in the document and is not added by later processing
     * return null.</p>
     *
     * <p>Lookups on fields which exist in the document, in a summary class which is already requested
     * filled returns the following types, even when the field has no actual value:</p>
     *
     * <ul>
     *     <li><b>string and uri fields</b>: A Java String.<br>
     *     The empty string ("") if no value is assigned in the document.
     *
     *     <li><b>Dynamic summary string fields</b>: A Java String before JuniperSearcher and a HitField after.</li>
     *
     *     <li><b>Numerics</b>: The corresponding numeric Java type.<br>
     *     If the field has <i>no value</i> assigned in the document,
     *     the special numeric {@link com.yahoo.search.result.NanNumber#NaN} is returned.
     *
     *     <li><b>raw fields</b>: A {@link com.yahoo.prelude.hitfield.RawData} instance
     *
     *     <li><b>tensor fields</b>: A {@link com.yahoo.tensor.Tensor} instance
     *
     *     <li><b>multivalue fields</b>: A {@link com.yahoo.data.access.Inspector} instance
     * </ul>
     */
    @Override
    public Object getField(String name) {
        Object value = super.getField(name);
        if (value != null) return value;
        value = getSummaryValue(name);
        if (value != null)
            super.setField(name, value);
        return value;
    }

    /** Returns the fields of this as a read-only map. This is more costly than fieldIterator() */
    @Override
    public Map<String, Object> fields() {
        Map<String, Object> fields = new HashMap<>();
        for (Iterator<Map.Entry<String, Object>> i = fieldIterator(); i.hasNext(); ) {
            Map.Entry<String, Object> field = i.next();
            fields.put(field.getKey(), field.getValue());
        }
        return fields;
    }

    /** Returns a modifiable iterator over the fields of this */
    @Override
    public Iterator<Map.Entry<String, Object>> fieldIterator() {
        return new FieldIterator(this, super.fieldIterator());
    }

    /** Returns a modifiable iterator over the field names of this */
    Iterator<String> fieldNameIterator() {
        return new FieldNameIterator(this, super.fieldKeys().iterator());
    }

    /** Removes all fields of this */
    @Override
    public void clearFields() {
        summaries.clear();
        super.clearFields();
    }

    /**
     * Removes a field from this
     *
     * @return the removed value of the field, or null if none
     */
    @Override
    public Object removeField(String name) {
        Object removedValue = super.removeField(name);
        if (removedValue == null)
            removedValue = getSummaryValue(name);

        if (removedValue != null) {
            if (removedFields == null)
                removedFields = new HashSet<>(2);
            removedFields.add(name);
        }

        return removedValue;
    }

    /**
     * Returns the keys of the fields of this hit as a modifiable view.
     * This follows the rules of key sets returned from maps: Key removals are reflected
     * in the map, add and addAll is not supported.
     */
    @Override
    public Set<String> fieldKeys() {
        return new FieldSet(this);
    }

    private Set<String> mapFieldKeys() {
        return super.fieldKeys();
    }

    /** Returns whether this field is present <b>in the field map in the parent hit</b> */
    // Note: If this is made public it must be changed to also check the summary data
    //       (and internal usage must change to another method).
    @Override
    protected boolean hasField(String name) {
        return super.hasField(name);
    }

    /** Returns whether any fields are present <b>in the field map in the parent hit</b> */
    // Note: If this is made public it must be changed to also check the summary data
    //       (and internal usage must change to another method).
    @Override
    protected boolean hasFields() {
        return super.hasFields();
    }

    /**
     * Changes the key under which a value is found. This is useful because it allows keys to be changed
     * without accessing the value (which may be lazily created).
     *
     * @deprecated do not use
     */
    @Deprecated
    @Override
    @SuppressWarnings("deprecation")
    public void changeFieldKey(String oldKey, String newKey) {
        Object value = removeField(oldKey);
        if (value != null)
            setField(newKey, value);
    }

    private Object getSummaryValue(String name) {
        if (removedFields != null && removedFields.contains(name))
            return null;
        for (SummaryData summaryData : summaries) {
            Object value = summaryData.getField(name);
            if (value != null) return value;
        }
        return null;
    }

    @Override
    public String toString() {
        return super.toString() + " [fasthit, globalid: " + globalId + ", partId: "
               + partId + ", distributionkey: " + distributionKey + "]";
    }

    @Override
    public int hashCode() {
        if (getId() == null) {
            throw new IllegalStateException("This hit must have a 'uri' field, and this fild must be filled through " +
                                            "Execution.fill(Result)) before hashCode() is accessed.");
        } else {
            return super.hashCode();
        }
    }

    /** @deprecated do not use */
    @Deprecated // TODO: Make private on Vespa 7
    public static String asHexString(GlobalId gid) {
        StringBuilder sb = new StringBuilder();
        byte[] rawGid = gid.getRawId();
        for (byte b : rawGid) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /** A set view of all the field names in this hit. Add/addAll is not supported but remove is. */
    private static class FieldSet implements Set<String> {

        // A set implementation which tries to avoid creating an actual set when possible.
        // With more work this could be optimized to avoid creating the set in additional cases.

        private final FastHit hit;

        /** The computed set of fields. Lazily created as it is not always needed. */
        private Set<String> fieldSet = null;

        public FieldSet(FastHit hit) {
            this.hit = hit;
        }

        @Override
        public int size() {
            return createSet().size();
        }

        @Override
        public boolean isEmpty() {
            if ( ! hit.hasFields() && hit.summaries.isEmpty()) return true;
            return createSet().isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            String field = (String)o;
            if (hit.hasField(field)) return true;
            return createSet().contains(field);
        }

        @Override
        public Object[] toArray() {
            return createSet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return createSet().toArray(a);
        }

        @Override
        public boolean add(String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            String field = (String)o;
            boolean removed = hit.removeField(field) != null;
            if (fieldSet != null)
                fieldSet.remove(field);
            return removed;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object field : c)
                if ( ! contains(field))
                    return false;
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends String> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            Set<?> toRetain = c instanceof Set<?> ? (Set<?>)c : new HashSet<Object>(c);
            boolean anyRemoved = false;
            for (Iterator<String> i = iterator(); i.hasNext(); ) {
                String field = i.next();
                if (toRetain.contains(field)) continue;

                i.remove();
                anyRemoved = true;
            }
            return anyRemoved;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean anyRemoved = false;
            for (Object field : c)
                if (remove(field))
                    anyRemoved = true;
            return anyRemoved;
        }

        @Override
        public void clear() {
            hit.clearFields();
            fieldSet = null;
        }

        @Override
        public Iterator<String> iterator() {
            return hit.fieldNameIterator();
        }

        private Set<String> createSet() {
            if ( ! hit.hasFields() && hit.summaries.isEmpty()) return Collections.emptySet(); // shortcut

            Set<String> fields = new HashSet<>();
            if (hit.hasFields())
                fields.addAll(hit.mapFieldKeys());

            for (SummaryData summaryData : hit.summaries)
                summaryData.data.traverse((ObjectTraverser)(name, __) -> fields.add(name));
            fields.removeAll(hit.removedFields);

            this.fieldSet = fields;

            return fields;
        }

    }

    /** Summary data (values of a number of fields) received for this hit */
    private static class SummaryData {

        private final FastHit hit;
        private final DocsumDefinition type;
        private final Inspector data;

        SummaryData(FastHit hit, DocsumDefinition type, Inspector data) {
            this.hit = hit;
            this.type = type;
            this.data = data;
        }

        Object getField(String name) {
            DocsumField fieldType = type.getField(name);
            if (fieldType == null) return null;
            Inspector fieldValue = data.field(name);
            if ( ! fieldValue.valid() && ! fieldType.getEmulConfig().forceFillEmptyFields()) return null;
            return fieldType.convert(fieldValue);
        }

        /** Decodes the given summary into the field map in the parent */
        private void decodeInto(FastHit hit) {
            hit.reserve(type.getFieldCount());
            for (DocsumField field : type.getFields()) {
                String fieldName = field.getName();
                Inspector f = data.field(fieldName);
                if (field.getEmulConfig().forceFillEmptyFields() || f.valid()) {
                    if (hit.getField(fieldName) == null)
                        hit.setField(fieldName, field.convert(f));
                }
            }
        }

        private Iterator<Map.Entry<String, Object>> fieldIterator() {
            return new SummaryFieldDataIterator(hit, type, data.fields().iterator());
        }

        private Iterator<String> fieldNameIterator() {
            return new SummaryFieldNameDataIterator(hit, data.fields().iterator());
        }

        /** A wrapper of a field iterator which skips removed fields. Read only. */
        private static abstract class SummaryDataIterator<VALUE> implements Iterator<VALUE> {

            private final FastHit hit;
            private final Iterator<Map.Entry<String, Inspector>> fieldIterator;

            /**
             * The next value or null if none, eagerly read because we need to skip removed and overwritten values
             */
            private Map.Entry<String, Inspector> next;

            SummaryDataIterator(FastHit hit, Iterator<Map.Entry<String, Inspector>> fieldIterator) {
                this.hit = hit;
                this.fieldIterator = fieldIterator;
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public VALUE next() {
                if (next == null) throw new NoSuchElementException();

                VALUE returnValue = toValue(next);
                advanceNext();
                return returnValue;
            }

            protected abstract VALUE toValue(Map.Entry<String, Inspector> field);

            private void advanceNext() {
                while (fieldIterator.hasNext()) {
                    next = fieldIterator.next();
                    if (!hit.hasField(next.getKey()) && !hit.removedFields.contains(next.getKey()))
                        break;
                }
                next = null;
            }
        }

        private static class SummaryFieldDataIterator extends SummaryDataIterator<Map.Entry<String, Object>> {

            private final DocsumDefinition type;

            /** The next value or null if none, eagerly read because we need to skip removed and overwritten values */
            private Map.Entry<String, Inspector> next;

            SummaryFieldDataIterator(FastHit hit, DocsumDefinition type, Iterator<Map.Entry<String, Inspector>> fieldIterator) {
                super(hit, fieldIterator);
                this.type = type;
            }

            @Override
            protected Map.Entry<String, Object> toValue(Map.Entry<String, Inspector> field) {
                return new SummaryFieldEntry(field.getKey(), type.getField(field.getKey()).convert(field.getValue()));
            }

            private static final class SummaryFieldEntry implements Map.Entry<String, Object> {

                private final String key;
                private final Object value;

                public SummaryFieldEntry(String key, Object value) {
                    this.key = key;
                    this.value = value;
                }

                @Override
                public String getKey() { return key; }

                @Override
                public Object getValue() { return value; }

                @Override
                public Object setValue(Object value) { throw new UnsupportedOperationException(); }

            }

        }

        /** A wrapper of a field iterator which converts to the expected field types. Read only. */
        private static class SummaryFieldNameDataIterator extends SummaryDataIterator<String> {

            /** The next value or null if none, eagerly read because we need to skip removed and overwritten values */
            private Map.Entry<String, Inspector> next;

            SummaryFieldNameDataIterator(FastHit hit, Iterator<Map.Entry<String, Inspector>> fieldIterator) {
                super(hit, fieldIterator);
            }

            @Override
            protected String toValue(Map.Entry<String, Inspector> field) {
                return field.toString();
            }

        }
    }

    private static abstract class SummaryIterator<VALUE> implements Iterator<VALUE> {

        private final FastHit hit;

        /** -1 means that the current iterator is the map iterator of the parent hit, not any summary data iterator */
        private int currentSummaryDataIndex = -1;
        private Iterator<VALUE> currentIterator;
        private VALUE previousReturned = null;

        public SummaryIterator(FastHit hit, Iterator<VALUE> mapFieldsIterator) {
            this.hit = hit;
            this.currentIterator = mapFieldsIterator;
        }

        @Override
        public boolean hasNext() {
            if (currentIterator.hasNext()) return true;
            return nextIterator();
        }

        @Override
        public VALUE next() {
            if (currentIterator.hasNext() || nextIterator()) return previousReturned = currentIterator.next();
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            if (previousReturned == null)
                throw new IllegalStateException();
            if ( ! ( currentIterator instanceof SummaryData.SummaryDataIterator))
                currentIterator.remove(); // remove from the map
            hit.removedFields.add(nameOf(previousReturned));
            previousReturned = null;
        }

        protected abstract String nameOf(VALUE value);
        protected abstract Iterator<VALUE> iteratorFor(SummaryData summary);

        /** Advanced to the next non-empty iterator, if any */
        private boolean nextIterator() {
            while (++currentSummaryDataIndex < hit.summaries.size()) {
                currentIterator = iteratorFor(hit.summaries.get(currentSummaryDataIndex));
                if (currentIterator.hasNext())
                    return true;
            }
            return false;
        }

    }

    private static class FieldIterator extends SummaryIterator<Map.Entry<String, Object>> {

        public FieldIterator(FastHit hit, Iterator<Map.Entry<String, Object>> mapFieldsIterator) {
            super(hit, mapFieldsIterator);
        }

        @Override
        protected String nameOf(Map.Entry<String, Object> value) {
            return value.getKey();
        }

        @Override
        protected Iterator<Map.Entry<String, Object>> iteratorFor(SummaryData summary) {
            return summary.fieldIterator();
        }

    }


    private static class FieldNameIterator extends SummaryIterator<String> {

        public FieldNameIterator(FastHit hit, Iterator<String> mapFieldNamesIterator) {
            super(hit, mapFieldNamesIterator);
        }

        @Override
        protected String nameOf(String value) {
            return value;
        }

        @Override
        protected Iterator<String> iteratorFor(SummaryData summary) {
            return summary.fieldNameIterator();
        }

    }

}
