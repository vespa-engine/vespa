// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.xgboost;

import com.devsmart.ubjson.UBArray;
import com.devsmart.ubjson.UBObject;
import com.devsmart.ubjson.UBReader;
import com.devsmart.ubjson.UBValue;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Utility to dump UBJ files as JSON format.
 * Outputs the raw UBJ structure without any conversion.
 * Usage: java UbjToJson <input.ubj>
 *
 * @author arnej
 */
public class UbjToJson {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java UbjToJson <input.ubj>");
            System.exit(1);
        }

        String ubjPath = args[0];
        UbjToJson converter = new UbjToJson();
        String json = converter.convertUbjToJson(ubjPath);
        System.out.println(json);
    }

    public String convertUbjToJson(String filePath) throws IOException {
        try (FileInputStream fileStream = new FileInputStream(filePath);
             UBReader reader = new UBReader(fileStream)) {
            UBValue root = reader.read();
            return toJson(root, 0);
        }
    }

    private String toJson(UBValue value, int indent) {
        if (value.isNull()) {
            return "null";
        } else if (value.isBool()) {
            return Boolean.toString(value.asBool());
        } else if (value.isNumber()) {
            if (value.isInteger()) {
                return Long.toString(value.asLong());
            } else {
                return Double.toString(value.asFloat64());
            }
        } else if (value.isString()) {
            return jsonString(value.asString());
        } else if (value.isArray()) {
            return arrayToJson(value.asArray(), indent);
        } else if (value.isObject()) {
            return objectToJson(value.asObject(), indent);
        } else {
            return "\"<unknown>\"";
        }
    }

    private String arrayToJson(UBArray array, int indent) {
        if (array.size() == 0) {
            return "[]";
        }

        // Check if this is a typed array and if all elements are numbers
        boolean isNumberArray = true;
        if (array.size() > 0) {
            for (int i = 0; i < array.size(); i++) {
                if (!array.get(i).isNumber()) {
                    isNumberArray = false;
                    break;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        String spaces = "  ".repeat(indent);
        String itemSpaces = "  ".repeat(indent + 1);

        if (isNumberArray && array.size() > 10) {
            // Compact format for large number arrays
            sb.append("[");
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(toJson(array.get(i), indent + 1));
            }
            sb.append("]");
        } else {
            // Regular format
            sb.append("[\n");
            for (int i = 0; i < array.size(); i++) {
                sb.append(itemSpaces);
                sb.append(toJson(array.get(i), indent + 1));
                if (i < array.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(spaces).append("]");
        }

        return sb.toString();
    }

    private String objectToJson(UBObject obj, int indent) {
        Set<String> keys = obj.keySet();
        if (keys.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        String spaces = "  ".repeat(indent);
        String itemSpaces = "  ".repeat(indent + 1);

        sb.append("{\n");
        int i = 0;
        for (String key : keys) {
            sb.append(itemSpaces);
            sb.append(jsonString(key));
            sb.append(": ");
            sb.append(toJson(obj.get(key), indent + 1));
            if (i < keys.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            i++;
        }
        sb.append(spaces).append("}");

        return sb.toString();
    }

    private String jsonString(String str) {
        return "\"" + str
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
