// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;

import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class BinaryFormat {

    final static String NODE_TYPE = "type";
    final static String KEY = "key";
    final static String SET = "feature_set";
    final static String RANGE_MIN = "range_min";
    final static String RANGE_MAX = "range_max";
    final static String CHILDREN = "children";
    final static String PARTITIONS = "partitions";
    final static String EDGE_PARTITIONS = "edge_partitions";
    final static String HASHED_PARTITIONS = "hashed_partitions";
    final static String HASHED_EDGE_PARTITIONS = "hashed_edge_partitions";
    final static String HASH = "hash";
    final static String PAYLOAD = "payload";
    final static String LABEL = "label";
    final static String VALUE = "value";
    final static String LOWER_BOUND = "lower_bound";
    final static String UPPER_BOUND = "upper_bound";

    final static int TYPE_CONJUNCTION = 1;
    final static int TYPE_DISJUNCTION = 2;
    final static int TYPE_NEGATION = 3;
    final static int TYPE_FEATURE_SET = 4;
    final static int TYPE_FEATURE_RANGE = 5;
    final static int TYPE_TRUE = 6;
    final static int TYPE_FALSE = 7;

    public static byte[] encode(Predicate predicate) {
        Objects.requireNonNull(predicate, "predicate");
        Slime slime = new Slime();
        encode(predicate, slime.setObject());
        return com.yahoo.slime.BinaryFormat.encode(slime);
    }

    public static Predicate decode(byte[] buf) {
        Objects.requireNonNull(buf, "buf");
        Slime slime = com.yahoo.slime.BinaryFormat.decode(buf);
        return decode(slime.get());
    }

    private static Predicate decode(Inspector in) {
        switch ((int)in.field(NODE_TYPE).asLong()) {
        case TYPE_CONJUNCTION:
            Conjunction conjunction = new Conjunction();
            in = in.field(CHILDREN);
            for (int i = 0, len = in.children(); i < len; ++i) {
                conjunction.addOperand(decode(in.entry(i)));
            }
            return conjunction;

        case TYPE_DISJUNCTION:
            Disjunction disjunction = new Disjunction();
            in = in.field(CHILDREN);
            for (int i = 0, len = in.children(); i < len; ++i) {
                disjunction.addOperand(decode(in.entry(i)));
            }
            return disjunction;

        case TYPE_NEGATION:
            return new Negation(decode(in.field(CHILDREN).entry(0)));

        case TYPE_FEATURE_RANGE:
            FeatureRange featureRange = new FeatureRange(in.field(KEY).asString());
            if (in.field(RANGE_MIN).valid()) {
                featureRange.setFromInclusive(in.field(RANGE_MIN).asLong());
            }
            if (in.field(RANGE_MAX).valid()) {
                featureRange.setToInclusive(in.field(RANGE_MAX).asLong());
            }
            Inspector p_in = in.field(PARTITIONS);
            for (int i = 0, len = p_in.children(); i < len; ++i) {
                featureRange.addPartition(new RangePartition(p_in.entry(i).asString()));
            }
            p_in = in.field(EDGE_PARTITIONS);
            for (int i = 0, len = p_in.children(); i < len; ++i) {
                Inspector obj = p_in.entry(i);
                featureRange.addPartition(new RangeEdgePartition(
                        obj.field(LABEL).asString(), obj.field(VALUE).asLong(),
                        (int)obj.field(LOWER_BOUND).asLong(), (int)obj.field(UPPER_BOUND).asLong()));
            }
            return featureRange;

        case TYPE_FEATURE_SET:
            FeatureSet featureSet = new FeatureSet(in.field(KEY).asString());
            in = in.field(SET);
            for (int i = 0, len = in.children(); i < len; ++i) {
                featureSet.addValue(in.entry(i).asString());
            }
            return featureSet;

        case TYPE_TRUE:
            return new BooleanPredicate(true);

        case TYPE_FALSE:
            return new BooleanPredicate(false);

            default:
            throw new UnsupportedOperationException(String.valueOf(in.field(NODE_TYPE).asLong()));
        }
    }

    private static void encode(Predicate predicate, Cursor out) {
        if (predicate instanceof Conjunction) {
            out.setLong(NODE_TYPE, TYPE_CONJUNCTION);
            out = out.setArray(CHILDREN);
            for (Predicate operand : ((Conjunction)predicate).getOperands()) {
                encode(operand, out.addObject());
            }
        } else if (predicate instanceof Disjunction) {
            out.setLong(NODE_TYPE, TYPE_DISJUNCTION);
            out = out.setArray(CHILDREN);
            for (Predicate operand : ((Disjunction)predicate).getOperands()) {
                encode(operand, out.addObject());
            }
        } else if (predicate instanceof FeatureRange) {
            FeatureRange range = (FeatureRange) predicate;
            out.setLong(NODE_TYPE, TYPE_FEATURE_RANGE);
            out.setString(KEY, range.getKey());
            Long from = range.getFromInclusive();
            if (from != null) {
                out.setLong(RANGE_MIN, from);
            }
            Long to = range.getToInclusive();
            if (to != null) {
                out.setLong(RANGE_MAX, to);
            }
            Cursor p_out = out.setArray(HASHED_PARTITIONS);
            for (RangePartition p : range.getPartitions()) {
                p_out.addLong(PredicateHash.hash64(p.getLabel()));
            }
            p_out = out.setArray(HASHED_EDGE_PARTITIONS);
            for (RangeEdgePartition p : range.getEdgePartitions()) {
                Cursor obj = p_out.addObject();
                obj.setLong(HASH, PredicateHash.hash64(p.getLabel()));
                obj.setLong(VALUE, p.getValue());
                obj.setLong(PAYLOAD, p.encodeBounds());
            }
        } else if (predicate instanceof FeatureSet) {
            out.setLong(NODE_TYPE, TYPE_FEATURE_SET);
            out.setString(KEY, ((FeatureSet)predicate).getKey());
            out = out.setArray(SET);
            for (String value : ((FeatureSet)predicate).getValues()) {
                out.addString(value);
            }
        } else if (predicate instanceof Negation) {
            out.setLong(NODE_TYPE, TYPE_NEGATION);
            out = out.setArray(CHILDREN);
            encode(((Negation)predicate).getOperand(), out.addObject());
        } else if (predicate instanceof BooleanPredicate) {
            out.setLong(NODE_TYPE, ((BooleanPredicate)predicate).getValue() ? TYPE_TRUE : TYPE_FALSE);
        } else {
            throw new UnsupportedOperationException(predicate.getClass().getName());
        }
    }
}
