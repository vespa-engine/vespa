// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.collections.ArraySet;
import com.yahoo.component.provider.ListenableFreezableClass;
import com.yahoo.net.URI;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.processing.Request;
import com.yahoo.processing.response.Data;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.text.XML;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * <p>A search hit. The identifier of the hit is the uri
 * (the uri is immutable once set).
 * If two hits have the same uri they are equal per definition.
 * Hits are naturally ordered by decreasing relevance.
 * Note that this definition of equals and natural ordering is inconsistent.</p>
 *
 * <p>Hits may be of the <i>meta</i> type, meaning that they contain some information
 * about the query or result which does not represent a particular piece of matched
 * content. Meta hits are not counted in the hit count of the result, and should
 * usually never be filtered out.</p>
 *
 * <p>Some hit sources may produce hits which are not <i>filled</i>. A non-filled
 * hit may miss some or all of its property values. To fill those,
 * {@link com.yahoo.search.Searcher#fill fill} must be called on the search chain by the searcher
 * which requires those properties. This mechanism allows initial filtering to be
 * done of a lightweight version of the hits, which is cheaper if a significant
 * number of hits are filtered out.</p>
 *
 * @author bratseth
 */
public class Hit extends ListenableFreezableClass implements Data, Comparable<Hit>, Cloneable {

    // Collection fields in hits are, when possible lazy because much of the work of a container
    // consists of allocating and then garbage collecting hits

    private static final String DOCUMENT_ID = "documentid";

    /** A collection of string keyed object properties. */
    private Map<String,Object> fields = null;
    private Map<String,Object> unmodifiableFieldMap = null;

    /** Meta data describing how a given searcher should treat this hit. */
    // TODO: The case for this is to allow multiple levels of federation searcher routing.
    //       Replace this by a cleaner specific solution to that problem.
    private Map<Searcher, Object> searcherSpecificMetaData;

    /** The id of this hit */
    private URI id;

    /** The types of this hit */
    private Set<String> types = new ArraySet<>(1);

    /** The relevance of this hit */
    private Relevance relevance;

    /** Says whether this hit is cached */
    private boolean cached = false;

    /**
     * The summary classes for which this hit is filled. If this set
     * is 'null', it means that this hit is unfillable, which is
     * equivalent to a hit where all summary classes have already
     * been filled, or a hit where further filling will
     * yield no extra information, if you prefer to look at it that
     * way.
     */
    private Set<String> filled = null;
    private Set<String> unmodifiableFilled = null;

    /** The name of the source creating this hit */
    private String source = null;

    /**
     * Add number, assigned when adding the hit to a result,
     * used to order equal relevant hit by add order
     */
    private int addNumber = -1;
    private int sourceNumber;

    /** The query which produced this hit. Used for multi phase searching */
    private Query query;

    /**
     * Set to true for hits which does not contain content,
     * but which contains meta information about the query or result
     */
    private boolean meta = false;

    /** If this is true, then this hit will not be counted as a concrete hit */
    private boolean auxiliary = false;

    /**
     * The hit field used to store rank features. TODO: Remove on Vespa 7
     */
    public static final String RANKFEATURES_FIELD = "rankfeatures";
    public static final String SDDOCNAME_FIELD = "sddocname";

    /** Creates an (invalid) empty hit. Id and relevance must be set before handoff */
    protected Hit() {}

    /**
     * Creates a minimal valid hit having relevance 1000
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     */
    public Hit(String id) {
        this(id, 1);
    }

    /**
     * Creates a minimal valid hit having relevance 1
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types referring to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param query the query having this as a hit
     */
    public Hit(String id, Query query) {
        this(id, 1, query);
    }

    /**
     * Creates a minimal valid hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types referring to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance a relevance measure, preferably normalized between 0 and 1
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1
     */
    public Hit(String id, double relevance) {
        this(id,new Relevance(relevance));
    }

    /**
     * Creates a minimal valid hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types referring to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance a relevance measure, preferably normalized between 0 and 1
     * @param query the query having this as a hit
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1
     */
    public Hit(String id, double relevance, Query query) {
        this(id,new Relevance(relevance),query);
    }

    /**
     * Creates a minimal valid hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance the relevance of this hit
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1000
     */
    public Hit(String id, Relevance relevance) {
        this(id, relevance, (String)null);
    }

    /**
     * Creates a minimal valid hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance the relevance of this hit
     * @param query the query having this as a hit
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1000
     */
    public Hit(String id, Relevance relevance, Query query) {
        this(id, relevance,null, query);
    }

    /**
     * Creates a hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance a relevance measure, preferably normalized between 0 and 1
     * @param source the name of the source of this hit, or null if no source is being specified
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1000
     */
    public Hit(String id, double relevance, String source) {
        this(id, new Relevance(relevance), source, null);
    }

    /**
     * Creates a hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance a relevance measure, preferably normalized between 0 and 1
     * @param source the name of the source of this hit, or null if no source is being specified
     * @param query the query having this as a hit
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1000
     */
    public Hit(String id, double relevance, String source, Query query) {
        this(id, new Relevance(relevance), source);
    }

    /**
     * Creates a hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance the relevance of this hit
     * @param source the name of the source of this hit
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1000
     */
    public Hit(String id, Relevance relevance, String source) {
        this(id, relevance, source, null);
    }

    /**
     * Creates a hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance the relevance of this hit
     * @param source the name of the source of this hit
     * @param query the query having this as a hit
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1000
     */
    public Hit(String id, Relevance relevance, String source, Query query) {
        this.id = new URI(id);
        this.relevance = relevance;
        this.source = source;
        this.query = query;
    }

    /** Calls setId(new URI(id)) */
    public void setId(String id) {
        if (this.id != null) throw new IllegalStateException("Attempt to change id of " + this + " to " + id);
        if (id == null) throw new NullPointerException("Attempt to assign id of " + this + " to null");
        assignId(new URI(id));
    }


    /**
     * Initializes the id of this hit.
     *
     * @throws NullPointerException if the uri is null
     * @throws IllegalStateException if the uri of this hit is already set
     */
    public void setId(URI id) {
        if (this.id != null) throw new IllegalStateException("Attempt to change id of " + this + " to " + id);
        assignId(id);
    }

    /**
     * Assigns a new or changed id to this hit.
     * As this is protected, reassigning isn't legal for Hits by default, however, subclasses may allow it
     * using this method.
     */
    protected final void assignId(URI id) {
        if (id == null) throw new NullPointerException("Attempt to assign id of " + this + " to null");
        this.id = id;
    }

    /** Returns the hit id */
    public URI getId() { return id; }

    /**
     * Returns the id to display, or null to not display (render) the id.
     * This is useful to avoid displaying ids when they are not assigned explicitly
     * but are just generated values for internal use.
     * This default implementation returns {@link #getId()}.toString()
     */
    public String getDisplayId() {
        String id = null;

        Object idField = getField(DOCUMENT_ID);
        if (idField != null)
            id = idField.toString();
        if (id == null)
            id = getId() == null ? null : getId().toString();
        return id;
    }

    /** Sets the relevance of this hit */
    public void setRelevance(Relevance relevance) {
        if (relevance == null) throw new NullPointerException("Cannot assign null as relevance");
        this.relevance = relevance;
    }

    /** Does setRelevance(new Relevance(relevance) */
    public void setRelevance(double relevance) {
        setRelevance(new Relevance(relevance));
    }


    /** Returns the relevance of this hit */
    public Relevance getRelevance() { return relevance; }

    /** Sets whether this hit is returned from a cache. Default is false */
    public void setCached(boolean cached) { this.cached = cached; }

    /** Returns whether this hit was added to this result from a cache or not */
    public boolean isCached() { return cached; }

    /**
     * Tag this hit as fillable. This means that additional properties
     * for this hit may be obtained by fetching document
     * summaries. This also enables tracking of which summary classes
     * have been used for filling so far. Invoking this method
     * multiple times is allowed and will have no addition
     * effect. Note that a fillable hit may not be made unfillable.
     **/
    public void setFillable() {
        if (filled == null) {
            filled = Collections.emptySet();
            unmodifiableFilled = filled;
        }
    }

    /**
     * Register that this hit has been filled with properties using
     * the given summary class. Note that this method will implicitly
     * tag this hit as fillable if it is currently not.
     *
     * @param summaryClass summary class used for filling
     **/
    public void setFilled(String summaryClass) {
        if (filled == null || filled.size() == 0) {
            filled = Collections.singleton(summaryClass);
            unmodifiableFilled = filled;
        } else if (filled.size() == 1) {
            filled = new HashSet<>(filled);
            unmodifiableFilled = Collections.unmodifiableSet(filled);

            filled.add(summaryClass);
        } else {
            filled.add(summaryClass);
        }
    }

    public boolean isFillable() {
        return filled != null;
    }

    /**
     * Returns the set of summary classes for which this hit is
     * filled as an unmodifiable set. If this set is 'null', it means that this hit is
     * unfillable, which is equivalent with a hit where all summary
     * classes have already been used for filling, or a hit where
     * further filling will yield no extra information, if you prefer
     * to look at it that way.
     *
     * Note that you might need to overload isFilled if you overload this one.
     */
    public Set<String> getFilled() {
        return unmodifiableFilled;
    }

    /**
     * Returns whether this hit has been filled with the properties
     * contained in the given summary class. Note that this method
     * will also return true if this hit is not fillable.
     */
    public boolean isFilled(String summaryClass) {
        return (filled == null) || filled.contains(summaryClass);
    }

    /** Sets the name of the source creating this hit */
    public void setSource(String source) { this.source = source; }

    /** Returns the name of the source creating this hit */
    public String getSource() { return source; }

    /**
     * Returns the fields of this as a read-only map. This is more costly than the preferred iterator(), as
     * it uses Collections.unmodifiableMap()
     *
     * @return An readonly map of the fields
     */
    // TODO Should it be deprecated ?
    public final Map<String, Object> fields() { return getUnmodifiableFieldMap(); }

    /** Allocate room for the given number of fields to avoid resizing. */
    public void reserve(int minSize) {
        getFieldMap(minSize);
    }

    /**
     * Sets the value of a field
     *
     * @return the previous value, or null if none
     */
    public Object setField(String key, Object value) {
        return getFieldMap().put(key, value);
    }

    /**
     * Returns an iterator over the fields of this
     * 
     * @return an iterator for traversing the fields of this hit
     */
    public final Iterator<Map.Entry<String,Object>> fieldIterator() { return getFieldMap().entrySet().iterator(); }

    /** Returns a field value */
    public Object getField(String value) { return fields != null ? fields.get(value) : null; }

    /** Removes all fields of this */
    public void clearFields() {
        getFieldMap().clear();
    }

    /**
     * Removes a field from this
     *
     * @return the removed value of the field, or null if none
     */
    public Object removeField(String field) {
        return getFieldMap().remove(field);
    }

    /**
     * Returns the keys of the fields of this hit as a modifiable view.
     * This follows the rules of key sets returned from maps: Key removals are reflected
     * in the map, add and addAll is not supported.
     */
    public Set<String> fieldKeys() {
        return getFieldMap().keySet();
    }

    /**
     * Changes the key under which a value is found. This is useful because it allows keys to be changed
     * without accessing the value (which may be lazily created).
     */
    public void changeFieldKey(String oldKey, String newKey) {
        Map<String,Object> fieldMap = getFieldMap();
        Object value = fieldMap.remove(oldKey);
        fieldMap.put(newKey, value);
    }

    private Map<String, Object> getFieldMap() {
        return getFieldMap(16);
    }

    private Map<String, Object> getFieldMap(int minSize) {
        if (fields == null) {
            // Compensate for loadfactor and then some, rounded up....
            fields = new LinkedHashMap<>(2*minSize);
        }
        return fields;
    }

    private Map<String, Object> getUnmodifiableFieldMap() {
        if (unmodifiableFieldMap == null) {
            if (fields == null) {
                return Collections.emptyMap();
            } else {
                unmodifiableFieldMap = Collections.unmodifiableMap(fields);
            }
        }
        return unmodifiableFieldMap;
    }

    /** Generate a HitField from a field if the field exists */
    public HitField buildHitField(String key) {
        return buildHitField(key, false);
    }

    /** Generate a HitField from a field if the field exists */
    @SuppressWarnings("deprecation")
    public HitField buildHitField(String key, boolean forceNoPreTokenize) {
        return buildHitField(key, forceNoPreTokenize, false);
    }

    // TODO: Remove third parameter on Vespa 7
    @Deprecated
    public HitField buildHitField(String key, boolean forceNoPreTokenize, boolean forceStringHandling) {
        Object o = getField(key);
        if (o == null) return null;
        if (o instanceof HitField) return (HitField)o;

        HitField h;
        if (forceNoPreTokenize) {
            if (o instanceof XMLString && !forceStringHandling) {
                h = new HitField(key, (XMLString) o, false);
            } else {
                h = new HitField(key, o.toString(), false);
            }
        } else {
            if (o instanceof XMLString && !forceStringHandling) {
                h = new HitField(key, (XMLString) o);
            } else {
                h = new HitField(key, o.toString());
            }
        }
        h.setOriginal(o);
        getFieldMap().put(key, h);
        return h;
    }

    /** Returns the types of this as a modifiable set. Modifications to this set are directly reflected in this hit */
    public Set<String> types() { return types; }

    /** @deprecated do not use */
    @Deprecated
    public String getTypeString() {
        return types().stream().collect(Collectors.joining(" "));
    }

    /**
     * Returns the add number, assigned when adding the hit to a Result.
     *
     * Used to order equal relevant hit by add order. -1 if this hit
     * has never been added to a result.
     *
     * @deprecated do not use
     */
    @Deprecated // TODO: Make package private on Vespa 7
    public int getAddNumber() { return addNumber; }

    /**
     * Sets the add number, assigned when adding the hit to a Result,
     * used to order equal relevant hit by add order.
     *
     * @deprecated do not use
     */
    @Deprecated // TODO: Make package private on Vespa 7
    public void setAddNumber(int addNumber) { this.addNumber = addNumber; }

    /**
     * Returns whether this is a concrete hit, containing content of the requested
     * kind, or a meta hit containing information on the collection of hits,
     * the query, the service and so on. This default implementation return false.
     */
    public boolean isMeta() { return meta; }

    public void setMeta(boolean meta) { this.meta=meta; }

    /**
     * Auxiliary hits are not counted towards the concrete number of hits to satisfy in the users request.
     * Any kind of meta hit is auxiliary, but hits containing concrete results can also be auxiliary,
     * for example ads in a service which does not primarily serve ads, or groups in a hierarchical organization.
     *
     * @return true if the auxiliary value is true, or if this is a meta hit
     */
    public boolean isAuxiliary() {
        return isMeta() || auxiliary;
    }

    public void setAuxiliary(boolean auxiliary) { this.auxiliary = auxiliary; }

    /** @deprecated do not use */
    @Deprecated // TODO: Remove on Vespa 7
    public int getSourceNumber() { return sourceNumber; }

    /** @deprecated do not use */
    @Deprecated // TODO: Remove on Vespa 7
    public void setSourceNumber(int number) { this.sourceNumber = number; }

    /** Returns the query which produced this hit, or null if not known */
    public Query getQuery() { return query; }

    /** Returns the query which produced this hit as a request, or null if not known */
    public Request request() { return query; }

    /** Sets the query which produced this. This is ignored (except if this is a HitGroup) if a query is already set */
    public final void setQuery(Query query) {
        if (this.query == null || this instanceof HitGroup) {
            this.query = query;
        }
    }

    /**
     * Returns a field of this hit XML escaped and without token
     * delimiters.
     *
     * @deprecated do not use
     * @return a field of this hit, or null if the property is not set
     */
    @Deprecated // TODO: Remove on Vespa 7
    public String getFieldXML(String key) {
        Object p = getField(key);

        if (p == null) {
            return null;
        } else if (p instanceof HitField) {
            return ((HitField)p).quotedContent(false);
        } else if (p instanceof StructuredData || p instanceof XMLString || p instanceof JSONString) {
            return p.toString();
        } else {
            return XML.xmlEscape(p.toString(), false, '\u001f');
        }
    }

    /**
     * @deprecated do not use
     */
    @Deprecated // TODO: Remove on Vespa 7
    public String getUnboldedField(String key, boolean escape) {
        Object p = getField(key);

        if (p == null) {
            return null;
        } else if (p instanceof HitField) {
            return ((HitField) p).bareContent(escape, false);
        } else if (p instanceof StructuredData) {
            return p.toString();
        } else if (p instanceof XMLString || p instanceof JSONString) {
            return p.toString();
        } else if (escape) {
            return XML.xmlEscape(p.toString(), false, '\u001f');
        } else {
            return stripCharacter('\u001F', p.toString());
        }
    }

    /** Attach some data to this hit for this searcher */
    public void setSearcherSpecificMetaData(Searcher searcher, Object data) {
        if (searcherSpecificMetaData == null) {
            searcherSpecificMetaData = Collections.singletonMap(searcher, data);
        } else {
            if (searcherSpecificMetaData.size() == 1) {
                Object tmp = searcherSpecificMetaData.get(searcher);
                if (tmp != null) {
                    searcherSpecificMetaData = Collections.singletonMap(searcher, data);
                } else {
                    searcherSpecificMetaData = new TreeMap<>(searcherSpecificMetaData);
                    searcherSpecificMetaData.put(searcher, data);
                }
            } else {
                searcherSpecificMetaData.put(searcher, data);
            }
        }
    }

    /** Returns data attached to this hit for this searcher, or null if none */
    public Object getSearcherSpecificMetaData(Searcher searcher) {
        return searcherSpecificMetaData != null ? searcherSpecificMetaData.get(searcher) : null;
    }

    /**
     * Internal - do not use
     *
     * @param filled the backing set
     */
    // TODO: Make package private on Vespa 7
    protected final void setFilledInternal(Set<String> filled) {
        this.filled = filled;
        unmodifiableFilled = (filled != null) ? Collections.unmodifiableSet(filled) : null;
    }

    /**
     * For internal use only.
     * Gives access to the modifiable backing set of filled summaries.
     * This set might be unmodifiable if the size is less than or equal to 1
     *
     * @return the set of filled summaries.
     */
    // TODO: Make package private on Vespa 7
    protected final Set<String> getFilledInternal() {
        return filled;
    }

    /**
     * @deprecated do not use
     */
    @Deprecated // TODO: Remove on Vespa 7
    public static String stripCharacter(char strip, String toStripFrom) {
        StringBuilder builder = null;

        int lastBadChar = 0;
        for (int i = 0; i < toStripFrom.length(); i++) {
            if (toStripFrom.charAt(i) == strip) {
                if (builder == null) {
                    builder = new StringBuilder(toStripFrom.length());
                }

                builder.append(toStripFrom, lastBadChar, i);
                lastBadChar = i + 1;
            }
        }

        if (builder == null) {
            return toStripFrom;
        } else {
            if (lastBadChar < toStripFrom.length()) {
                builder.append(toStripFrom, lastBadChar, toStripFrom.length());
            }

            return builder.toString();
        }
    }

    /** Releases the resources held by this, making it irreversibly unusable */
    protected void close() {
        query = null;
        fields = null;
        unmodifiableFieldMap = null;
    }

    /** Returns true if the argument is a hit having the same uri as this */
    @Override
    public boolean equals(Object object) {
        if ( ! (object instanceof Hit))
            return false;
        return getId().equals(((Hit) object).getId());
    }

    /** Returns the hashCode of this hit, which is the hashcode of its uri. */
    @Override
    public int hashCode() {
        if (getId() == null)
            throw new IllegalStateException("Id has not been set.");

        return getId().hashCode();
    }

    /** Compares this hit to another hit */
    @SuppressWarnings("deprecation")
    @Override
    public int compareTo(Hit other) {
        // higher relevance is before
        int result = other.getRelevance().compareTo(getRelevance());
        if (result != 0)
            return result;

        // lower addnumber is before
        result = this.getAddNumber() - other.getAddNumber();
        if (result != 0)
            return result;

        // if all else fails, compare URIs (alphabetically)
        if (this.getId() == null && other.getId() == null)
            return 0;
        else if (other.getId() == null)
            return -1;
        else if (this.getId() == null)
            return 1;
        else
            return this.getId().compareTo(other.getId());
    }

    @Override
    public Hit clone() {
        Hit hit = (Hit) super.clone();

        hit.fields = fields != null ? new LinkedHashMap<>(fields) : null;
        hit.unmodifiableFieldMap = null;
        hit.types = new LinkedHashSet<>(types);
        if (filled != null) {
            hit.setFilledInternal(new HashSet<>(filled));
        }

        return hit;
    }

    @Override
    public String toString() {
        return "hit " + getId() + " (relevance " + getRelevance() + ")";
    }

}
