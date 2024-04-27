// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import static com.yahoo.search.yql.YqlParser.ACCENT_DROP;
import static com.yahoo.search.yql.YqlParser.ALTERNATIVES;
import static com.yahoo.search.yql.YqlParser.AND_SEGMENTING;
import static com.yahoo.search.yql.YqlParser.BOUNDS;
import static com.yahoo.search.yql.YqlParser.BOUNDS_LEFT_OPEN;
import static com.yahoo.search.yql.YqlParser.BOUNDS_OPEN;
import static com.yahoo.search.yql.YqlParser.BOUNDS_RIGHT_OPEN;
import static com.yahoo.search.yql.YqlParser.CONNECTION_ID;
import static com.yahoo.search.yql.YqlParser.CONNECTION_WEIGHT;
import static com.yahoo.search.yql.YqlParser.CONNECTIVITY;
import static com.yahoo.search.yql.YqlParser.DISTANCE;
import static com.yahoo.search.yql.YqlParser.DOT_PRODUCT;
import static com.yahoo.search.yql.YqlParser.END_ANCHOR;
import static com.yahoo.search.yql.YqlParser.EQUIV;
import static com.yahoo.search.yql.YqlParser.FILTER;
import static com.yahoo.search.yql.YqlParser.FUZZY;
import static com.yahoo.search.yql.YqlParser.GEO_LOCATION;
import static com.yahoo.search.yql.YqlParser.HIT_LIMIT;
import static com.yahoo.search.yql.YqlParser.IMPLICIT_TRANSFORMS;
import static com.yahoo.search.yql.YqlParser.LABEL;
import static com.yahoo.search.yql.YqlParser.MAX_EDIT_DISTANCE;
import static com.yahoo.search.yql.YqlParser.NEAR;
import static com.yahoo.search.yql.YqlParser.NEAREST_NEIGHBOR;
import static com.yahoo.search.yql.YqlParser.NORMALIZE_CASE;
import static com.yahoo.search.yql.YqlParser.ONEAR;
import static com.yahoo.search.yql.YqlParser.ORIGIN;
import static com.yahoo.search.yql.YqlParser.ORIGIN_LENGTH;
import static com.yahoo.search.yql.YqlParser.ORIGIN_OFFSET;
import static com.yahoo.search.yql.YqlParser.ORIGIN_ORIGINAL;
import static com.yahoo.search.yql.YqlParser.PHRASE;
import static com.yahoo.search.yql.YqlParser.PREFIX;
import static com.yahoo.search.yql.YqlParser.PREFIX_LENGTH;
import static com.yahoo.search.yql.YqlParser.RANGE;
import static com.yahoo.search.yql.YqlParser.RANK;
import static com.yahoo.search.yql.YqlParser.RANKED;
import static com.yahoo.search.yql.YqlParser.SAME_ELEMENT;
import static com.yahoo.search.yql.YqlParser.SCORE_THRESHOLD;
import static com.yahoo.search.yql.YqlParser.SIGNIFICANCE;
import static com.yahoo.search.yql.YqlParser.START_ANCHOR;
import static com.yahoo.search.yql.YqlParser.STEM;
import static com.yahoo.search.yql.YqlParser.SUBSTRING;
import static com.yahoo.search.yql.YqlParser.SUFFIX;
import static com.yahoo.search.yql.YqlParser.TARGET_NUM_HITS;
import static com.yahoo.search.yql.YqlParser.THRESHOLD_BOOST_FACTOR;
import static com.yahoo.search.yql.YqlParser.UNIQUE_ID;
import static com.yahoo.search.yql.YqlParser.URI;
import static com.yahoo.search.yql.YqlParser.USE_POSITION_DATA;
import static com.yahoo.search.yql.YqlParser.WAND;
import static com.yahoo.search.yql.YqlParser.WEAK_AND;
import static com.yahoo.search.yql.YqlParser.WEIGHT;
import static com.yahoo.search.yql.YqlParser.WEIGHTED_SET;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableMap;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.AndSegmentItem;
import com.yahoo.prelude.query.BoolItem;
import com.yahoo.prelude.query.DotProductItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.FalseItem;
import com.yahoo.prelude.query.FuzzyItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.IndexedItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.GeoLocationItem;
import com.yahoo.prelude.query.MarkerWordItem;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NearestNeighborItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.NumericInItem;
import com.yahoo.prelude.query.ONearItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.PredicateQueryItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.RangeItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.RegExpItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.SegmentingRule;
import com.yahoo.prelude.query.StringInItem;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.prelude.query.TaggableItem;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.prelude.query.ToolBox.QueryVisitor;
import com.yahoo.prelude.query.TrueItem;
import com.yahoo.prelude.query.UriItem;
import com.yahoo.prelude.query.WandItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.query.QueryTree;

/**
 * Serialize Vespa query trees to YQL+ strings.
 *
 * @author Steinar Knutsen
 */
public class VespaSerializer {

    // TODO: Refactor, too much copy/paste
    private static abstract class Serializer<ITEM extends Item> {

        abstract void onExit(StringBuilder destination, ITEM item);

        String separator(Deque<SerializerWrapper> state) {
            throw new UnsupportedOperationException("Having several items for this query operator serializer, "
                                                    + this.getClass().getSimpleName() + ", not yet implemented.");
        }

        abstract boolean serialize(StringBuilder destination, ITEM item);

    }

    private static class AndSegmentSerializer extends Serializer<AndSegmentItem> {

        private static void serializeWords(StringBuilder destination, AndSegmentItem segment) {
            for (int i = 0; i < segment.getItemCount(); ++i) {
                if (i > 0) {
                    destination.append(", ");
                }
                Item current = segment.getItem(i);
                if (current instanceof WordItem) {
                    destination.append('"');
                    escape(((WordItem) current).getIndexedString(), destination).append('"');
                } else {
                    throw new IllegalArgumentException("Serializing of " + current.getClass().getSimpleName()
                                                       + " in segment AND expressions not implemented, please report this as a bug.");
                }
            }
        }

        @Override
        void onExit(StringBuilder destination, AndSegmentItem item) { }

        @Override
        boolean serialize(StringBuilder destination, AndSegmentItem item) {
            return serialize(destination, item, true);
        }

        static boolean serialize(StringBuilder destination, AndSegmentItem item, boolean includeField) {
            Substring origin = item.getOrigin();
            String image;
            int offset;
            int length;

            if (origin == null) {
                image = item.getRawWord();
                offset = 0;
                length = image.length();
            } else {
                image = origin.getSuperstring();
                offset = origin.start;
                length = origin.end - origin.start;
            }

            if (includeField) {
                destination.append(normalizeIndexName(item.getIndexName())).append(" contains ");
            }
            destination.append("({");
            serializeOrigin(destination, image, offset, length);
            destination.append(", ").append(AND_SEGMENTING).append(": true");
            destination.append("}");
            destination.append(PHRASE).append('(');
            serializeWords(destination, item);
            destination.append("))");
            return false;
        }
    }

    private static class AndSerializer extends Serializer<AndItem> {

        @Override
        void onExit(StringBuilder destination, AndItem item) {
            destination.append(')');
        }

        @Override
        String separator(Deque<SerializerWrapper> state) {
            return " AND ";
        }

        @Override
        boolean serialize(StringBuilder destination, AndItem item) {
            destination.append("(");
            return true;
        }
    }

    private static class DotProductSerializer extends Serializer<WeightedSetItem> {

        @Override
        void onExit(StringBuilder destination, WeightedSetItem item) { }

        @Override
        boolean serialize(StringBuilder destination, WeightedSetItem item) {
            serializeWeightedSetContents(destination, DOT_PRODUCT, item);
            return false;
        }

    }

    private static class EquivSerializer extends Serializer<EquivItem> {

        @Override
        void onExit(StringBuilder destination, EquivItem item) { }

        @Override
        boolean serialize(StringBuilder destination, EquivItem item) {
            String annotations = leafAnnotations(item);
            destination.append(getIndexName(item.getItem(0))).append(" contains ");
            if (annotations.length() > 0) {
                destination.append("({").append(annotations).append("}");
            }
            destination.append(EQUIV).append('(');
            int initLen = destination.length();
            for (Iterator<Item> i = item.getItemIterator(); i.hasNext();) {
                Item x = i.next();
                if (destination.length() > initLen) {
                    destination.append(", ");
                }
                if (x instanceof PhraseItem) {
                    PhraseSerializer.serialize(destination, (PhraseItem)x, false);
                } else {
                    destination.append('"');
                    escape(((IndexedItem) x).getIndexedString(), destination);
                    destination.append('"');
                }
            }
            if (annotations.length() > 0) {
                destination.append(')');
            }
            destination.append(')');
            return false;
        }

    }

    private static class NearSerializer extends Serializer<NearItem> {

        @Override
        void onExit(StringBuilder destination, NearItem item) { }

        @Override
        boolean serialize(StringBuilder destination, NearItem item) {
            String annotations = nearAnnotations(item);

            destination.append(getIndexName(item.getItem(0))).append(" contains ");
            if (annotations.length() > 0) {
                destination.append('(').append(annotations);
            }
            destination.append(NEAR).append('(');
            int initLen = destination.length();
            for (ListIterator<Item> i = item.getItemIterator(); i.hasNext();) {
                WordItem close = (WordItem) i.next();
                if (destination.length() > initLen) {
                    destination.append(", ");
                }
                destination.append('"');
                escape(close.getIndexedString(), destination).append('"');
            }
            destination.append(')');
            if (annotations.length() > 0) {
                destination.append(')');
            }
            return false;
        }

        static String nearAnnotations(NearItem n) {
            if (n.getDistance() != NearItem.defaultDistance) {
                return "{" + DISTANCE + ": " + n.getDistance() + "}";
            } else {
                return "";
            }
        }

    }

    private static class UriSerializer extends Serializer<UriItem> {

        @Override
        void onExit(StringBuilder destination, UriItem item) { }

        @Override
        boolean serialize(StringBuilder destination, UriItem uriItem) {
            String annotations = uriAnnotations(uriItem);

            destination.append(uriItem.getIndexName()).append(" contains ");
            if (annotations.length() > 0)
                destination.append('(').append(annotations);
            destination.append(URI).append("(\"");
            destination.append(uriItem.getArgumentString());
            destination.append("\")");
            if (annotations.length() > 0)
                destination.append(')');
            return false;
        }

        static String uriAnnotations(UriItem item) {
            if (item.hasStartAnchor() == item.isStartAnchorDefault() &&
                item.hasEndAnchor() == item.isEndAnchorDefault())
                return "";

            StringBuilder b = new StringBuilder();
            b.append("{");
            if (item.hasStartAnchor() != item.isStartAnchorDefault()) {
                b.append(START_ANCHOR + ": " + item.hasStartAnchor());
            }
            if (item.hasEndAnchor() != item.isEndAnchorDefault()) {
                if (b.length() > 2)
                    b.append(", ");
                b.append(END_ANCHOR + ": " + item.hasEndAnchor());
            }
            b.append("}");
            return b.toString();
        }

    }

    private static class NotSerializer extends Serializer<NotItem> {

        @Override
        void onExit(StringBuilder destination, NotItem item) {
            destination.append(')');
        }

        @Override
        String separator(Deque<SerializerWrapper> state) {
            if (state.peekFirst().subItems == 1) {
                return ") AND !(";
            } else {
                return " OR ";
            }
        }

        @Override
        boolean serialize(StringBuilder destination, NotItem item) {
            destination.append("(");
            return true;
        }
    }

    private static class NullSerializer extends Serializer<NullItem> {

        @Override
        void onExit(StringBuilder destination, NullItem item) { }

        @Override
        boolean serialize(StringBuilder destination, NullItem item) {
            throw new NullItemException("NullItem encountered in query tree. This is usually a symptom of an invalid " +
                                        "query or an error in a query transformer.");
        }

    }

    private static class NumberSerializer extends Serializer<IntItem> {

        @Override
        void onExit(StringBuilder destination, IntItem item) { }

        @Override
        boolean serialize(StringBuilder destination, IntItem intItem) {
            if (intItem.getFromLimit().number().equals(intItem.getToLimit().number())) {
                destination.append(normalizeIndexName(intItem.getIndexName())).append(" = ");
                annotatedNumberImage(intItem, intItem.getFromLimit().number().toString(), destination);
            } else if (intItem.getFromLimit().isInfinite()) {
                destination.append(normalizeIndexName(intItem.getIndexName()));
                destination.append(intItem.getToLimit().isInclusive() ? " <= " : " < ");
                annotatedNumberImage(intItem, intItem.getToLimit().number().toString(), destination);
            } else if (intItem.getToLimit().isInfinite()) {
                destination.append(normalizeIndexName(intItem.getIndexName()));
                destination.append(intItem.getFromLimit().isInclusive() ? " >= " : " > ");
                annotatedNumberImage(intItem, intItem.getFromLimit().number().toString(), destination);
            } else {
                serializeAsRange(destination, intItem);
            }
            return false;
        }

        private void serializeAsRange(StringBuilder destination, IntItem intItem) {
            String annotations = leafAnnotations(intItem);
            boolean leftOpen = !intItem.getFromLimit().isInclusive();
            boolean rightOpen = !intItem.getToLimit().isInclusive();
            String boundsAnnotation = "";
            int initLen;

            if (leftOpen && rightOpen) {
                boundsAnnotation = BOUNDS + ": " + "\"" + BOUNDS_OPEN + "\"";
            } else if (leftOpen) {
                boundsAnnotation = BOUNDS + ": " + "\"" + BOUNDS_LEFT_OPEN + "\"";
            } else if (rightOpen) {
                boundsAnnotation = BOUNDS + ": " + "\"" + BOUNDS_RIGHT_OPEN + "\"";
            }
            if (annotations.length() > 0 || boundsAnnotation.length() > 0) {
                destination.append("({");
            }
            initLen = destination.length();
            if (annotations.length() > 0) {
                destination.append(annotations);
            }
            comma(destination, initLen);
            if (boundsAnnotation.length() > 0) {
                destination.append(boundsAnnotation);
            }
            if (initLen != annotations.length()) {
                destination.append("}");
            }
            destination.append(RANGE).append('(')
                    .append(normalizeIndexName(intItem.getIndexName()))
                    .append(", ").append(intItem.getFromLimit().number())
                    .append(", ").append(intItem.getToLimit().number())
                    .append(")");
            if (annotations.length() > 0 || boundsAnnotation.length() > 0) {
                destination.append(")");
            }
        }

        private void annotatedNumberImage(IntItem item, String rawNumber, StringBuilder image) {
            String annotations = leafAnnotations(item);

            if (annotations.length() > 0) {
                image.append("({").append(annotations).append("}");
            }
            if ('-' == rawNumber.charAt(0)) {
                image.append('(');
            }
            image.append(rawNumber);
            appendLongIfNecessary(rawNumber, image);
            if ('-' == rawNumber.charAt(0)) {
                image.append(')');
            }
            if (annotations.length() > 0) {
                image.append(')');
            }
        }

        private void appendLongIfNecessary(String rawNumber, StringBuilder image) {
            // floating point
            if (rawNumber.indexOf('.') >= 0) {
                return;
            }
            try {
                long l = Long.parseLong(rawNumber);
                if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                    image.append('L');
                }
            } catch (NumberFormatException e) {
                // somebody has managed to init an IntItem containing noise, just give up
            }
        }
    }

    private static class BoolSerializer extends Serializer<BoolItem> {

        @Override
        void onExit(StringBuilder destination, BoolItem item) { }

        @Override
        boolean serialize(StringBuilder destination, BoolItem item) {
            destination.append(normalizeIndexName(item.getIndexName())).append(" = ");
            destination.append(item.stringValue());
            return false;
        }

    }

    private static class TrueSerializer extends Serializer<TrueItem> {
        @Override
        void onExit(StringBuilder destination, TrueItem item) { }
        @Override
        boolean serialize(StringBuilder destination, TrueItem item) {
            destination.append("true");
            return false;
        }
    }

    private static class FalseSerializer extends Serializer<FalseItem> {
        @Override
        void onExit(StringBuilder destination, FalseItem item) { }
        @Override
        boolean serialize(StringBuilder destination, FalseItem item) {
            destination.append("false");
            return false;
        }
    }

    private static class RegExpSerializer extends Serializer<RegExpItem> {

        @Override
        void onExit(StringBuilder destination, RegExpItem item) { }

        @Override
        boolean serialize(StringBuilder destination, RegExpItem regexp) {
            String annotations = leafAnnotations(regexp);
            destination.append(normalizeIndexName(regexp.getIndexName())).append(" matches ");
            annotatedTerm(destination, regexp, annotations);
            return false;
        }
    }

    private static class FuzzySerializer extends Serializer<FuzzyItem> {

        @Override
        void onExit(StringBuilder destination, FuzzyItem item) { }

        @Override
        boolean serialize(StringBuilder destination, FuzzyItem fuzzy) {
            String annotations = fuzzyAnnotations(fuzzy);

            destination.append(normalizeIndexName(fuzzy.getIndexName())).append(" contains ");

            if (annotations.length() > 0) {
                destination.append('(').append(annotations);
            }

            destination.append(FUZZY).append('(');
            destination.append('"');
            escape(fuzzy.getIndexedString(), destination).append('"');
            destination.append(')');

            if (annotations.length() > 0) {
                destination.append(')');
            }
            return false;
        }

        static String fuzzyAnnotations(FuzzyItem fuzzyItem) {
            boolean isMaxEditDistanceSet = fuzzyItem.getMaxEditDistance() != FuzzyItem.DEFAULT_MAX_EDIT_DISTANCE;
            boolean isPrefixLengthSet = fuzzyItem.getPrefixLength() != FuzzyItem.DEFAULT_PREFIX_LENGTH;
            boolean isPrefixMatch = fuzzyItem.isPrefixMatch();
            boolean anyAnnotationSet = isMaxEditDistanceSet || isPrefixLengthSet || isPrefixMatch;

            if (!anyAnnotationSet) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            builder.append("{");
            if (isMaxEditDistanceSet) {
                builder.append(MAX_EDIT_DISTANCE + ":").append(fuzzyItem.getMaxEditDistance());
                if (isPrefixLengthSet || isPrefixMatch) {
                    builder.append(",");
                }
            }
            if (isPrefixLengthSet) {
                builder.append(PREFIX_LENGTH + ":").append(fuzzyItem.getPrefixLength());
                if (isPrefixMatch) {
                    builder.append(",");
                }
            }
            if (isPrefixMatch) {
                builder.append(PREFIX).append(':').append(fuzzyItem.isPrefixMatch());
            }
            builder.append("}");
            return builder.toString();
        }
    }

    private static class ONearSerializer extends Serializer<ONearItem> {

        @Override
        void onExit(StringBuilder destination, ONearItem item) {
        }

        @Override
        boolean serialize(StringBuilder destination, ONearItem item) {
            String annotations = NearSerializer.nearAnnotations(item);

            destination.append(getIndexName(item.getItem(0))).append(" contains ");
            if (annotations.length() > 0) {
                destination.append('(').append(annotations);
            }
            destination.append(ONEAR).append('(');
            int initLen = destination.length();
            for (ListIterator<Item> i = item.getItemIterator(); i.hasNext();) {
                WordItem close = (WordItem) i.next();
                if (destination.length() > initLen) {
                    destination.append(", ");
                }
                destination.append('"');
                escape(close.getIndexedString(), destination).append('"');
            }
            destination.append(')');
            if (annotations.length() > 0) {
                destination.append(')');
            }
            return false;
        }

    }

    private static class OrSerializer extends Serializer<OrItem> {

        @Override
        void onExit(StringBuilder destination, OrItem item) {
            destination.append(')');
        }

        @Override
        String separator(Deque<SerializerWrapper> state) {
            return " OR ";
        }

        @Override
        boolean serialize(StringBuilder destination, OrItem item) {
            destination.append("(");
            return true;
        }
    }

    private static class PhraseSegmentSerializer extends Serializer<PhraseSegmentItem> {

        private static void serializeWords(StringBuilder destination, PhraseSegmentItem segment) {
            for (int i = 0; i < segment.getItemCount(); ++i) {
                if (i > 0) {
                    destination.append(", ");
                }
                Item current = segment.getItem(i);
                if (current instanceof WordItem) {
                    destination.append('"');
                    escape(((WordItem) current).getIndexedString(), destination).append('"');
                } else {
                    throw new IllegalArgumentException("Serializing of " + current.getClass().getSimpleName()
                                                       + " in phrases not implemented, please report this as a bug.");
                }
            }
        }

        @Override
        void onExit(StringBuilder destination, PhraseSegmentItem item) { }

        @Override
        boolean serialize(StringBuilder destination, PhraseSegmentItem item) {
            return serialize(destination, item, true);
        }

        static boolean serialize(StringBuilder destination, Item item, boolean includeField) {
            PhraseSegmentItem phrase = (PhraseSegmentItem) item;
            Substring origin = phrase.getOrigin();
            String image;
            int offset;
            int length;

            if (includeField) {
                destination.append(normalizeIndexName(phrase.getIndexName())).append(" contains ");
            }
            if (origin == null) {
                image = phrase.getRawWord();
                offset = 0;
                length = image.length();
            } else {
                image = origin.getSuperstring();
                offset = origin.start;
                length = origin.end - origin.start;
            }

            destination.append("({");
            serializeOrigin(destination, image, offset, length);
            String annotations = leafAnnotations(phrase);
            if (annotations.length() > 0) {
                destination.append(", ").append(annotations);
            }
            if (phrase.getSegmentingRule() == SegmentingRule.BOOLEAN_AND) {
                destination.append(", ").append('"').append(AND_SEGMENTING).append("\": true");
            }
            destination.append("}");
            destination.append(PHRASE).append('(');
            serializeWords(destination, phrase);
            destination.append("))");
            return false;
        }
    }

    private static class PhraseSerializer extends Serializer<PhraseItem> {

        @Override
        void onExit(StringBuilder destination, PhraseItem item) { }

        @Override
        boolean serialize(StringBuilder destination, PhraseItem item) {
            return serialize(destination, item, true);
        }

        static boolean serialize(StringBuilder destination, PhraseItem phrase, boolean includeField) {
            String annotations = leafAnnotations(phrase);

            if (includeField)
                destination.append(normalizeIndexName(phrase.getIndexName())).append(" contains ");
            if (annotations.length() > 0)
                destination.append("({").append(annotations).append("}");

            destination.append(PHRASE).append('(');
            for (int i = 0; i < phrase.getItemCount(); ++i) {
                if (i > 0)
                    destination.append(", ");
                Item current = phrase.getItem(i);
                if (current instanceof WordItem) {
                    WordSerializer.serializeWordWithoutIndex(destination, current);
                } else if (current instanceof PhraseSegmentItem) {
                    PhraseSegmentSerializer.serialize(destination, current, false);
                } else if (current instanceof WordAlternativesItem) {
                    WordAlternativesSerializer.serialize(destination, (WordAlternativesItem) current, false);
                } else {
                    throw new IllegalArgumentException("Serializing of " + current.getClass().getSimpleName() +
                                                       " in phrases not implemented, please report this as a bug.");
                }
            }
            destination.append(')');
            if (annotations.length() > 0)
                destination.append(')');
            return false;
        }

    }

    private static class SameElementSerializer extends Serializer<SameElementItem> {

        @Override
        void onExit(StringBuilder destination, SameElementItem item) { }

        @Override
        boolean serialize(StringBuilder destination, SameElementItem item) {
            return serialize(destination, item, true);
        }

        static boolean serialize(StringBuilder destination, SameElementItem item, boolean includeField) {
            if (includeField) {
                destination.append(normalizeIndexName(item.getFieldName())).append(" contains ");
            }

            destination.append(SAME_ELEMENT).append('(');
            for (int i = 0; i < item.getItemCount(); ++i) {
                if (i > 0) {
                    destination.append(", ");
                }
                Item current = item.getItem(i);
                if (current instanceof WordItem) {
                    new WordSerializer().serialize(destination, (WordItem)current);
                } else if (current instanceof IntItem) {
                    new NumberSerializer().serialize(destination, (IntItem)current);
                } else {
                    throw new IllegalArgumentException("Serializing of " + current.getClass().getSimpleName() +
                                                       " in same_element is not implemented, please report this as a bug.");
                }
            }
            destination.append(')');

            return false;
        }

    }

    private static class GeoLocationSerializer extends Serializer<GeoLocationItem> {
        @Override
        void onExit(StringBuilder destination, GeoLocationItem item) { }
        @Override
        boolean serialize(StringBuilder destination, GeoLocationItem item) {
            String annotations = leafAnnotations(item);
            if (annotations.length() > 0) {
                destination.append("({").append(annotations).append("}");
            }
            destination.append(GEO_LOCATION).append('(');
            destination.append(item.getIndexName()).append(", ");
            var loc = item.getLocation();
            destination.append(loc.degNS()).append(", ");
            destination.append(loc.degEW()).append(", ");
            destination.append('"').append(loc.degRadius()).append(" deg").append('"');
            destination.append(')');
            return false;
        }
    }

    private static class NearestNeighborSerializer extends Serializer<NearestNeighborItem> {

        @Override
        void onExit(StringBuilder destination, NearestNeighborItem item) { }

        @Override
        boolean serialize(StringBuilder destination, NearestNeighborItem item) {
            destination.append("{");
            int initLen = destination.length();
            destination.append(leafAnnotations(item));
            comma(destination, initLen);
            int targetNumHits = item.getTargetNumHits();
            annotationKey(destination, YqlParser.TARGET_NUM_HITS).append(targetNumHits);
            double distanceThreshold = item.getDistanceThreshold();
            if (distanceThreshold < Double.POSITIVE_INFINITY) {
                comma(destination, initLen);
                String key = YqlParser.DISTANCE_THRESHOLD;
                annotationKey(destination, key).append(distanceThreshold);
            }
            int explore = item.getHnswExploreAdditionalHits();
            if (explore != 0) {
                comma(destination, initLen);
                String key = YqlParser.HNSW_EXPLORE_ADDITIONAL_HITS;
                annotationKey(destination, key).append(explore);
            }
            boolean allow_approx = item.getAllowApproximate();
            if (! allow_approx) {
                comma(destination, initLen);
                annotationKey(destination, "approximate").append(allow_approx);
            }
            destination.append("}");
            destination.append(NEAREST_NEIGHBOR).append('(');
            destination.append(item.getIndexName()).append(", ");
            destination.append(item.getQueryTensorName()).append(')');
            return false;
        }

    }

    private static class PredicateQuerySerializer extends Serializer<PredicateQueryItem> {

        @Override
        void onExit(StringBuilder destination, PredicateQueryItem item) { }

        @Override
        boolean serialize(StringBuilder destination, PredicateQueryItem item) {
            destination.append("predicate(").append(item.getIndexName()).append(',');
            appendFeatures(destination, item.getFeatures());
            destination.append(',');
            appendFeatures(destination, item.getRangeFeatures());
            destination.append(')');
            return false;
        }

        private void appendFeatures(StringBuilder destination, Collection<? extends PredicateQueryItem.EntryBase> features) {
            if (features.isEmpty()) {
                destination.append('0'); // Workaround for empty maps.
                return;
            }
            destination.append('{');
            boolean first = true;
            for (PredicateQueryItem.EntryBase entry : features) {
                if (!first) {
                    destination.append(',');
                }
                if (entry.getSubQueryBitmap() != PredicateQueryItem.ALL_SUB_QUERIES) {
                    destination.append("\"0x").append(Long.toHexString(entry.getSubQueryBitmap()));
                    destination.append("\":{");
                    appendKeyValue(destination, entry);
                    destination.append('}');
                } else {
                    appendKeyValue(destination, entry);
                }
                first = false;
            }
            destination.append('}');
        }

        private void appendKeyValue(StringBuilder destination, PredicateQueryItem.EntryBase entry) {
            destination.append('"');
            escape(entry.getKey(), destination);
            destination.append("\":");
            if (entry instanceof PredicateQueryItem.Entry) {
                destination.append('"');
                escape(((PredicateQueryItem.Entry) entry).getValue(), destination);
                destination.append('"');
            } else {
                destination.append(((PredicateQueryItem.RangeEntry) entry).getValue());
                destination.append('L');
            }
        }

    }

    private static class RangeSerializer extends Serializer<RangeItem> {

        @Override
        void onExit(StringBuilder destination, RangeItem item) { }

        @Override
        boolean serialize(StringBuilder destination, RangeItem range) {
            String annotations = leafAnnotations(range);
            if (annotations.length() > 0) {
                destination.append("{").append(annotations).append("}");
            }
            destination.append(RANGE).append('(')
                    .append(normalizeIndexName(range.getIndexName()))
                    .append(", ");
            appendNumberImage(destination, range.getFrom()); // TODO: Serialize
                                                             // inclusive/exclusive
            destination.append(", ");
            appendNumberImage(destination, range.getTo());
            destination.append(')');
            return false;
        }

        private void appendNumberImage(StringBuilder destination, Number number) {
            destination.append(number.toString());
            if (number instanceof Long) {
                destination.append('L');
            }
        }
    }

    private static class RankSerializer extends Serializer<RankItem> {

        @Override
        void onExit(StringBuilder destination, RankItem item) {
            destination.append(')');
        }

        @Override
        String separator(Deque<SerializerWrapper> state) {
            return ", ";
        }

        @Override
        boolean serialize(StringBuilder destination, RankItem item) {
            destination.append(RANK).append('(');
            return true;

        }

    }

    private static class WordAlternativesSerializer extends Serializer<WordAlternativesItem> {

        @Override
        void onExit(StringBuilder destination, WordAlternativesItem item) { }

        @Override
        boolean serialize(StringBuilder destination, WordAlternativesItem item) {
            return serialize(destination, item, true);
        }

        static boolean serialize(StringBuilder destination, WordAlternativesItem alternatives, boolean includeField) {
            String annotations = leafAnnotations(alternatives);
            Substring origin = alternatives.getOrigin();
            boolean isFromQuery = alternatives.isFromQuery();
            boolean needsAnnotations = annotations.length() > 0 || origin != null || !isFromQuery;

            if (includeField) {
                destination.append(normalizeIndexName(alternatives.getIndexName())).append(" contains ");
            }

            if (needsAnnotations) {
                destination.append("({");
                int initLen = destination.length();

                if (origin != null) {
                    String image = origin.getSuperstring();
                    int offset = origin.start;
                    int length = origin.end - origin.start;
                    serializeOrigin(destination, image, offset, length);
                }
                if (!isFromQuery) {
                    comma(destination, initLen);
                    destination.append(IMPLICIT_TRANSFORMS).append(": false");
                }
                if (annotations.length() > 0) {
                    comma(destination, initLen);
                    destination.append(annotations);
                }

                destination.append("}");
            }

            destination.append(ALTERNATIVES).append("({");
            int initLen = destination.length();
            List<WordAlternativesItem.Alternative> sortedAlternatives = new ArrayList<>(alternatives.getAlternatives());
            // ensure most precise forms first
            Collections.sort(sortedAlternatives, (x, y) -> Double.compare(y.exactness, x.exactness));
            for (WordAlternativesItem.Alternative alternative : sortedAlternatives) {
                comma(destination, initLen);
                destination.append('"');
                escape(alternative.word, destination);
                destination.append("\": ").append(alternative.exactness);
            }
            destination.append("})");
            if (needsAnnotations) {
                destination.append(')');
            }
            return false;
        }
    }

    private static class WandSerializer extends Serializer<WandItem> {

        @Override
        void onExit(StringBuilder destination, WandItem item) {
        }

        @Override
        boolean serialize(StringBuilder destination, WandItem item) {
            serializeWeightedSetContents(destination, WAND, item, specificAnnotations(item));
            return false;
        }

        private String specificAnnotations(WandItem w) {
            StringBuilder annotations = new StringBuilder();
            int targetNumHits = w.getTargetNumHits();
            double scoreThreshold = w.getScoreThreshold();
            double thresholdBoostFactor = w.getThresholdBoostFactor();
            if (targetNumHits != 10) {
                annotations.append(TARGET_NUM_HITS).append(": ").append(targetNumHits);
            }
            if (scoreThreshold != 0) {
                comma(annotations, 0);
                annotations.append(SCORE_THRESHOLD).append(": ").append(scoreThreshold);
            }
            if (thresholdBoostFactor != 1) {
                comma(annotations, 0);
                annotations.append(THRESHOLD_BOOST_FACTOR).append(": ").append(thresholdBoostFactor);
            }
            return annotations.toString();
        }

    }

    private static class WeakAndSerializer extends Serializer<WeakAndItem> {

        @Override
        void onExit(StringBuilder destination, WeakAndItem item) {
            destination.append(')');
            if (needsAnnotationBlock(item)) {
                destination.append(')');
            }
        }

        @Override
        String separator(Deque<SerializerWrapper> state) {
            return ", ";
        }

        private boolean needsAnnotationBlock(WeakAndItem item) {
            return nonDefaultTargetNumHits(item);
        }

        @Override
        boolean serialize(StringBuilder destination, WeakAndItem item) {
            if (needsAnnotationBlock(item)) {
                destination.append("({");
            }
            if (nonDefaultTargetNumHits(item)) {
                destination.append(TARGET_NUM_HITS).append(": ").append(item.getN());
            }
            if (needsAnnotationBlock(item)) {
                destination.append("}");
            }
            destination.append(WEAK_AND).append('(');
            return true;
        }

        private boolean nonDefaultTargetNumHits(WeakAndItem w) {
            return w.getN() != WeakAndItem.defaultN;
        }
    }

    private static class WeightedSetSerializer extends Serializer<WeightedSetItem> {

        @Override
        void onExit(StringBuilder destination, WeightedSetItem item) {
        }

        @Override
        boolean serialize(StringBuilder destination, WeightedSetItem item) {
            serializeWeightedSetContents(destination, WEIGHTED_SET, item);
            return false;
        }

    }

    private static class StringInSerializer extends Serializer<StringInItem> {
        @Override
        void onExit(StringBuilder destination, StringInItem item) {

        }

        @Override
        boolean serialize(StringBuilder destination, StringInItem item) {
            destination.append(item.getIndexName()).append(" in (");
            int initLen = destination.length();
            List<String> tokens = new ArrayList<>(item.getTokens());
            Collections.sort(tokens);
            for (var token : tokens) {
                comma(destination, initLen);
                destination.append('"');
                escape(token, destination);
                destination.append("\"");
            }
            destination.append(")");
            return false;
        }
    }

    private static class NumericInSerializer extends Serializer<NumericInItem> {
        @Override
        void onExit(StringBuilder destination, NumericInItem item) {
        }

        @Override
        boolean serialize(StringBuilder destination, NumericInItem item) {
            destination.append(item.getIndexName()).append(" in (");
            int initLen = destination.length();
            List<Long> tokens = new ArrayList<>(item.getTokens());
            Collections.sort(tokens);
            for (var token : tokens) {
                comma(destination, initLen);
                destination.append(token.toString());
                if (token < Integer.MIN_VALUE || token > Integer.MAX_VALUE)
                    destination.append("L");
            }
            destination.append(")");
            return false;
        }
    }

    private static class WordSerializer extends Serializer<WordItem> {

        @Override
        void onExit(StringBuilder destination, WordItem item) {
        }

        @Override
        boolean serialize(StringBuilder destination, WordItem item) {
            StringBuilder wordAnnotations = getAllAnnotations(item);

            destination.append(normalizeIndexName(item.getIndexName())).append(" contains ");
            VespaSerializer.annotatedTerm(destination, item, wordAnnotations.toString());
            return false;
        }

        static void serializeWordWithoutIndex(StringBuilder destination, Item item) {
            WordItem w = (WordItem) item;
            StringBuilder wordAnnotations = getAllAnnotations(w);

            VespaSerializer.annotatedTerm(destination, w, wordAnnotations.toString());
        }

        private static StringBuilder getAllAnnotations(WordItem w) {
            StringBuilder wordAnnotations = new StringBuilder(WordSerializer.wordAnnotations(w));
            String leafAnnotations = leafAnnotations(w);

            if (leafAnnotations.length() > 0) {
                comma(wordAnnotations, 0);
                wordAnnotations.append(leafAnnotations(w));
            }
            return wordAnnotations;
        }

        private static String wordAnnotations(WordItem item) {
            Substring origin = item.getOrigin();
            boolean usePositionData = item.usePositionData();
            boolean stemmed = item.isStemmed();
            boolean lowercased = item.isLowercased();
            boolean accentDrop = item.isNormalizable();
            SegmentingRule andSegmenting = item.getSegmentingRule();
            boolean isFromQuery = item.isFromQuery();
            StringBuilder annotation = new StringBuilder();
            boolean prefix = item instanceof PrefixItem;
            boolean suffix = item instanceof SuffixItem;
            boolean substring = item instanceof SubstringItem;
            int initLen = annotation.length();
            String image;
            int offset;
            int length;

            if (origin == null) {
                image = item.getRawWord();
                offset = 0;
                length = image.length();
            } else {
                image = origin.getSuperstring();
                offset = origin.start;
                length = origin.end - origin.start;
            }

            if (!image.substring(offset, offset + length).equals(item.getIndexedString())) {
                VespaSerializer.serializeOrigin(annotation, image, offset, length);
            }
            if ( ! usePositionData) {
                VespaSerializer.comma(annotation, initLen);
                annotation.append(USE_POSITION_DATA).append(": false");
            }
            if (stemmed) {
                VespaSerializer.comma(annotation, initLen);
                annotation.append(STEM).append(": false");
            }
            if (lowercased) {
                VespaSerializer.comma(annotation, initLen);
                annotation.append(NORMALIZE_CASE).append(": false");
            }
            if ( ! accentDrop) {
                VespaSerializer.comma(annotation, initLen);
                annotation.append(ACCENT_DROP).append(": false");
            }
            if (andSegmenting == SegmentingRule.BOOLEAN_AND) {
                VespaSerializer.comma(annotation, initLen);
                annotation.append(AND_SEGMENTING).append(": true");
            }
            if (!isFromQuery) {
                VespaSerializer.comma(annotation, initLen);
                annotation.append(IMPLICIT_TRANSFORMS).append(": false");
            }
            if (prefix) {
                VespaSerializer.comma(annotation, initLen);
                annotation.append(PREFIX).append(": true");
            }
            if (suffix) {
                VespaSerializer.comma(annotation, initLen);
                annotation.append(SUFFIX).append(": true");
            }
            if (substring) {
                VespaSerializer.comma(annotation, initLen);
                annotation.append(SUBSTRING).append(": true");
            }
            return annotation.toString();
        }

    }

    private static final class SerializerWrapper {
        int subItems;
        final Serializer type;
        final Item item;

        SerializerWrapper(Serializer type, Item item) {
            subItems = 0;
            this.type = type;
            this.item = item;
        }

    }

    private static final class TokenComparator implements Comparator<Entry<Object, Integer>> {

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public int compare(Entry<Object, Integer> o1, Entry<Object, Integer> o2) {
            Comparable c1 = (Comparable) o1.getKey();
            Comparable c2 = (Comparable) o2.getKey();
            return c1.compareTo(c2);
        }
    }

    private static class VespaVisitor extends QueryVisitor {

        final StringBuilder destination;
        final Deque<SerializerWrapper> state = new ArrayDeque<>();

        VespaVisitor(StringBuilder destination) {
            this.destination = destination;
        }

        @Override
        public void onExit() {
            SerializerWrapper w = state.removeFirst();
            w.type.onExit(destination, w.item);
            w = state.peekFirst();
            if (w != null) {
                w.subItems += 1;
            }
        }

        @Override
        public boolean visit(Item item) {
            Serializer doIt = dispatch.get(item.getClass());

            if (doIt == null) {
                throw new IllegalArgumentException(item.getClass() + " not supported for YQL marshalling.");
            }

            if (state.peekFirst() != null && state.peekFirst().subItems > 0) {
                destination.append(state.peekFirst().type.separator(state));
            }
            state.addFirst(new SerializerWrapper(doIt, item));
            return doIt.serialize(destination, item);

        }
    }

    private static final char[] DIGITS =
            new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    private static final Map<Class<?>, Serializer> dispatch;

    private static final Comparator<? super Entry<Object, Integer>> tokenComparator = new TokenComparator();

    static {
        Map<Class<?>, Serializer> dispatchBuilder = new HashMap<>();
        dispatchBuilder.put(AndItem.class, new AndSerializer());
        dispatchBuilder.put(AndSegmentItem.class, new AndSegmentSerializer());
        dispatchBuilder.put(DotProductItem.class, new DotProductSerializer());
        dispatchBuilder.put(EquivItem.class, new EquivSerializer());
        dispatchBuilder.put(ExactStringItem.class, new WordSerializer());
        dispatchBuilder.put(IntItem.class, new NumberSerializer());
        dispatchBuilder.put(GeoLocationItem.class, new GeoLocationSerializer());
        dispatchBuilder.put(BoolItem.class, new BoolSerializer());
        dispatchBuilder.put(TrueItem.class, new TrueSerializer());
        dispatchBuilder.put(FalseItem.class, new FalseSerializer());
        dispatchBuilder.put(MarkerWordItem.class, new WordSerializer()); // gotcha
        dispatchBuilder.put(NearItem.class, new NearSerializer());
        dispatchBuilder.put(NearestNeighborItem.class, new NearestNeighborSerializer());
        dispatchBuilder.put(NotItem.class, new NotSerializer());
        dispatchBuilder.put(NullItem.class, new NullSerializer());
        dispatchBuilder.put(ONearItem.class, new ONearSerializer());
        dispatchBuilder.put(OrItem.class, new OrSerializer());
        dispatchBuilder.put(PhraseItem.class, new PhraseSerializer());
        dispatchBuilder.put(SameElementItem.class, new SameElementSerializer());
        dispatchBuilder.put(PhraseSegmentItem.class, new PhraseSegmentSerializer());
        dispatchBuilder.put(PredicateQueryItem.class, new PredicateQuerySerializer());
        dispatchBuilder.put(PrefixItem.class, new WordSerializer()); // gotcha
        dispatchBuilder.put(WordAlternativesItem.class, new WordAlternativesSerializer());
        dispatchBuilder.put(RangeItem.class, new RangeSerializer());
        dispatchBuilder.put(RankItem.class, new RankSerializer());
        dispatchBuilder.put(SubstringItem.class, new WordSerializer()); // gotcha
        dispatchBuilder.put(SuffixItem.class, new WordSerializer()); // gotcha
        dispatchBuilder.put(WandItem.class, new WandSerializer());
        dispatchBuilder.put(WeakAndItem.class, new WeakAndSerializer());
        dispatchBuilder.put(WeightedSetItem.class, new WeightedSetSerializer());
        dispatchBuilder.put(WordItem.class, new WordSerializer());
        dispatchBuilder.put(RegExpItem.class, new RegExpSerializer());
        dispatchBuilder.put(UriItem.class, new UriSerializer());
        dispatchBuilder.put(FuzzyItem.class, new FuzzySerializer());
        dispatchBuilder.put(StringInItem.class, new StringInSerializer());
        dispatchBuilder.put(NumericInItem.class, new NumericInSerializer());
        dispatch = ImmutableMap.copyOf(dispatchBuilder);
    }

    /**
     * Do YQL+ escaping, which is basically the same as for JSON, of the
     * incoming string to the "quoted" buffer. The buffer returned is the same
     * as the one given in the "quoted" parameter.
     *
     * @param in a string to escape
     * @param escaped the target buffer for escaped data
     * @return the same buffer as given in the "quoted" parameter
     */
    private static StringBuilder escape(String in, StringBuilder escaped) {
        for (char c : in.toCharArray()) {
            switch (c) {
            case ('\b'):
                escaped.append("\\b");
                break;
            case ('\t'):
                escaped.append("\\t");
                break;
            case ('\n'):
                escaped.append("\\n");
                break;
            case ('\f'):
                escaped.append("\\f");
                break;
            case ('\r'):
                escaped.append("\\r");
                break;
            case ('"'):
                escaped.append("\\\"");
                break;
            case ('\''):
                escaped.append("\\'");
                break;
            case ('\\'):
                escaped.append("\\\\");
                break;
            case ('/'):
                escaped.append("\\/");
                break;
            default:
                if (c < 32 || c >= 127) {
                    escaped.append("\\u").append(fourDigitHexString(c));
                } else {
                    escaped.append(c);
                }
            }
        }
        return escaped;
    }

    private static char[] fourDigitHexString(char c) {
        char[] hex = new char[4];
        int in = ((c) & 0xFFFF);
        for (int i = 3; i >= 0; --i) {
            hex[i] = DIGITS[in & 0xF];
            in >>>= 4;
        }
        return hex;
    }

    static String getIndexName(Item item) {
        if (!(item instanceof IndexedItem))
            throw new IllegalArgumentException("Expected IndexedItem, got " + item.getClass());
        return normalizeIndexName(((IndexedItem) item).getIndexName());
    }

    public static String serialize(Query query) {
        return serialize(query, "");
    }

    public static String serialize(Query query, String insertBeforeGrouping) {
        StringBuilder out = new StringBuilder();
        serialize(query.getModel().getQueryTree().getRoot(), out);
        out.append(insertBeforeGrouping);
        for (GroupingRequest request : query.getSelect().getGrouping()) {
            out.append(" | ");
            serialize(request, out);
        }
        return out.toString();
    }

    private static void serialize(GroupingRequest request, StringBuilder out) {
        Iterator<Continuation> it = request.continuations().iterator();
        if (it.hasNext()) {
            out.append("{ continuations:[");
            while (it.hasNext()) {
                out.append('\'').append(it.next()).append('\'');
                if (it.hasNext()) {
                    out.append(", ");
                }
            }
            out.append("] }");
        }
        out.append(request.getRootOperation());
    }

    private static void serialize(Item item, StringBuilder out) {
        VespaVisitor visitor = new VespaVisitor(out);
        ToolBox.visit(visitor, item);
    }

    static String serialize(QueryTree item) {
        StringBuilder out = new StringBuilder();
        serialize(item.getRoot(), out);
        return out.toString();
    }

    static String serialize(Item item) {
        StringBuilder out = new StringBuilder();
        serialize(item, out);
        return out.toString();
    }

    private static void serializeWeightedSetContents(StringBuilder destination, String opName,
                                                     WeightedSetItem weightedSet) {
        serializeWeightedSetContents(destination, opName, weightedSet, "");
    }

    private static void serializeWeightedSetContents(StringBuilder destination, String opName,
                                                     WeightedSetItem weightedSet, String optionalAnnotations) {
        boolean addedAnnotations = addAnnotations(destination, weightedSet, optionalAnnotations);
        destination.append(opName).append('(')
                   .append(normalizeIndexName(weightedSet.getIndexName()))
                   .append(", {");
        int initLen = destination.length();
        List<Entry<Object, Integer>> tokens = new ArrayList<>(weightedSet.getNumTokens());
        for (Iterator<Entry<Object, Integer>> i = weightedSet.getTokens(); i.hasNext();) {
            tokens.add(i.next());
        }
        Collections.sort(tokens, tokenComparator);
        for (Entry<Object, Integer> entry : tokens) {
            comma(destination, initLen);
            destination.append('"');
            escape(entry.getKey().toString(), destination);
            destination.append("\": ").append(entry.getValue().toString());
        }
        destination.append("})");
        if (addedAnnotations)
            destination.append(")");
    }

    /** Adds annotations and returns whether any were added */
    private static boolean addAnnotations(StringBuilder destination, WeightedSetItem weightedSet,
                                          String optionalAnnotations) {
        int preAnnotationValueLen;
        int incomingLen = destination.length();
        String annotations = leafAnnotations(weightedSet);

        if (optionalAnnotations.length() > 0 || annotations.length() > 0) {
            destination.append("({");
        }
        preAnnotationValueLen = destination.length();
        if (annotations.length() > 0) {
            destination.append(annotations);
        }
        if (optionalAnnotations.length() > 0) {
            comma(destination, preAnnotationValueLen);
            destination.append(optionalAnnotations);
        }
        if (destination.length() > incomingLen) {
            destination.append("}");
            return true;
        }
        else {
            return false;
        }
    }

    private static StringBuilder annotationKey(StringBuilder annotation, String val) {
        annotation.append(val).append(": ");
        return annotation;
    }

    private static void comma(StringBuilder annotation, int initLen) {
        if (annotation.length() > initLen) {
            annotation.append(", ");
        }
    }

    private static String leafAnnotations(TaggableItem item) {
        // TODO: There is no usable API for the general annotations map in the Item instances
        StringBuilder annotation = new StringBuilder();
        int initLen = annotation.length();
        {
            int uniqueId = item.getUniqueID();
            double connectivity = item.getConnectivity();
            TaggableItem connectedTo = (TaggableItem) item.getConnectedItem();
            double significance = item.getSignificance();
            if (connectedTo != null && connectedTo.getUniqueID() != 0) {
                annotation.append(CONNECTIVITY).append(": {")
                        .append(CONNECTION_ID).append(": ")
                        .append(connectedTo.getUniqueID()).append(", ")
                        .append(CONNECTION_WEIGHT).append(": ")
                        .append(connectivity).append("}");
            }
            if (item.hasExplicitSignificance()) {
                comma(annotation, initLen);
                annotation.append(SIGNIFICANCE).append(": ").append(significance);
            }
            if (uniqueId != 0) {
                comma(annotation, initLen);
                annotation.append(UNIQUE_ID).append(": ").append(uniqueId);
            }
        }
        {
            Item leaf = (Item) item;
            boolean filter = leaf.isFilter();
            boolean isRanked = leaf.isRanked();
            String label = leaf.getLabel();
            int weight = leaf.getWeight();

            if (filter) {
                comma(annotation, initLen);
                annotation.append(FILTER).append(": true");
            }
            if ( ! isRanked) {
                comma(annotation, initLen);
                annotation.append(RANKED).append(": false");
            }
            if (label != null) {
                comma(annotation, initLen);
                annotation.append(LABEL).append(": \"");
                escape(label, annotation);
                annotation.append("\"");
            }
            if (weight != 100) {
                comma(annotation, initLen);
                annotation.append(WEIGHT).append(": ").append(weight);
            }
        }
        if (item instanceof IntItem) {
            int hitLimit = ((IntItem) item).getHitLimit();
            if (hitLimit != 0) {
                comma(annotation, initLen);
                annotation.append(HIT_LIMIT).append(": ").append(hitLimit);
            }
        }
        return annotation.toString();
    }

    private static void serializeOrigin(StringBuilder destination, String image, int offset, int length) {
        destination.append(ORIGIN).append(": {").append(ORIGIN_ORIGINAL).append(": \"");
        escape(image, destination);
        destination.append("\", ").append(ORIGIN_OFFSET).append(": ")
                .append(offset).append(", ").append(ORIGIN_LENGTH)
                .append(": ").append(length).append("}");
    }

    private static String normalizeIndexName(String indexName) {
        if (indexName.length() == 0) {
            return "default";
        } else {
            return indexName;
        }
    }

    private static void annotatedTerm(StringBuilder destination, IndexedItem w, String annotations) {
        if (annotations.length() > 0) {
            destination.append("({").append(annotations).append("}");
        }
        destination.append('"');
        escape(w.getIndexedString(), destination).append('"');
        if (annotations.length() > 0) {
            destination.append(')');
        }
    }

}
