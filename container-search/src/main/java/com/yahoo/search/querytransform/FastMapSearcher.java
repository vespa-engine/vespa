// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.IndexedItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.query.MapMatchItem;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.StringRangeItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.FieldInfo;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.searchlib.document.FastMapSearch;

import java.util.regex.Pattern;

/**
 * When a field (a map, or an array of struct) has fast map search enabled, this class transforms
 * map lookups naming its lookup field, such as myMap.myLookup{"key"} = 42, to target the fast map attribute.
 * Such a lookup is a {@link MapMatchItem} on the field name followed by a dot and the lookup name.
 * <p>
 * A rewritten query no longer searches the field itself, so anything which depends on the sameElement
 * matching the original field, such as matched-elements-only summaries and match features on its
 * key and value struct fields, will not see the match.
 *
 * @author johsol
 */
@Before(QueryCanonicalizer.queryCanonicalization)
@After(PhaseNames.TRANSFORMED_QUERY)
public class FastMapSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        var schemaInfo = execution.context().schemaInfo();
        var session = schemaInfo.newSession(query);
        new Rewriter().rewriteFastMapSearch(query, session);
        return execution.search(query);
    }

    private class Rewriter {

        private boolean hasRewritten = false;

        /** Entry point for rewriting a query */
        private void rewriteFastMapSearch(Query query, SchemaInfo.Session session) {
            Item root = query.getModel().getQueryTree().getRoot();
            Item possibleNewRoot = rewriteFastMapSearchVisit(root, session);
            if (root != possibleNewRoot) {
                query.getModel().getQueryTree().setRoot(possibleNewRoot);
            }
            if (hasRewritten) {
                query.trace("rewrote map lookup to fast-map lookup", true, 2);
            }
        }

        /**
         * Rewrite map lookups for fast map search.
         */
        private Item rewriteFastMapSearchVisit(Item item, SchemaInfo.Session session) {
            if (item == null) {
                return null;
            }

            // handle map lookup rewrite
            if (item instanceof MapMatchItem mapMatchItem) {
                Item rewritten = tryRewriteMapMatch(mapMatchItem, session);
                if (rewritten != null) {
                    hasRewritten = true;
                    return rewritten;
                }
                return item;
            }

            // recursively try rewrite children.
            if (item instanceof CompositeItem composite) {
                for (int i = 0; i < composite.getItemCount(); i++) {
                    Item child = composite.getItem(i);
                    Item newChild = rewriteFastMapSearchVisit(child, session);
                    if (newChild != child) {
                        composite.setItem(i, newChild);
                    }
                }
            }

            return item;
        }

    }

    /**
     * Returns the rewrite of the given map lookup, or null if it does not name the lookup field of a field
     * with fast map search. The rewrite is a single term on the fast map attribute if the lookup can be
     * expressed as one, and otherwise the equivalent sameElement on the field itself.
     */
    private Item tryRewriteMapMatch(MapMatchItem mapMatchItem, SchemaInfo.Session session) {
        String name = mapMatchItem.getFieldName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return null;
        }
        String fieldName = name.substring(0, dot);
        String lookupName = name.substring(dot + 1);
        Field.FastMapSearchFields fastMap = fastMapSearch(fieldName, session);
        if (fastMap == null || ! fastMap.lookupName().equals(lookupName)) {
            return null;
        }
        // The key and value are read from the children rather than the keyItem and valueItem fields,
        // which are not updated when the item is cloned.
        if (mapMatchItem.getItemCount() != 2) {
            return null;
        }
        Item keyItem = mapMatchItem.getItem(0);
        Item valueItem = mapMatchItem.getItem(1);
        TermItem lookup = tryMakeFastMapItem(keyItem, valueItem, fastMap, FastMapSearch.toLookupFieldName(fieldName, lookupName));
        if (lookup != null) {
            return lookup;
        }
        return toSameElement(keyItem, valueItem, fieldName, fastMap);
    }

    /** Returns the sameElement on the given field equivalent to a map lookup of the given key and value on its lookup field. */
    private static SameElementItem toSameElement(Item keyItem, Item valueItem, String fieldName, Field.FastMapSearchFields fastMap) {
        SameElementItem sameElement = new SameElementItem(fieldName);
        sameElement.addItem(withIndex(keyItem.clone(), fastMap.keyField()));
        sameElement.addItem(withIndex(valueItem.clone(), fastMap.valueField()));
        return sameElement;
    }

    private static Item withIndex(Item item, String indexName) {
        if (item instanceof IndexedItem indexed) {
            indexed.setIndexName(indexName);
        }
        return item;
    }

    /**
     * Returns the single fast map lookup term equivalent to a map lookup of the given key and value,
     * or null if it cannot be expressed as one. A single value becomes a
     * word lookup, a range becomes a lexical range, both on the given lookup attribute.
     */
    private TermItem tryMakeFastMapItem(Item keyTerm, Item valueTerm, Field.FastMapSearchFields fastMap, String lookupFieldName) {
        if ( ! (keyTerm instanceof TermItem keyItem) || ! (valueTerm instanceof TermItem valueItem)) {
            return null;
        }

        // only support string keys for now.
        if (fastMap.keyType().kind() != Field.Type.Kind.STRING) {
            return null;
        }

        if (fastMap.valueType().kind() == Field.Type.Kind.STRING) {
            var key = getString(keyItem);
            var value = getString(valueItem);
            if (key == null || value == null) {
                return null;
            }
            return makeWord(key, value, lookupFieldName);
        }

        if (fastMap.valueType().kind() == Field.Type.Kind.INT) {
            var key = getString(keyItem);
            if (key == null) {
                return null;
            }
            var value = getInteger(valueItem);
            if (value != null) {
                return makeWord(key, value, lookupFieldName);
            }
            return makeIntRange(key, valueItem, lookupFieldName);
        }

        if (fastMap.valueType().kind() == Field.Type.Kind.LONG) {
            var key = getString(keyItem);
            if (key == null) {
                return null;
            }
            var value = getLong(valueItem);
            if (value != null) {
                return makeWord(key, value, lookupFieldName);
            }
            return makeLongRange(key, valueItem, lookupFieldName);
        }

        if (fastMap.valueType().kind() == Field.Type.Kind.FLOAT || fastMap.valueType().kind() == Field.Type.Kind.DOUBLE) {
            var key = getString(keyItem);
            if (key == null) {
                return null;
            }
            return makeFloatingPointItem(key, valueItem, lookupFieldName, fastMap.valueType().kind() == Field.Type.Kind.FLOAT);
        }

        return null;
    }

    /** Gets value as string or null. */
    private String getString(TermItem term) {
        if (term.getClass() == WordItem.class || term instanceof ExactStringItem) {
            return ((WordItem) term).getWord();
        }
        return null;
    }

    /** Gets value as integer or null. */
    private Integer getInteger(TermItem term) {
       String number = getNumberString(term);
       if (number == null) {
           return null;
       }
       try {
           return Integer.parseInt(number.trim());
       } catch (NumberFormatException e) {
           return null; // a range expression, or not an int: the caller tries the range form
       }
    }

    /** Gets value as long or null. */
    private Long getLong(TermItem term) {
        String number = getNumberString(term);
        if (number == null) {
            return null;
        }
        try {
            return Long.parseLong(number.trim());
        } catch (NumberFormatException e) {
            return null; // a range expression, or not a long: the caller tries the range form
        }
    }

    /** Returns the text of a numeric or word term, or null if the term is neither. */
    private static String getNumberString(TermItem term) {
        if (term instanceof IntItem intItem) {
            return intItem.getNumber();
        }
        if (term.getClass() == WordItem.class) {
            return ((WordItem) term).getWord();
        }
        return null;
    }

    /** Package-private for unit testing. */
    WordItem makeWord(String key, String value, String lookupFieldName) {
        return new WordItem(FastMapSearch.toKeyValueTerm(key, value),
                            lookupFieldName, false);
    }

    /**
     * Returns the lexical range over the synthetic attribute equivalent to the given numeric
     * range on the value of one map key, or null if it cannot be expressed as one.
     */
    StringRangeItem makeIntRange(String key, TermItem valueItem, String lookupFieldName) {
        if (!(valueItem instanceof IntItem intItem)) {
            return null;
        }
        Limit fromLimit = intItem.getFromLimit();
        Limit toLimit = intItem.getToLimit();
        Integer from = toIntBound(fromLimit, Integer.MIN_VALUE);
        Integer to = toIntBound(toLimit, Integer.MAX_VALUE);
        if (from == null || to == null) {
            return null;
        }
        return new StringRangeItem(FastMapSearch.toKeyValue8Term(key, from), fromLimit.isInclusive(),
                                   FastMapSearch.toKeyValue8Term(key, to), toLimit.isInclusive(),
                                   intItem.getHitLimit(),
                                   lookupFieldName, false, null);
    }

    /**
     * Returns the int to encode for the given range endpoint, using the given value when the
     * endpoint is unbounded, or null if the endpoint has no exact int form.
     */
    private static Integer toIntBound(Limit limit, int whenInfinite) {
        if (limit.isInfinite()) {
            return whenInfinite;
        }
        int asInt = limit.number().intValue();
        double asDouble = limit.number().doubleValue();
        if (asDouble != (double)asInt) {
            return null; // not an int endpoint: fall back to the regular sameElement
        }
        return asInt;
    }

    /** Package-private for unit testing. */
    WordItem makeWord(String key, Integer value, String lookupFieldName) {
        return new WordItem(FastMapSearch.toKeyValue8Term(key, value),
                            lookupFieldName, false);
    }

    /**
     * Returns the lexical range over the synthetic attribute equivalent to the given numeric
     * range on the long value of one map key, or null if it cannot be expressed as one.
     */
    StringRangeItem makeLongRange(String key, TermItem valueItem, String lookupFieldName) {
        if (!(valueItem instanceof IntItem intItem)) {
            return null;
        }
        Limit fromLimit = intItem.getFromLimit();
        Limit toLimit = intItem.getToLimit();
        Long from = toLongBound(fromLimit, Long.MIN_VALUE);
        Long to = toLongBound(toLimit, Long.MAX_VALUE);
        if (from == null || to == null) {
            return null;
        }
        return new StringRangeItem(FastMapSearch.toKeyValue16Term(key, from), fromLimit.isInclusive(),
                                   FastMapSearch.toKeyValue16Term(key, to), toLimit.isInclusive(),
                                   intItem.getHitLimit(),
                                   lookupFieldName, false, null);
    }

    /**
     * Returns the long to encode for the given range endpoint, using the given value when the
     * endpoint is unbounded, or null if the endpoint has no exact long form.
     */
    private static Long toLongBound(Limit limit, long whenInfinite) {
        if (limit.isInfinite()) {
            return whenInfinite;
        }
        Number number = limit.number();
        if (number instanceof Long || number instanceof Integer) {
            return number.longValue();
        }
        // A fractional or out-of-range endpoint has no exact long form. Every double in the long
        // range which equals its own rounding is an integer, and is cast exactly.
        double asDouble = number.doubleValue();
        if (asDouble != Math.rint(asDouble) || asDouble < -0x1p63 || asDouble >= 0x1p63) {
            return null; // fall back to the regular sameElement
        }
        return (long) asDouble;
    }

    /** Package-private for unit testing. */
    WordItem makeWord(String key, Long value, String lookupFieldName) {
        return new WordItem(FastMapSearch.toKeyValue16Term(key, value),
                            lookupFieldName, false);
    }

    /**
     * Returns the fast map lookup term equivalent to the given value term on the float or double value of
     * one map key, or null if it cannot be expressed as one.
     *
     * Each endpoint is rounded to the nearest value of the attribute type, like the backend does for a
     * float or double attribute. Since -0.0 and 0.0 are equal as numbers but not as encoded strings, a
     * zero endpoint is encoded as the zero which makes the range include or exclude both zeros.
     * A single value (other than zero) becomes a word lookup, anything else a lexical range.
     *
     * Package-private for unit testing.
     */
    TermItem makeFloatingPointItem(String key, TermItem valueItem, String lookupFieldName, boolean isFloat) {
        Limit fromLimit;
        Limit toLimit;
        int hitLimit = 0;
        if (valueItem instanceof IntItem intItem) {
            hitLimit = intItem.getHitLimit();
            fromLimit = intItem.getFromLimit();
            toLimit = intItem.getToLimit();
        } else if (valueItem.getClass() == WordItem.class) {
            Double value = parseDecimal(((WordItem) valueItem).getWord());
            if (value == null) {
                return null;
            }
            fromLimit = toLimit = new Limit(value, true);
        } else {
            return null;
        }
        // An unbounded limit is included by the backend whether the limit is inclusive or not.
        boolean fromInclusive = fromLimit.isInfinite() || fromLimit.isInclusive();
        boolean toInclusive = toLimit.isInfinite() || toLimit.isInclusive();
        double from = toFloatingPointBound(fromLimit, isFloat, true, fromInclusive);
        double to = toFloatingPointBound(toLimit, isFloat, false, toInclusive);
        if (Double.isNaN(from) || Double.isNaN(to)) {
            return null; // matches nothing in the backend: fall back to the regular sameElement
        }
        if (fromInclusive && toInclusive && Double.doubleToRawLongBits(from) == Double.doubleToRawLongBits(to)) {
            return new WordItem(toKeyValueTerm(key, from, isFloat), lookupFieldName, false);
        }
        return new StringRangeItem(toKeyValueTerm(key, from, isFloat), fromInclusive,
                                   toKeyValueTerm(key, to, isFloat), toInclusive,
                                   hitLimit,
                                   lookupFieldName, false, null);
    }

    /**
     * Returns the float or double endpoint of a range with the given limit. An unbounded limit becomes
     * the infinity in its direction. A zero endpoint becomes the zero on the outside of the range when
     * inclusive, and on the inside when exclusive, so that either both zeros are matched or neither is.
     */
    private static double toFloatingPointBound(Limit limit, boolean isFloat, boolean isLower, boolean inclusive) {
        double bound;
        if (limit.isInfinite()) {
            bound = isLower ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        } else if (isFloat) {
            bound = (float) limit.number().doubleValue(); // via double, like the backend
        } else {
            bound = limit.number().doubleValue();
        }
        if (bound == 0.0) {
            bound = (isLower == inclusive) ? -0.0 : 0.0;
        }
        return bound;
    }

    private static String toKeyValueTerm(String key, double value, boolean isFloat) {
        return isFloat ? FastMapSearch.toKeyValueFloatTerm(key, (float) value)
                       : FastMapSearch.toKeyValueDoubleTerm(key, value);
    }

    private static final Pattern decimalNumber = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    /** Returns the given word as a double if it is a plain decimal number, and null otherwise. */
    private static Double parseDecimal(String word) {
        String trimmed = word.trim();
        if ( ! decimalNumber.matcher(trimmed).matches()) {
            return null; // a range expression, or not a number: the caller falls back to the regular sameElement
        }
        return Double.parseDouble(trimmed);
    }

    /** Returns the key and value fields of the given field if it has fast map search enabled, and null otherwise. */
    private static Field.FastMapSearchFields fastMapSearch(String fieldName, SchemaInfo.Session session) {
        FieldInfo info = session.fieldInfo(fieldName).orElse(null);
        if (info instanceof Field field) {
            return field.fastMapSearch().orElse(null);
        }
        return null;
    }

}
