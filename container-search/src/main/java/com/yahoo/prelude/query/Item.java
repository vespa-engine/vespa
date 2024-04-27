// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.collections.CopyOnWriteHashMap;
import com.yahoo.compress.IntegerCompressor;
import com.yahoo.language.Language;
import com.yahoo.prelude.query.textualrepresentation.Discloser;
import com.yahoo.search.query.QueryTree;
import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;
import java.util.Objects;


/**
 * An item in the tree which defines which documents will match a query.
 * Item subclasses can be composed freely to create arbitrary complex matching trees.
 * Items are in general mutable and not thread safe.
 * They can be deeply cloned by calling clone().
 * Their identity is defined by their content
 * (i.e. the field value of two items decide if they are equal).
 *
 * @author bratseth
 * @author havardpe
 */
public abstract class Item implements Cloneable {

    // These must match the types in searchlib/src/vespa/searchlib/parsequery/parse.h
    public enum ItemType {
        OR(0),
        AND(1),
        NOT(2),
        RANK(3),
        WORD(4),
        INT(5),
        PHRASE(6),
        MULTI_TERM(7),
        PREFIX(8),
        SUBSTRING(9),
        NEAR(11),
        ONEAR(12),
        SUFFIX(13),
        EQUIV(14),
        WEIGHTEDSET(15),
        WEAK_AND(16),
        EXACT(17),
        SAME_ELEMENT(18),
        PURE_WEIGHTED_STRING(19),
        PURE_WEIGHTED_INTEGER(20),
        DOTPRODUCT(21),
        WAND(22),
        PREDICATE_QUERY(23),
        REGEXP(24),
        WORD_ALTERNATIVES(25),
        NEAREST_NEIGHBOR(26),
        GEO_LOCATION_TERM(27),
        TRUE(28),
        FALSE(29),
        FUZZY(30),
        STRING_IN(31),
        NUMERIC_IN(32);

        public final int code;

        ItemType(int code) {
            this.code = code;
        }

    }

    // These must match the definitions in searchlib/src/vespa/searchlib/parsequery/item_creator.h
    @Deprecated // TODO: Remove on Vespa 9
    public enum ItemCreator {

        ORIG(0),
        FILTER(1);

        public final int code;

        ItemCreator(int code) {
            this.code = code;
        }

    }

    public static final int DEFAULT_WEIGHT = 100;

    /** The relative importance of this term in the query. Default is 100 */
    private int weight = DEFAULT_WEIGHT;

    private boolean fromSpecialToken = false;

    private ItemCreator creator = ItemCreator.ORIG;

    /** The parent in the query tree, or null if this is a root */
    private CompositeItem parent = null;

    /** The annotations made on this item */
    private CopyOnWriteHashMap<String, Object> annotations;

    /** Whether this item should affect ranking. */
    private boolean isRanked = true;

    /** Whether position data should be used when ranking this item */
    private boolean usePositionData = true;

    /** Whether the item should encode a unique ID */
    private boolean hasUniqueID = false;

    /** Optional symbolic name for this item, requires unique id */
    private String label = null;

    /** Unique identifier to address the item for external annotation */
    protected int uniqueID = 0;

    /** Items for explicit connectivity */
    // TODO: Don't use protected members, especially not for something like this
    // Move this to an object which can take care of being a weighted bidirectional reference more elegantly and safely.
    protected Item connectedItem;
    protected Item connectedBacklink;
    protected double connectivity = 0;

    /** Explicit term significance */
    protected double significance = 0;
    protected boolean explicitSignificance = false;

    /** Whether this item is eligible for change by query rewriters (false) or should be kept as-is (true) */
    private boolean isProtected;

    private Language language = Language.UNKNOWN;
    
    /** Sets the index name of this item */
    public abstract void setIndexName(String index);

    /** Returns the int code of this item */
    public final int getCode() {
        return getItemType().code;
    }

    /** Return the enumerated type of this item. */
    public abstract ItemType getItemType();

    /** Returns the name of this item */
    public abstract String getName();

    /**
     * Sets whether this is a filter term. Filter terms are treated just like other terms except that
     * they will not be highlighted in dynamic summaries.
     */
    public void setFilter(boolean filter) {
        if (filter) {
            creator = ItemCreator.FILTER;
        } else {
            creator = ItemCreator.ORIG;
        }
    }

    /** Returns whether this is a filter term */
    public boolean isFilter() {
        return creator == ItemCreator.FILTER;
    }

    /**
     * Indicates that a query item that does not normally match with prefix semantics
     * should do so for this particular query item instance.
     *
     * False by default; should be overridden by subclasses that want to signal this behavior.
     */
    protected boolean hasPrefixMatchSemantics() {
        return false;
    }

    /**
     * Returns the item creator value.
     *
     * @deprecated use isFilter(boolean)
     */
    @Deprecated // TODO: Remove on Vespa 9
    public ItemCreator getCreator() {
        return creator;
    }

    /**
     * Sets the item creator value.
     *
     * @deprecated use setFilter(boolean)
     */
    @Deprecated // TODO: Remove on Vespa 9
    public void setCreator(ItemCreator creator) {
        this.creator = creator;
    }

    /** Sets the relative importance of this term */
    public void setWeight(int w) {
        weight = w;
    }

    /** Returns the relative importance of this term. Default is 100. */
    public int getWeight() {
        return weight;
    }

    /**
     * Annotate this item
     *
     * @param key the annotation key
     * @param value the value, or null to set a valueless annotation
     */
    public void addAnnotation(String key, Object value) {
        if (annotations == null)
            annotations = new CopyOnWriteHashMap<>();
        annotations.put(key, value);
    }

    /** Returns an annotation on this item, or null if the annotation is not set */
    public Object getAnnotation(String annotation) {
        if (annotations == null) {
            return null;
        }
        return annotations.get(annotation);
    }

    /**
     * Returns whether this has an annotation
     */
    public boolean hasAnnotation(String annotation) {
        if (annotations == null) return false;
        return annotations.containsKey(annotation);
    }

    /** Set whether this should be protected from change/remove by query rewriters */
    public void setProtected(boolean isProtected) { this.isProtected=isProtected; }

    /** Returns whether this is to be protected from change/remove by query rewriters. default is false */
    public boolean isProtected() { return isProtected; }


    /** Sets the parent in the tree. Do not use: Only to be called from CompositeItem/QueryTree */
    public void setParent(CompositeItem parent) {
        this.parent = parent;
    }

    /** Returns the parent in the query tree, or null if this node has no parent */
    public CompositeItem getParent() {
        return parent;
    }

    public abstract int encode(ByteBuffer buffer);

    protected void encodeThis(ByteBuffer buffer) {
        byte CODE_MASK =     0b00011111;
        byte FEAT_WEIGHT =   0b00100000;
        byte FEAT_UNIQUEID = 0b01000000;
        byte FEAT_FLAGS =   -0b10000000;

        int code = getCode();
        byte type = code >= CODE_MASK ? CODE_MASK : (byte) code;
        if (code >= 0x80 + CODE_MASK)
            throw new IllegalStateException("must increase number of bytes in serialization format for queries");

        if (weight != DEFAULT_WEIGHT) {
            type |= FEAT_WEIGHT;
        }
        if (hasUniqueID()) {
            type |= FEAT_UNIQUEID;
        }
        byte flags = getFlagsFeature();
        if (flags != 0) {
            type |= FEAT_FLAGS;
        }

        buffer.put(type);
        if (code >= CODE_MASK) {
            // This is an extension to the serialization to work around original 5 bits limit for code
            buffer.put((byte) (code - CODE_MASK));
        }
        if ((type & FEAT_WEIGHT) != 0) {
            IntegerCompressor.putCompressedNumber(weight, buffer);
        }
        if ((type & FEAT_UNIQUEID) != 0) {
            IntegerCompressor.putCompressedPositiveNumber(uniqueID, buffer);
        }
        if ((type & FEAT_FLAGS) != 0) {
            buffer.put(flags);
        }
    }

    /**
     * Returns an integer that contains all feature flags for this item.
     *
     * @return the feature flags
     */
    // This must be kept in sync with the flags in searchlib/parsequery/parse.h.
    private byte getFlagsFeature() {
        byte FLAGS_NORANK = 0x01;
        byte FLAGS_SPECIALTOKEN = 0x02;
        byte FLAGS_NOPOSITIONDATA = 0x04;
        byte FLAGS_ISFILTER = 0x08;
        byte FLAGS_PREFIX_MATCH = 0x10;

        byte ret = 0;
        if (!isRanked()) {
            ret |= FLAGS_NORANK;
        }
        if (isFromSpecialToken()) {
            ret |= FLAGS_SPECIALTOKEN;
        }
        if (!usePositionData()) {
            ret |= FLAGS_NOPOSITIONDATA;
        }
        if (isFilter()) {
            ret |= FLAGS_ISFILTER;
        }
        if (hasPrefixMatchSemantics()) {
            ret |= FLAGS_PREFIX_MATCH;
        }
        return ret;
    }


    /** Utility method for turning a string into utf-8 bytes */
    protected static byte[] getBytes(String string) {
        return Utf8.toBytes(string);
    }
    public static void putString(String s, ByteBuffer buffer) {
        putBytes(Utf8.toBytes(s), buffer);
    }
    public static void putBytes(byte[] bytes, ByteBuffer buffer) {
        IntegerCompressor.putCompressedPositiveNumber(bytes.length, buffer);
        buffer.put(bytes);
    }

    public abstract int getTermCount();

    /**
     * Returns the canonical query language string of this item.
     * The canonical language represent an item by the string
     * <pre>
     * ([itemName] [body])
     * </pre>
     * where the body may recursively be other items.
     */
    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();

        if (shouldParenthesize()) {
            buffer.append("(");
        }
        if (isFilter()) {
            buffer.append("|");
        }
        appendHeadingString(buffer);
        appendBodyString(buffer);
        if (shouldParenthesize()) {
            buffer.append(")");
        }

        if (weight != DEFAULT_WEIGHT) {
            buffer.append("!");
            buffer.append(weight);
        }

        return buffer.toString();
    }

    /**
     * Returns whether this item should be parenthesized when printed.
     * Default is false - no parentheses
     */
    protected boolean shouldParenthesize() {
        return false;
    }

    /** Appends the heading of this string. As default getName() followed by a space. */
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        buffer.append(" ");
    }

    /**
     * Override to append the item body in the canonical query language of this item.
     * An item is usually represented by the string
     * <pre>
     * ([itemName] [body])
     * </pre>
     * The body must be appended appended by this method.
     */
    protected abstract void appendBodyString(StringBuilder buffer);

    /** Returns a deep copy of this item */
    @Override
    public Item clone() {
        try {
            Item clone = (Item)super.clone();
            if (this.annotations != null)
                clone.annotations = this.annotations.clone();
            // note: connectedItem and connectedBacklink references are corrected in CompositeItem.clone()
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Someone made Item uncloneable");
        }
    }

    /** Returns whether this item is of the same class and contains the same state as the given item. */
    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (o.getClass() != this.getClass()) return false;
        Item other = (Item)o;
        if (this.weight != other.weight) return false;
        if (this.fromSpecialToken != other.fromSpecialToken) return false;
        if (this.creator != other.creator) return false;
        if ( ! Objects.equals(this.annotations, other.annotations)) return false;
        if (this.isRanked != other.isRanked) return false;
        if (this.usePositionData != other.usePositionData) return false;
        if ( ! Objects.equals(this.label, other.label)) return false;
        if (this.uniqueID != other.uniqueID) return false;
        if ( ! Objects.equals(this.connectedItem, other.connectedItem)) return false;
        if (this.connectivity != other.connectivity) return false;
        if (this.significance != other.significance) return false;
        if (this.language != other.language) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(weight, fromSpecialToken, creator, annotations, isRanked, usePositionData, label,
                            uniqueID, connectedItem, connectivity, significance, language);
    }

    protected boolean hasUniqueID() {
        return hasUniqueID;
    }

    protected void setHasUniqueID(boolean hasUniqueID) {
        this.hasUniqueID = hasUniqueID;
    }

    /**
     * Label this item with a symbolic name which can later be used by
     * the back-end to identify specific items for ranking purposes.
     *
     * @param label label for this item
     */
    public void setLabel(String label) {
        setHasUniqueID(true);
        this.label = label;
    }

    /** Returns the label for this item. This method will return null if no label has been set. */
    public String getLabel() {
        return label;
    }

    /**
     * Sets whether this term item should affect ranking.
     * If set to false this term is not exposed to the ranking framework in the search backend.
     */
    public void setRanked(boolean isRanked) {
        this.isRanked = isRanked;
    }

    /** Returns whether this item should affect ranking. */
    public boolean isRanked() {
        return isRanked;
    }

    /**
     * Sets whether position data should be used when ranking this term item.
     * If set to false the search backend uses fast bit vector data structures when matching on this term
     * and only a few simple ranking features will be available when ranking this term.
     * Note that setting this to false also saves a lot of CPU during matching as bit vector data structures are used.
     */
    public void setPositionData(boolean usePositionData) {
        this.usePositionData = usePositionData;
    }

    /** Returns whether position data should be used when ranking this item */
    public boolean usePositionData() {
        return usePositionData;
    }

    public void disclose(Discloser discloser) {
        if (connectivity != 0)
            discloser.addProperty("connectivity", connectivity);
        if (connectedItem != null)
            discloser.addProperty("connectedItem", connectedItem); // reference
        if (creator != ItemCreator.ORIG)
            discloser.addProperty("creator", creator);
        if ( ! isRanked)
            discloser.addProperty("isRanked", isRanked);
        if ( ! usePositionData)
            discloser.addProperty("usePositionData", usePositionData);
        if (explicitSignificance)
            discloser.addProperty("significance", significance);
        if (weight != 100)
            discloser.addProperty("weight", weight);
        if (label != null)
            discloser.addProperty("label", label);
        if (hasUniqueID)
            discloser.addProperty("uniqueID", uniqueID);
    }

    public boolean isFromSpecialToken() {
        return fromSpecialToken;
    }

    public void setFromSpecialToken(boolean fromSpecialToken) {
        this.fromSpecialToken = fromSpecialToken;
    }

    /** Returns the language of any natural language text below this item, or Language.UNKNOWN if not set. */
    public Language getLanguage() { return language; }
    
    /** 
     * Sets the language of any natural language text below this item. 
     * This cannot be set to null but can be set to Language.UNKNOWN 
     */
    public void setLanguage(Language language) {
        Objects.requireNonNull(language, "Language cannot be null");
        this.language = language;
    }
    
    /**
     * DO NOT USE
     */
    public boolean hasConnectivityBackLink() {
        return connectedBacklink != null;
    }

    /** Returns true if this is the root item - that is if the parent is the QueryTree (or null for legacy reasons)*/
    public boolean isRoot() {
        if (getParent()==null) return true;
        if (getParent() instanceof QueryTree) return true;
        return false;
    }

}
