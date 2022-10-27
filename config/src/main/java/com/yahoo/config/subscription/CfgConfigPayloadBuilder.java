// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.collections.Pair;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.StringNode;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadBuilder;

import java.util.ArrayList;
import java.util.List;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;

/**
 * Deserializes config payload (cfg format) to a ConfigPayload.
 *
 * @author hmusum
 */
public class CfgConfigPayloadBuilder {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(CfgConfigPayloadBuilder.class.getName());

    /**
     * Deserializes a config payload to slime
     *
     * @param lines a list with config payload strings
     * @return an instance of the config class
     */
    public ConfigPayload deserialize(List<String> lines) {
        return ConfigPayload.fromBuilder(deserializeToBuilder(lines));
    }

    @SuppressWarnings("WeakerAccess")
    public ConfigPayloadBuilder deserializeToBuilder(List<String> lines) {
        int lineNum = 1;
        ConfigPayloadBuilder payloadBuilder = new ConfigPayloadBuilder();
        for (String line : lines) {
            parseLine(line, lineNum, payloadBuilder);
            lineNum++;
        }
        log.log(FINEST, () -> "payload=" + payloadBuilder);
        return payloadBuilder;
    }

    private void parseLine(final String line, int lineNum, ConfigPayloadBuilder payloadBuilder) {
        String trimmedLine = line.trim();
        if (trimmedLine.startsWith("#")) return;
        Pair<String, String> fieldAndValue = parseFieldAndValue(trimmedLine);
        String field = fieldAndValue.getFirst();
        String value = fieldAndValue.getSecond();
        if (field==null || value==null) {
            log.log(FINE, () -> "Got field without value in line " + lineNum + ": " + line + ", skipping");
            return;
        }
        field = field.trim();
        value = value.trim();
        validateField(field, trimmedLine, lineNum);
        validateValue(value, trimmedLine, lineNum);
        List<String> fields = parseFieldList(field);
        ConfigPayloadBuilder currentBuilder = payloadBuilder;
        for (int fieldNum = 0; fieldNum < fields.size(); fieldNum++) {
            String fieldName = fields.get(fieldNum);
            boolean isLeaf = (fieldNum == fields.size() - 1);
            if (isLeaf) {
                if (isArray(fieldName)) {
                    // array leaf
                    ConfigPayloadBuilder.Array array = currentBuilder.getArray(getArrayName(fieldName));
                    array.set(getArrayIndex(fieldName), removeQuotes(value));
                } else if (isMap(fieldName)) {
                    // map leaf
                    ConfigPayloadBuilder.MapBuilder map = currentBuilder.getMap(getMapName(fieldName));
                    map.put(getMapKey(fieldName), removeQuotes(value));
                } else {
                    // scalar leaf value
                    currentBuilder.setField(fieldName, removeQuotes(value));
                }
            } else {
                if (isArray(fieldName)) {
                    // array of structs
                    ConfigPayloadBuilder.Array array = currentBuilder.getArray(getArrayName(fieldName));
                    currentBuilder = array.get(getArrayIndex(fieldName));
                } else if (isMap(fieldName)) {
                    // map of structs
                    ConfigPayloadBuilder.MapBuilder map = currentBuilder.getMap(getMapName(fieldName));
                    currentBuilder = map.get(getMapKey(fieldName));
                } else {
                    // struct
                    currentBuilder = currentBuilder.getObject(fieldName);
                }
            }
        }
    }

    // split on space, but not if inside { } (map key)
    Pair<String, String> parseFieldAndValue(String line) {
        String field=null;
        String value;
        StringBuilder sb = new StringBuilder();
        boolean inMapKey = false;
        for (char c : line.toCharArray()) {
            if (c=='{') inMapKey=true;
            if (c=='}') inMapKey=false;
            if (c==' ' && !inMapKey) {
                if (field==null) {
                    field = sb.toString();
                    sb = new StringBuilder();
                    continue;
                }
            }
            sb.append(c);
        }
        value = sb.toString();
        return new Pair<>(field, value);
    }

    // split on dot, but not if inside { } (map key)
    List<String> parseFieldList(String field) {
        List<String> ret = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inMapKey = false;
        for (char c : field.toCharArray()) {
            if (c=='{') inMapKey=true;
            if (c=='}') inMapKey=false;
            if (c=='.' && !inMapKey) {
                ret.add(sb.toString());
                sb = new StringBuilder();
                continue;
            }
            sb.append(c);
        }
        ret.add(sb.toString());
        return ret;
    }

    // TODO Need more validation
    private void validateField(String field, String line, int lineNum) {
        if (field.length() == 0) {
            throw new ConfigurationRuntimeException("Error on line " + lineNum + ": " + line + "\n" +
                                                    "'" + field + "' is not a valid field name");
        }
    }

    // TODO Need more validation
    private void validateValue(String value, String line, int lineNum) {
        if (value.length() == 0) {
            throw new ConfigurationRuntimeException("Error on line " + lineNum + ": " + line + "\n" +
                                                    "'" + value + "' is not a valid value");
        }
    }

    private boolean isArray(String name) {
        return name.endsWith("]") && !name.startsWith("[");
    }

    private boolean isMap(String name) {
        return name.contains("{");
    }

    private String removeQuotes(String s) {
        return StringNode.unescapeQuotedString(s);
    }

    private String getMapName(String name) {
        if (name.contains("{")) {
            return name.substring(0, name.indexOf("{"));
        } else {
            return name;
        }
    }

    private String getMapKey(String name) {
        if (name.contains("{")) {
            return removeQuotes(name.substring(name.indexOf("{") + 1, name.indexOf("}")));
        } else {
            return "";
        }
    }

    private String getArrayName(String name) {
        if (name.contains("[")) {
            return name.substring(0, name.indexOf("["));
        } else {
            return name;
        }
    }

    private int getArrayIndex(String name) {
        if (name.contains("[")) {
            return Integer.parseInt(name.substring(name.indexOf("[") + 1, name.indexOf("]")));
        } else {
            return 0;
        }
    }

}
