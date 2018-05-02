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

    /**
     * Fetch binary data from the query packet which produced this hit. These
     * data may not be available, this method will then return null.
     *
     * @return wrapped binary data from a query packet, or null
     */
    public QueryPacketData getQueryPacketData() { return queryPacketData; }

    CacheKey getCacheKey() { return cacheKey; }

    void setCacheKey(CacheKey cacheKey) { this.cacheKey = cacheKey; }

    /** For internal use */
    public void addSummary(DocsumDefinition docsumDef, Inspector value) {
        reserve(docsumDef.getFieldCount());
        for (DocsumField field : docsumDef.getFields()) {
            String fieldName = field.getName();
            Inspector f = value.field(fieldName);
            if (field.getEmulConfig().forceFillEmptyFields() || f.valid()) {
                if (super.getField(fieldName) == null) {
                    setField(fieldName, field.convert(f));
                }
            }
        }
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
        return super.getField(key);
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

}
