// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.HasIndexItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.query.MapMatchItem;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.query.StringRangeItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.searchlib.document.FastMapSearch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * When a field (a map, or an array of struct) has fast map search enabled, this class transforms
 * map lookups naming its lookup field, such as myMap.myLookup{"key"} = 42, to target the fast map attribute.
 * Such a lookup is a {@link MapMatchItem} on the field name followed by a dot and the lookup name.
 * A lookup which cannot be expressed as a single term on the fast map attribute is rejected, as is any other
 * query on the lookup field or its key and value, and a lookup on a field which does not have the same lookup
 * field in all the schemas searched.
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

    private final boolean active;

    FastMapSearcher() {
        this.active = true;
    }

    @Inject
    public FastMapSearcher(SchemaInfo schemaInfo) {
        // check if there is any lookups in any schema in any source
        var session = schemaInfo.newSession(Set.of(), Set.of());
        this.active = ! findLookups(session).isEmpty();
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (active) {
            var session = execution.context().schemaInfo().newSession(query);
            var lookupNames = findLookups(session);
            new Rewriter(session, lookupNames).rewriteFastMapSearch(query);
        }
        return execution.search(query);
    }

    /** Returns the names of the lookup fields, as in field.lookupName, of the fields in the schemas of the given session. */
    private static Set<String> findLookups(SchemaInfo.Session session) {
        Set<String> names = new HashSet<>();
        for (Schema schema : session.schemas()) {
            for (Field field : schema.fields().values()) {
                for (var fastMap : field.fastMapSearches()) {
                    names.add(field.name() + "." + fastMap.lookupName());
                }
            }
        }
        return names;
    }

    private class Rewriter {

        private final SchemaInfo.Session session;
        private final Set<String> knownLookups;
        private boolean hasRewritten = false;

        Rewriter(SchemaInfo.Session session, Set<String> lookupNames) {
            this.session = session;
            this.knownLookups = lookupNames;
        }

        /** Entry point for rewriting a query */
        private void rewriteFastMapSearch(Query query) {
            Item root = query.getModel().getQueryTree().getRoot();
            Item possibleNewRoot = rewriteFastMapSearchVisit(root);
            if (root != possibleNewRoot) {
                query.getModel().getQueryTree().setRoot(possibleNewRoot);
            }
            if (hasRewritten) {
                query.trace("rewrote map lookup to fast-map lookup", true, 2);
            }
        }

        /**
         * Rewrite map lookups for fast map search, and reject any other use of a lookup field.
         */
        private Item rewriteFastMapSearchVisit(Item item) {
            if (item == null) {
                return null;
            }

            // handle map lookup rewrite. Its children are relative to it, and are handled by the rewrite.
            if (item instanceof MapMatchItem mapMatchItem) {
                if (knownLookups.contains(mapMatchItem.getFieldName())) {
                    Item rewritten = tryRewriteMapMatch(mapMatchItem, session);
                    if (rewritten != null) {
                        hasRewritten = true;
                        return rewritten;
                    }
                }
                rejectLookupFieldUse(mapMatchItem);
                return item;
            }

            if (item instanceof HasIndexItem indexed) {
                rejectLookupFieldUse(indexed);
            }

            // recursively try rewrite children.
            if (item instanceof CompositeItem composite) {
                for (int i = 0; i < composite.getItemCount(); i++) {
                    Item child = composite.getItem(i);
                    Item newChild = rewriteFastMapSearchVisit(child);
                    if (newChild != child) {
                        composite.setItem(i, newChild);
                    }
                }
            }

            return item;
        }

        /** A lookup field, and its key and value, can only be searched by a map lookup, which is rewritten. */
        private void rejectLookupFieldUse(HasIndexItem item) {
            String name = item.getFieldName();
            if (name == null) return;
            String lookupName = name;
            if (name.endsWith("." + YqlParser.KEY_FIELD_NAME) || name.endsWith("." + YqlParser.VALUE_FIELD_NAME)) {
                String withoutSuffix = name.substring(0, name.lastIndexOf('.'));
                if (knownLookups.contains(withoutSuffix)) {
                    lookupName = withoutSuffix;
                }
            }
            if (knownLookups.contains(lookupName)) {
                throw new IllegalInputException("'" + name + "' can only be searched by a map lookup, as in " +
                                                lookupName + "{\"key\"} = value, but got " + item);
            }
        }

    }

    /**
     * Returns the rewrite of the given map lookup, or null if it does not name the lookup field of a field
     * with fast map search. The rewrite is a single term on the fast map attribute.
     *
     * @throws IllegalInputException if the lookup cannot be expressed as a single term, or the lookup field
     *         is not the same in all the schemas searched which have the field
     */
    private Item tryRewriteMapMatch(MapMatchItem mapMatchItem, SchemaInfo.Session session) {
        String name = mapMatchItem.getFieldName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return null;
        }
        String fieldName = name.substring(0, dot);
        String lookupName = name.substring(dot + 1);
        Field.FastMapSearchFields fastMap = fastMapSearch(name, fieldName, lookupName, session);
        if (fastMap == null) {
            return null;
        }
        Item keyItem = mapMatchItem.keyItem();
        Item valueItem = mapMatchItem.valueItem();
        TermItem lookup = tryMakeFastMapItem(keyItem, valueItem, fastMap, FastMapSearch.toLookupFieldName(fieldName, lookupName));
        if (lookup == null) {
            throw new IllegalInputException("Lookup '" + name + "' requires " + describeSupportedKeys(fastMap.keyType()) +
                                            " as key, and " +
                                            describeSupportedValues(fastMap.valueType()) +
                                            " as value, but got " + mapMatchItem);
        }
        return lookup;
    }

    private static String describeSupportedKeys(Field.Type keyType) {
        return switch (keyType.kind()) {
            case INT -> "a single int";
            case LONG -> "a single long";
            default -> "a single word";
        };
    }

    private static String describeSupportedValues(Field.Type valueType) {
        return switch (valueType.kind()) {
            case INT, LONG -> "a single integer or an integer range";
            case FLOAT, DOUBLE -> "a single number or a number range";
            default -> "a single word";
        };
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

        var key = getKey(keyItem, fastMap.keyType());
        if (key == null) {
            return null;
        }

        if (fastMap.valueType().kind() == Field.Type.Kind.STRING) {
            var value = getString(valueItem);
            if (value == null) {
                return null;
            }
            return makeWord(key, value, lookupFieldName);
        }

        if (fastMap.valueType().kind() == Field.Type.Kind.INT) {
            var value = getInteger(valueItem);
            if (value != null) {
                return makeWord(key, value, lookupFieldName);
            }
            return makeIntRange(key, valueItem, lookupFieldName);
        }

        if (fastMap.valueType().kind() == Field.Type.Kind.LONG) {
            var value = getLong(valueItem);
            if (value != null) {
                return makeWord(key, value, lookupFieldName);
            }
            return makeLongRange(key, valueItem, lookupFieldName);
        }

        if (fastMap.valueType().kind() == Field.Type.Kind.FLOAT || fastMap.valueType().kind() == Field.Type.Kind.DOUBLE) {
            return makeFloatingPointItem(key, valueItem, lookupFieldName, fastMap.valueType().kind() == Field.Type.Kind.FLOAT);
        }

        return null;
    }

    /**
     * Returns the key as it is in the fast map attribute, or null if the given term is not a single key.
     * An int or long key is in decimal, as given by to_string when indexing.
     */
    private String getKey(TermItem term, Field.Type keyType) {
        return switch (keyType.kind()) {
            case STRING -> getString(term);
            case INT -> getIntegerKey(term, Integer.MIN_VALUE, Integer.MAX_VALUE);
            case LONG -> getIntegerKey(term, Long.MIN_VALUE, Long.MAX_VALUE);
            default -> throw new IllegalStateException("Fast map search with key type " + keyType + " is not supported");
        };
    }

    private static final Pattern integerKey = Pattern.compile("-?[0-9]+");

    /**
     * Returns the decimal form of the given numeric or word term, or null unless it is a plain integer
     * (ASCII digits with an optional minus sign) between min and max. Leading zeros are dropped,
     * as the key is written to the attribute without them.
     */
    private static String getIntegerKey(TermItem term, long min, long max) {
        String number = (term instanceof IntItem intItem) ? intItem.getNumber() : getString(term);
        if (number == null || ! integerKey.matcher(number).matches()) {
            return null; // not a plain integer, such as a range expression
        }
        long key;
        try {
            key = Long.parseLong(number);
        } catch (NumberFormatException e) {
            return null; // outside the long range
        }
        return (key < min || key > max) ? null : Long.toString(key);
    }

    /** Returns the word of a word or exact string term (which is a word item), or null if the term is neither. */
    private static String getString(TermItem term) {
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
            return null; // not an int endpoint
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
            return null;
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
            return null; // matches nothing in the backend
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
            return null; // a range expression, or not a number
        }
        return Double.parseDouble(trimmed);
    }

    /**
     * Returns the fast map search fields of the given field if it has the given lookup field in some schema
     * searched, and null otherwise.
     *
     * @throws IllegalInputException if some schema searched has the field, but not with the same lookup field,
     *         as the lookup would then silently miss the documents of that schema
     */
    private static Field.FastMapSearchFields fastMapSearch(String name, String fieldName, String lookupName, SchemaInfo.Session session) {
        Field.FastMapSearchFields found = null;
        List<String> withLookup = new ArrayList<>();
        List<String> withoutLookup = new ArrayList<>();
        boolean differs = false;
        for (Schema schema : session.schemas()) {
            if ( ! (schema.fieldInfo(fieldName).orElse(null) instanceof Field field)) continue;
            var fastMap = field.fastMapSearch(lookupName).orElse(null);
            if (fastMap == null) {
                withoutLookup.add(schema.name());
                continue;
            }
            withLookup.add(schema.name());
            if (found == null)
                found = fastMap;
            else if ( ! found.equals(fastMap))
                differs = true;
        }
        if (found == null) return null;
        if ( ! withoutLookup.isEmpty())
            throw new IllegalInputException("Lookup '" + name + "' is not defined for field '" + fieldName + "' in " +
                                            schemaList(withoutLookup) + ", but is in " + schemaList(withLookup) +
                                            ". Restrict the query to the schemas which have it");
        if (differs)
            throw new IllegalInputException("Lookup '" + name + "' has different key or value types in " +
                                            schemaList(withLookup) + ". Restrict the query to schemas where it is the same");
        return found;
    }

    private static String schemaList(List<String> schemas) {
        return (schemas.size() == 1 ? "schema " : "schemas ") +
               String.join(", ", schemas.stream().sorted().map(schema -> "'" + schema + "'").toList());
    }

}
