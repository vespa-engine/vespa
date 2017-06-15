// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.example.playground;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.*;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import java.io.IOException;
import java.util.Map;

public class ExpressionEvaluator {

    public static String evaluate(String json) throws IOException {
        ObjectMapper m = new ObjectMapper();
        JsonNode root = m.readTree(json);
        if (root == null) {
            return error("Could not parse json to evaluate");
        }

        if (!root.has("expression")) {
            return error("Required field missing: expression");
        }
        String expression = root.get("expression").asText();

        try {
            MapContext context = new MapContext();
            if (root.has("arguments")) {
                JsonNode arguments = root.get("arguments");
                if (!arguments.isArray()) {
                    return error("Invalid JSON format: arguments must be an array");
                }

                for (JsonNode argument : arguments) {
                    if (!argument.isObject()) {
                        return error("Invalid JSON format: argument must be an object");
                    }
                    if (!argument.has("name")) {
                        return error("Invalid JSON format: argument must have a name");
                    }
                    if (!argument.has("type")) {
                        return error("Invalid JSON format: argument must have a type");
                    }
                    if (!argument.has("value")) {
                        return error("Invalid JSON format: argument must have a value");
                    }

                    String name = argument.get("name").asText();
                    String type = argument.get("type").asText();
                    Value value;

                    if ("string".equalsIgnoreCase(type)) {
                        value = new StringValue(argument.get("value").asText());
                    } else if ("double".equalsIgnoreCase(type)) {
                        value = new DoubleValue(argument.get("value").asDouble());
                    } else if ("boolean".equalsIgnoreCase(type)) {
                        value = new BooleanValue(argument.get("value").asBoolean());
                    } else if (type.toLowerCase().startsWith("tensor(")) {
                        value = new TensorValue(Tensor.from(type + ":" + argument.get("value").asText()));
                    } else {
                        return error("Unknown argument type: " + type);
                    }

                    context.put(name, value);
                }
            }

            Value value = evaluate(expression, context);

            String valueType = valueType(value);
            if (valueType.equals("unknown")) {
                return error("Evaluation of ranking expression returned unknown value type");
            }

            return String.join("\n",
                    "{",
                    "   \"type\": \"" + valueType + "\",",
                    "   \"value\": " + valueValue(value),
                    "}");

        } catch (ParseException e) {
            return error("Could not parse expression: " + expression);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }


    public static Value evaluate(String expressionString, Context context) throws ParseException {
        return new RankingExpression(expressionString).evaluate(context);
    }


    private static String error(String msg) {
        return "{ \"error\": \"" + msg + "\" }";
    }

    private static String valueType(Value value) {
        if (value instanceof StringValue) {
            return "string";
        }
        if (value instanceof BooleanValue) {
            return "boolean";
        }
        if (value instanceof DoubleCompatibleValue) {
            return "double";
        }
        if (value instanceof TensorValue) {
            return ((TensorValue)value).asTensor().type().toString();
        }
        return "unknown";
    }

    private static String valueValue(Value value) {
        if (value instanceof TensorValue) {
            Tensor tensor = ((TensorValue)value).asTensor();

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"literal\": \"" + tensor.toString() + "\", ");
            sb.append("\"cells\": [");

            for (Map.Entry<TensorAddress, Double> entry : tensor.cells().entrySet()) {
                TensorAddress address = entry.getKey();
                sb.append("{\"address\":{");
                for (int i=0; i < address.size(); i++) {
                    sb.append("\"" + tensor.type().dimensions().get(i).name() + "\":\"" + address.label(i) + "\",");
                }
                if (address.size() > 0)
                    sb.deleteCharAt(sb.length()-1);
                sb.append("},\"value\":" + entry.getValue());
                sb.append("},");
            }
            if (tensor.cells().size() > 0)
                sb.deleteCharAt(sb.length()-1);
            sb.append("]}");
            return sb.toString();

        } else if (value instanceof StringValue) {
            return "\"" + value.toString() + "\"";
        }
        return value.toString();
    }

}
