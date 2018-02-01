// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.common.annotations.Beta;
import com.yahoo.document.GlobalId;
import com.yahoo.fs4.QueryPacketData;
import com.yahoo.net.URI;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.data.access.simple.Value.StringValue;

/**
 * A regular hit from a Vespa backend
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class FastHit extends Hit {

    public static final String SUMMARY = "summary"; // TODO: Remove on Vespa 7

    private static final long serialVersionUID = 298098891191029589L;

    /** The global id of this document in the backend node which produced it */
    private GlobalId globalId = new GlobalId(new byte[GlobalId.LENGTH]);

    /** Part ID */
    private int partId;

    /** DistributionKey (needed to generate getDocsumPacket, for two-phase search) */
    private int distributionKey = 0;

    /** The index uri of this. Lazily set */
    private URI indexUri = null;

    /**
     * The number of least significant bits in the part id which specifies the
     * row in the search cluster which produced this hit. The other bits
     * specifies the column. 0 if not known.
     */
    private int rowBits = 0;

    /**
     * Whether or not to ignore the row bits. If this is set, FastSearcher is
     * allowed to choose an appropriate row.
     */
    private boolean ignoreRowBits = false;

    /** Whether to use the row number in the index uri, see FastSearcher for details */
    private boolean useRowInIndexUri = true;

    private transient QueryPacketData queryPacketData = null;
    private transient CacheKey cacheKey = null;

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
        types().add(SUMMARY); // TODO: Remove on Vespa 7
        setPartId(0, 0);
    }

    public String toString() {
        return super.toString() + " [fasthit, globalid: " + globalId + ", partId: "
            + partId + ", distributionkey: " + distributionKey + "]";
    }

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

    @Override
    public int hashCode() {
        if (getId() == null) {
            throw new IllegalStateException("This hit must have a 'uri' field, and this fild must be filled through " +
                                            "Execution.fill(Result)) before hashCode() is accessed.");
        } else {
            return super.hashCode();
        }
    }

    @Override
    public URI getId() {
        return getUri(); // Make sure we decode it if the id is encoded
    }

    /**
     * Returns the explicitly set uri if available, returns "index:[source]/[partid]/[id]" otherwise
     *
     * @return uri of hit
     */
    public URI getUri() {
        URI uri = super.getId();
        if (uri != null) return uri;

        // TODO: Remove, this should be one of the last vestiges of URL field magic
        if (fields().containsKey("uri")) {
            // trigger decoding
            Object o = getField("uri");
            setId(o.toString());
            return super.getId();
        }

        return getIndexUri();
    }

    /**
     * The uri of the index location of this hit ("index:[source]/[partid]/[id]").
     * This is the uri if no other uri is assigned
     *
     * @return uri to the index.
     */
    public URI getIndexUri() {
        if (indexUri != null) return indexUri;

        String rowString = "-";
        if (useRowInIndexUri)
            rowString = String.valueOf(getRow());

        indexUri = new URI("index:" + getSourceNumber() + "/" + getColumn() + "/" + rowString + "/" + asHexString(getGlobalId()));
        return indexUri;
    }

    /** Returns the global id of this document in the backend node which produced it */
    public GlobalId getGlobalId() {
        return globalId;
    }

    public void setGlobalId(GlobalId globalId) {
        this.globalId = globalId;
    }

    public int getPartId() {
        return partId;
    }

    /**
     * Sets the part id number, which specifies the node where this hit is
     * found. The row count is used to decode the part id into a column and a
     * row number: the number of n least significant bits required to hold the
     * highest row number are the row bits, the rest are column bits.
     *
     * @param partId  partition id
     * @param rowBits number of bits to encode row number
     */
    public void setPartId(int partId, int rowBits) {
        this.partId = partId;
        this.rowBits = rowBits;
    }

    /**
     * Sets whether to use the row in the index uri. See FastSearcher for details.
     */
    public void setUseRowInIndexUri(boolean useRowInIndexUri) {
        this.useRowInIndexUri = useRowInIndexUri;
    }

    /**
     * Returns the column number where this hit originated, or partId if not known
     */
    public int getColumn() {
        return partId >>> rowBits;
    }

    /**
     *  Returns the row number where this hit originated, or 0 if not known
     * */
    public int getRow() {
        if (rowBits == 0) {
            return 0;
        }

        return partId & ((1 << rowBits) - 1);
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
    public Object getField(String key) {
        Object value = super.getField(key);

        if (value instanceof LazyValue) {
            return getAndCacheLazyValue(key, (LazyValue) value);
        } else {
            return value;
        }
    }

    private Object getAndCacheLazyValue(String key, LazyValue value) {
        Object forcedValue = value.getValue(key);
        setField(key, forcedValue);
        return forcedValue;
    }

    /** Returns false - this is a concrete hit containing requested content */
    public boolean isMeta() {
        return false;
    }

    /**
     * Only needed when fetching summaries in 2 phase.
     *
     * @return distribution key of node where the hit originated from
     */
    public int getDistributionKey() {
        return distributionKey;
    }

    /**
     * Only needed when fetching summaries in 2 phase.
     *
     * @param distributionKey Of node where you find this hit.
     */
    public void setDistributionKey(int distributionKey) {
        this.distributionKey = distributionKey;
    }

    void addSummary(DocsumDefinition docsumDef, Inspector value) {
        reserve(docsumDef.getFieldCount());
        for (DocsumField field : docsumDef.getFields()) {
            String fieldName = field.getName();
            Inspector f = value.field(fieldName);
            if (field.getEmulConfig().forceFillEmptyFields() || f.valid()) {
                setDocsumFieldIfNotPresent(fieldName, field.convert(f));
            }
        }
    }

    private void setDocsumFieldIfNotPresent(String fieldName, Object value) {
        if (super.getField(fieldName) == null) {
            setField(fieldName, value);
        }
    }

    /**
     * Set a field to behave like a string type summary field, not decoding raw
     * data till actually used. Added to make testing lazy docsum functionality
     * easier. This is not a method to be used for efficiency, as it causes
     * object allocations.
     *
     * @param fieldName
     *            the name of the field to insert undecoded UTF-8 into
     * @param value
     *            an array of valid UTF-8 data
     */
    @Beta
    public void setLazyStringField(String fieldName, byte[] value) {
        setField(fieldName, new LazyString(new StringField(fieldName), new StringValue(value)));
    }

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

    /**
     * Fetch binary data from the query packet which produced this hit. These
     * data may not be available, this method will then return null.
     *
     * @return wrapped binary data from a query packet, or null
     */
    public QueryPacketData getQueryPacketData() {
        return queryPacketData;
    }

    CacheKey getCacheKey() {
        return cacheKey;
    }

    void setCacheKey(CacheKey cacheKey) {
        this.cacheKey = cacheKey;
    }

    public void setIgnoreRowBits(boolean ignoreRowBits) {
        this.ignoreRowBits = ignoreRowBits;
    }

    public boolean shouldIgnoreRowBits() {
        return ignoreRowBits;
    }

    public boolean fieldIsNotDecoded(String name) {
        return super.getField(name) instanceof LazyValue;
    }

    public RawField fetchFieldAsUtf8(String fieldName) {
        Object value = super.getField(fieldName);
        if (value instanceof LazyValue) {
            return ((LazyValue) value).getFieldAsUtf8(fieldName);
        } else {
            throw new IllegalStateException("Field " + fieldName + " has already been decoded:" + value);
        }
    }

    public static final class RawField {

        private final boolean needXmlEscape;

        private final byte[] contents;

        public RawField(DocsumField fieldType, byte[] contents) {
            needXmlEscape = ! (fieldType instanceof XMLField);
            this.contents = contents;
        }

        public byte [] getUtf8() { return contents; }
        public boolean needXmlEscape() { return needXmlEscape; }

    }

    private static abstract class LazyValue {
        abstract Object getValue(String fieldName);
        abstract RawField getFieldAsUtf8(String fieldName);
    }

    private static class LazyString extends LazyValue {

        private final Inspector value;
        private final DocsumField fieldType;

        LazyString(DocsumField fieldType, Inspector value) {
            assert(value.type() == Type.STRING);
            this.value = value;
            this.fieldType = fieldType;
        }

        Object getValue(String fieldName) {
            return value.asString();
        }

        RawField getFieldAsUtf8(String fieldName) {
            return new RawField(fieldType, value.asUtf8());
        }

    }

}
