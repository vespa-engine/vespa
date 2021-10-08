// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.util.Utils;
import org.apache.pig.parser.ParserException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class VespaQuerySchema implements Iterable<VespaQuerySchema.AliasTypePair> {

    private final List<AliasTypePair> tupleSchema = new ArrayList<>();

    public VespaQuerySchema(String schema) {
        for (String e : schema.split(",")) {
            String[] pair = e.split(":");
            String alias = pair[0].trim();
            String type = pair[1].trim();
            tupleSchema.add(new AliasTypePair(alias, type));
        }
    }

    public Tuple buildTuple(int rank, JsonNode hit) {
        Tuple tuple = TupleFactory.getInstance().newTuple();

        for (VespaQuerySchema.AliasTypePair tupleElement : tupleSchema) {
            String alias = tupleElement.getAlias();
            Byte type = DataType.findTypeByName(tupleElement.getType());

            // reserved word
            if ("rank".equals(alias)) {
                tuple.append(rank);
            } else {
                JsonNode field = hit;
                String[] path = alias.split("/"); // move outside
                for (String p : path) {
                    field = field.get(p);
                    if (field == null) {
                        type = DataType.NULL; // effectively skip field as it is not found
                        break;
                    }
                }
                switch (type) {
                    case DataType.BOOLEAN:
                        tuple.append(field.asBoolean());
                        break;
                    case DataType.INTEGER:
                        tuple.append(field.asInt());
                        break;
                    case DataType.LONG:
                        tuple.append(field.asLong());
                        break;
                    case DataType.FLOAT:
                    case DataType.DOUBLE:
                        tuple.append(field.asDouble());
                        break;
                    case DataType.DATETIME:
                        tuple.append(field.asText());
                        break;
                    case DataType.CHARARRAY:
                        tuple.append(field.asText());
                        break;
                    default:
                        // the rest of the data types are currently not supported
                }
            }
        }
        return tuple;
    }

    public static Schema getPigSchema(String schemaString) {
        Schema schema = null;
        schemaString = schemaString.replace("/", "_");
        schemaString = "{(" + schemaString + ")}";
        try {
            schema = Utils.getSchemaFromString(schemaString);
        } catch (ParserException e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public Iterator<AliasTypePair> iterator() {
        return tupleSchema.iterator();
    }


    public static class AliasTypePair {
        private final String alias;
        private final String type;

        AliasTypePair(String alias, String type) {
            this.alias = alias;
            this.type = type;
        }

        public String getAlias() {
            return alias;
        }

        public String getType() {
            return type;
        }

    }

}
