package ai.vespa.llm.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonSchemaToGrammarConverter {

    /**
     * Representation of a built-in rule that may have dependencies on other rules.
     */
    public static class BuiltinRule {
        public String content;
        public List<String> deps;

        public BuiltinRule(String content, List<String> deps) {
            this.content = content;
            this.deps = (deps == null) ? new ArrayList<>() : deps;
        }
    }

    // Constraining spaces to prevent model "running away".
    private static final String SPACE_RULE = "| \" \" | \"\\n\" [ \\t]{0,20}";

    // Built-in primitive rules
    private static final Map<String, BuiltinRule> PRIMITIVE_RULES = new HashMap<>();
    static {
        PRIMITIVE_RULES.put("boolean", new BuiltinRule("(\"true\" | \"false\") space", Collections.emptyList()));
        PRIMITIVE_RULES.put("decimal-part", new BuiltinRule("[0-9]{1,16}", Collections.emptyList()));
        PRIMITIVE_RULES.put("integral-part", new BuiltinRule("[0] | [1-9] [0-9]{0,15}", Collections.emptyList()));
        PRIMITIVE_RULES.put("number", new BuiltinRule(
                "(\"-\"? integral-part) (\".\" decimal-part)? ([eE] [-+]? integral-part)? space",
                Arrays.asList("integral-part", "decimal-part")));
        PRIMITIVE_RULES.put("integer", new BuiltinRule("(\"-\"? integral-part) space",
                Collections.singletonList("integral-part")));
        PRIMITIVE_RULES.put("value", new BuiltinRule("object | array | string | number | boolean | null",
                Arrays.asList("object", "array", "string", "number", "boolean", "null")));
        PRIMITIVE_RULES.put("object", new BuiltinRule(
                "\"{\" space ( string \":\" space value (\",\" space string \":\" space value)* )? \"}\" space",
                Arrays.asList("string", "value")));
        PRIMITIVE_RULES.put("array", new BuiltinRule(
                "\"[\" space ( value (\",\" space value)* )? \"]\" space",
                Collections.singletonList("value")));
        PRIMITIVE_RULES.put("uuid", new BuiltinRule(
                "\"\\\"\" [0-9a-fA-F]{8} \"-\" [0-9a-fA-F]{4} \"-\" [0-9a-fA-F]{4} \"-\" [0-9a-fA-F]{4} \"-\" [0-9a-fA-F]{12} \"\\\"\" space",
                Collections.emptyList()));
        PRIMITIVE_RULES.put("char", new BuiltinRule(
                "[^\"\\\\\\x7F\\x00-\\x1F] | [\\\\] ([\"\\\\bfnrt] | \"u\" [0-9a-fA-F]{4})",
                Collections.emptyList()));
        PRIMITIVE_RULES.put("string", new BuiltinRule("\"\\\"\" char* \"\\\"\" space", Collections.singletonList("char")));
        PRIMITIVE_RULES.put("null", new BuiltinRule("\"null\" space", Collections.emptyList()));
    }

    // Extended string-format rules
    private static final Map<String, BuiltinRule> STRING_FORMAT_RULES = new HashMap<>();
    static {
        STRING_FORMAT_RULES.put("date",
                new BuiltinRule("[0-9]{4} \"-\" ( \"0\" [1-9] | \"1\" [0-2] ) \"-\" ( \"0\" [1-9] | [1-2] [0-9] | \"3\" [0-1] )",
                        Collections.emptyList()));
        STRING_FORMAT_RULES.put("time",
                new BuiltinRule("([01] [0-9] | \"2\" [0-3]) \":\" [0-5] [0-9] \":\" [0-5] [0-9] ( \".\" [0-9]{3} )? "
                        + "( \"Z\" | ( \"+\" | \"-\" ) ( [01] [0-9] | \"2\" [0-3] ) \":\" [0-5] [0-9] )",
                        Collections.emptyList()));
        STRING_FORMAT_RULES.put("date-time",
                new BuiltinRule("date \"T\" time",
                        Arrays.asList("date", "time")));
        STRING_FORMAT_RULES.put("date-string",
                new BuiltinRule("\"\\\"\" date \"\\\"\" space",
                        Collections.singletonList("date")));
        STRING_FORMAT_RULES.put("time-string",
                new BuiltinRule("\"\\\"\" time \"\\\"\" space",
                        Collections.singletonList("time")));
        STRING_FORMAT_RULES.put("date-time-string",
                new BuiltinRule("\"\\\"\" date-time \"\\\"\" space",
                        Collections.singletonList("date-time")));
    }

    // Reserved names
    private static final Set<String> RESERVED_NAMES = new HashSet<>();
    static {
        RESERVED_NAMES.add("root");
        RESERVED_NAMES.add("dot");
        RESERVED_NAMES.addAll(PRIMITIVE_RULES.keySet());
        RESERVED_NAMES.addAll(STRING_FORMAT_RULES.keySet());
    }

    private static final Pattern INVALID_RULE_CHARS_RE = Pattern.compile("[^a-zA-Z0-9-]+");
    private static final Pattern GRAMMAR_LITERAL_ESCAPE_RE = Pattern.compile("[\r\n\"]");
    private static final Map<String, String> GRAMMAR_LITERAL_ESCAPES = new HashMap<>();
    static {
        GRAMMAR_LITERAL_ESCAPES.put("\r", "\\r");
        GRAMMAR_LITERAL_ESCAPES.put("\n", "\\n");
        GRAMMAR_LITERAL_ESCAPES.put("\"", "\\\"");
        GRAMMAR_LITERAL_ESCAPES.put("-", "\\-");
        GRAMMAR_LITERAL_ESCAPES.put("]", "\\]");
    }

    private static final Set<String> NON_LITERAL_SET = new HashSet<>(Arrays.asList(
            "|",".","(",")","[","]","{","}","*","+","?"));
    private static final Set<Character> ESCAPED_IN_REGEXPS_BUT_NOT_IN_LITERALS = new HashSet<>(
            Arrays.asList('^','$','.', '[', ']', '(', ')', '|', '{', '}', '*', '+', '?'));

    /**
     * A converter that walks over the Jackson tree to build GBNF rules.
     */
    public static class SchemaConverter {
        private final Map<String, Integer> propOrder;
        private final boolean allowFetch;
        private final boolean dotAll;
        private final boolean rawPattern;

        // The grammar rules: ruleName -> definition
        private final Map<String, String> rules;

        // References map: $ref -> the actual sub-schema node
        private final Map<String, JsonNode> refs;

        // Which references are currently being resolved (to avoid cycles)
        private final Set<String> refsBeingResolved;

        private final ObjectMapper mapper = new ObjectMapper();

        public SchemaConverter(Map<String, Integer> propOrder,
                               boolean allowFetch,
                               boolean dotAll,
                               boolean rawPattern) {
            this.propOrder = propOrder;
            this.allowFetch = allowFetch;
            this.dotAll = dotAll;
            this.rawPattern = rawPattern;
            this.rules = new HashMap<>();
            this.refs = new HashMap<>();
            this.refsBeingResolved = new HashSet<>();

            // Set the "space" rule initially
            this.rules.put("space", SPACE_RULE);
        }

        public String formatGrammar() {
            List<String> keys = new ArrayList<>(rules.keySet());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder();
            for (String k : keys) {
                sb.append(k).append(" ::= ").append(rules.get(k)).append("\n");
            }
            return sb.toString();
        }

        /**
         * Resolve all $ref fields (recursively) in the given schema,
         * storing sub-schemas in `refs`.
         */
        public JsonNode resolveRefs(JsonNode schema, String url) {
            visitForRefs(schema, schema, url);
            return schema;
        }

        private JsonNode visitForRefs(JsonNode node, JsonNode root, String url) {
            if (node.isArray()) {
                ArrayNode arr = (ArrayNode) node;
                for (int i = 0; i < arr.size(); i++) {
                    JsonNode updated = visitForRefs(arr.get(i), root, url);
                    arr.set(i, updated);
                }
            } else if (node.isObject()) {
                ObjectNode obj = (ObjectNode) node;
                if (obj.has("$ref")) {
                    String refVal = obj.get("$ref").asText();
                    if (!refs.containsKey(refVal)) {
                        // handle remote references
                        if (refVal.startsWith("https://")) {
                            if (!allowFetch) {
                                throw new RuntimeException("Fetching remote schemas is not allowed. Use allowFetch=true to enable.");
                            }
                            int hashIdx = refVal.indexOf('#');
                            String baseUrl = (hashIdx == -1) ? refVal : refVal.substring(0, hashIdx);

                            if (!refs.containsKey(baseUrl)) {
                                JsonNode remoteSchema = fetchRemoteJSON(refVal);
                                // recursively resolve
                                remoteSchema = resolveRefs(remoteSchema, baseUrl);
                                refs.put(baseUrl, remoteSchema);
                            }
                            JsonNode target = refs.get(baseUrl);
                            String fragment = "";
                            if (hashIdx != -1 && hashIdx < refVal.length() - 1) {
                                fragment = refVal.substring(hashIdx + 1);
                            }
                            if (fragment.startsWith("/")) {
                                // walk
                                String[] parts = fragment.split("/");
                                JsonNode tmp = target;
                                for (int i = 1; i < parts.length; i++) {
                                    String sel = parts[i];
                                    if (!tmp.has(sel)) {
                                        throw new RuntimeException("Error resolving ref " + refVal + ": " + sel + " not found in " + tmp);
                                    }
                                    tmp = tmp.get(sel);
                                }
                                refs.put(refVal, tmp);
                                return tmp;
                            } else {
                                // empty or just '#'
                                refs.put(refVal, target);
                                return target;
                            }
                        } else if (refVal.startsWith("#/")) {
                            // local reference
                            String newRefVal = url + refVal;
                            obj.put("$ref", newRefVal);

                            JsonNode tmp = root;
                            String[] parts = refVal.split("/");
                            for (int i = 1; i < parts.length; i++) {
                                String sel = parts[i];
                                if (!tmp.has(sel)) {
                                    throw new RuntimeException("Error resolving ref " + refVal + ": " + sel + " not in " + tmp);
                                }
                                tmp = tmp.get(sel);
                            }
                            refs.put(newRefVal, tmp);
                            return tmp;
                        } else {
                            throw new RuntimeException("Unsupported ref " + refVal);
                        }
                    }
                } else {
                    // normal object => visit all fields
                    Iterator<String> fieldNames = obj.fieldNames();
                    while (fieldNames.hasNext()) {
                        String key = fieldNames.next();
                        JsonNode child = obj.get(key);
                        JsonNode updated = visitForRefs(child, root, url);
                        obj.set(key, updated);
                    }
                }
            }
            return node;
        }

        private JsonNode fetchRemoteJSON(String refVal) {
            try {
                URL u = new URL(refVal);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() != 200) {
                    throw new RuntimeException("Failed to fetch " + refVal + ": HTTP " + conn.getResponseCode());
                }
                try (InputStream is = conn.getInputStream()) {
                    return mapper.readTree(is);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error fetching " + refVal, e);
            }
        }

        public String visit(JsonNode schema, String name) {
            // type can be a single string or array
            String schemaType = null;
            if (schema.has("type") && schema.get("type").isTextual()) {
                schemaType = schema.get("type").asText();
            }
            String schemaFormat = schema.has("format") ? schema.get("format").asText() : null;

            String ruleName = name;
            if (RESERVED_NAMES.contains(ruleName)) {
                ruleName += "-";
            }
            if (ruleName.isEmpty()) {
                ruleName = "root";
            }

            // If $ref => delegate
            if (schema.has("$ref")) {
                String refVal = schema.get("$ref").asText();
                return addRule(ruleName, resolveRef(refVal));
            }

            // oneOf / anyOf => union
            if (schema.has("oneOf") || schema.has("anyOf")) {
                JsonNode arr = schema.has("oneOf") ? schema.get("oneOf") : schema.get("anyOf");
                if (!arr.isArray()) {
                    throw new RuntimeException("oneOf/anyOf must be an array");
                }
                List<JsonNode> altSchemas = new ArrayList<>();
                for (JsonNode n : arr) {
                    altSchemas.add(n);
                }
                return addRule(ruleName, generateUnionRule(name, altSchemas));
            }

            // if type is array of types => union
            if (schema.has("type") && schema.get("type").isArray()) {
                ArrayNode arr = (ArrayNode) schema.get("type");
                List<JsonNode> altSchemas = new ArrayList<>();
                for (JsonNode n : arr) {
                    ObjectNode partial = schema.deepCopy();
                    partial.remove("type");
                    partial.put("type", n.asText());
                    altSchemas.add(partial);
                }
                return addRule(ruleName, generateUnionRule(name, altSchemas));
            }

            // const => literal
            if (schema.has("const")) {
                JsonNode c = schema.get("const");
                String rule = formatLiteral(jsonValueAsString(c)) + " space";
                return addRule(ruleName, rule);
            }

            // enum => multiple constants
            if (schema.has("enum")) {
                JsonNode en = schema.get("enum");
                if (!en.isArray()) {
                    throw new RuntimeException("enum must be an array");
                }
                StringBuilder sb = new StringBuilder("(");
                for (int i = 0; i < en.size(); i++) {
                    if (i > 0) {
                        sb.append(" | ");
                    }
                    sb.append(formatLiteral(jsonValueAsString(en.get(i))));
                }
                sb.append(") space");
                return addRule(ruleName, sb.toString());
            }

            // object-like
            if ((schemaType == null || "object".equals(schemaType)) &&
                    (schema.has("properties") ||
                            (schema.has("additionalProperties") && !schema.get("additionalProperties").asBoolean(true)))) {
                // gather required
                Set<String> required = new HashSet<>();
                if (schema.has("required") && schema.get("required").isArray()) {
                    for (JsonNode rn : schema.get("required")) {
                        required.add(rn.asText());
                    }
                }
                // gather properties
                List<Map.Entry<String, JsonNode>> propsList = new ArrayList<>();
                if (schema.has("properties") && schema.get("properties").isObject()) {
                    Iterator<String> fieldIt = schema.get("properties").fieldNames();
                    while (fieldIt.hasNext()) {
                        String propName = fieldIt.next();
                        JsonNode propSchema = schema.get("properties").get(propName);
                        propsList.add(new AbstractMap.SimpleEntry<>(propName, propSchema));
                    }
                }
                // additionalProperties
                JsonNode apNode = schema.get("additionalProperties");
                JsonNode additionalPropsSchema = null;
                if (apNode != null) {
                    if (apNode.isBoolean() && !apNode.asBoolean()) {
                        additionalPropsSchema = null;
                    } else if (apNode.isObject()) {
                        additionalPropsSchema = apNode;
                    } else if (apNode.isBoolean() && apNode.asBoolean()) {
                        additionalPropsSchema = null; // means "any" => "value"
                    }
                }
                return addRule(ruleName, buildObjectRule(propsList, required, name, additionalPropsSchema));
            }

            // object + allOf
            if ((schemaType == null || "object".equals(schemaType)) && schema.has("allOf")) {
                JsonNode arr = schema.get("allOf");
                if (!arr.isArray()) {
                    throw new RuntimeException("allOf must be an array");
                }
                Set<String> required = new HashSet<>();
                List<Map.Entry<String, JsonNode>> propsList = new ArrayList<>();
                for (JsonNode comp : arr) {
                    if (comp.has("$ref")) {
                        comp = refs.get(comp.get("$ref").asText());
                    }
                    if (comp != null && comp.has("anyOf")) {
                        JsonNode anyOfArr = comp.get("anyOf");
                        if (anyOfArr.isArray()) {
                            for (JsonNode one : anyOfArr) {
                                addComponent(one, propsList, false, required);
                            }
                        }
                    } else {
                        addComponent(comp, propsList, true, required);
                    }
                }
                return addRule(ruleName, buildObjectRule(propsList, required, name, null));
            }

            // array-like
            if ((schemaType == null || "array".equals(schemaType)) &&
                    (schema.has("items") || schema.has("prefixItems"))) {
                // handle multiple forms
                if (schema.has("items") && schema.get("items").isArray()) {
                    ArrayNode itemsArr = (ArrayNode) schema.get("items");
                    StringBuilder sb = new StringBuilder("\"[\" space ");
                    for (int i = 0; i < itemsArr.size(); i++) {
                        if (i > 0) {
                            sb.append(" \",\" space ");
                        }
                        JsonNode itemSchema = itemsArr.get(i);
                        String itemRuleName = visit(itemSchema, (name.isEmpty() ? "" : name + "-") + "tuple-" + i);
                        sb.append(itemRuleName);
                    }
                    sb.append(" \"]\" space");
                    return addRule(ruleName, sb.toString());
                } else if (schema.has("prefixItems")) {
                    ArrayNode prefixArr = (ArrayNode) schema.get("prefixItems");
                    if (prefixArr.size() > 0) {
                        StringBuilder sb = new StringBuilder("\"[\" space ");
                        for (int i = 0; i < prefixArr.size(); i++) {
                            if (i > 0) {
                                sb.append(" \",\" space ");
                            }
                            JsonNode itemSchema = prefixArr.get(i);
                            String itemRuleName = visit(itemSchema, (name.isEmpty() ? "" : name + "-") + "tuple-" + i);
                            sb.append(itemRuleName);
                        }
                        sb.append(" \"]\" space");
                        return addRule(ruleName, sb.toString());
                    } else {
                        // prefix is empty => fallback to single 'items'
                        if (schema.has("items") && schema.get("items").isObject()) {
                            JsonNode itemsObj = schema.get("items");
                            String itemRuleName = visit(itemsObj, (name.isEmpty() ? "" : name + "-") + "items");
                            int minItems = schema.has("minItems") ? schema.get("minItems").asInt(0) : 0;
                            Integer maxItems = schema.has("maxItems") ? schema.get("maxItems").asInt() : null;
                            String repetition = buildRepetition(itemRuleName, minItems, maxItems, "\",\" space");
                            return addRule(ruleName, "\"[\" space " + repetition + " \"]\" space");
                        } else {
                            // fallback => "value"
                            String itemRuleName = addPrimitive("value", PRIMITIVE_RULES.get("value"));
                            int minItems = schema.has("minItems") ? schema.get("minItems").asInt(0) : 0;
                            Integer maxItems = schema.has("maxItems") ? schema.get("maxItems").asInt() : null;
                            String repetition = buildRepetition(itemRuleName, minItems, maxItems, "\",\" space");
                            return addRule(ruleName, "\"[\" space " + repetition + " \"]\" space");
                        }
                    }
                } else {
                    // single schema in items
                    JsonNode itemsObj = schema.get("items");
                    String itemRuleName;
                    if (itemsObj != null && itemsObj.isObject()) {
                        itemRuleName = visit(itemsObj, (name.isEmpty() ? "" : name + "-") + "item");
                    } else {
                        itemRuleName = addPrimitive("value", PRIMITIVE_RULES.get("value"));
                    }
                    int minItems = schema.has("minItems") ? schema.get("minItems").asInt(0) : 0;
                    Integer maxItems = schema.has("maxItems") ? schema.get("maxItems").asInt() : null;
                    String repetition = buildRepetition(itemRuleName, minItems, maxItems, "\",\" space");
                    return addRule(ruleName, "\"[\" space " + repetition + " \"]\" space");
                }
            }

            // string with pattern
            if ((schemaType == null || "string".equals(schemaType)) && schema.has("pattern")) {
                String pattern = schema.get("pattern").asText();
                return visitPattern(pattern, ruleName);
            }

            // string format
            if ((schemaType == null || "string".equals(schemaType)) && schemaFormat != null) {
                if (schemaFormat.matches("^uuid[1-5]?$")) {
                    // map to "uuid"
                    return addPrimitive(schemaFormat.equals("uuid") ? "uuid" : "uuid",
                            PRIMITIVE_RULES.get("uuid"));
                }
                // date-string, time-string, etc.
                if (STRING_FORMAT_RULES.containsKey(schemaFormat + "-string")) {
                    BuiltinRule br = STRING_FORMAT_RULES.get(schemaFormat + "-string");
                    return addRule(ruleName, addPrimitive(schemaFormat + "-string", br));
                }
            }

            // string with minLength, maxLength
            if ("string".equals(schemaType) &&
                    (schema.has("minLength") || schema.has("maxLength"))) {
                int minLen = schema.has("minLength") ? schema.get("minLength").asInt(0) : 0;
                Integer maxLen = schema.has("maxLength") ? schema.get("maxLength").asInt() : null;
                String charRuleName = addPrimitive("char", PRIMITIVE_RULES.get("char"));
                String repetition = buildRepetition(charRuleName, minLen, maxLen, null);
                return addRule(ruleName, "\"\\\"\" " + repetition + " \"\\\"\" space");
            }

            // integer with min/max
            if ((schemaType == null || "integer".equals(schemaType)) &&
                    (schema.has("minimum") || schema.has("exclusiveMinimum") ||
                            schema.has("maximum") || schema.has("exclusiveMaximum"))) {
                Integer minVal = null;
                Integer maxVal = null;
                if (schema.has("minimum")) {
                    minVal = schema.get("minimum").asInt();
                } else if (schema.has("exclusiveMinimum")) {
                    minVal = schema.get("exclusiveMinimum").asInt() + 1;
                }
                if (schema.has("maximum")) {
                    maxVal = schema.get("maximum").asInt();
                } else if (schema.has("exclusiveMaximum")) {
                    maxVal = schema.get("exclusiveMaximum").asInt() - 1;
                }
                StringBuilder sb = new StringBuilder("(");
                generateMinMaxInt(minVal, maxVal, sb, 16, true);
                sb.append(") space");
                return addRule(ruleName, sb.toString());
            }

            // fallback to known primitives
            if (schemaType != null && PRIMITIVE_RULES.containsKey(schemaType)) {
                return addPrimitive(ruleName.equals("root") ? "root" : schemaType,
                        PRIMITIVE_RULES.get(schemaType));
            }

            // if "object" or empty => fallback to "object"
            if ("object".equals(schemaType) || schema.size() == 0) {
                return addRule(ruleName, addPrimitive("object", PRIMITIVE_RULES.get("object")));
            }

            throw new RuntimeException("Unrecognized or unsupported schema: " + schema.toString());
        }

        private void addComponent(JsonNode compSchema,
                                  List<Map.Entry<String, JsonNode>> props,
                                  boolean isRequired,
                                  Set<String> required) {
            if (compSchema != null && compSchema.has("properties") && compSchema.get("properties").isObject()) {
                Iterator<String> it = compSchema.get("properties").fieldNames();
                while (it.hasNext()) {
                    String k = it.next();
                    JsonNode propSchema = compSchema.get("properties").get(k);
                    props.add(new AbstractMap.SimpleEntry<>(k, propSchema));
                }
            }
            if (compSchema != null && compSchema.has("required") && compSchema.get("required").isArray()) {
                for (JsonNode rn : compSchema.get("required")) {
                    if (isRequired) {
                        required.add(rn.asText());
                    }
                }
            }
        }

        private String buildObjectRule(List<Map.Entry<String, JsonNode>> properties,
                                       Set<String> requiredSet,
                                       String name,
                                       JsonNode additionalProps) {
            // sort by propOrder
            properties.sort((a,b) -> {
                int idxA = propOrder.getOrDefault(a.getKey(), Integer.MAX_VALUE);
                int idxB = propOrder.getOrDefault(b.getKey(), Integer.MAX_VALUE);
                return Integer.compare(idxA, idxB);
            });

            Map<String, String> propKvRuleNames = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> e : properties) {
                String propName = e.getKey();
                JsonNode propSchema = e.getValue();
                String propRuleName = visit(propSchema, (name.isEmpty()? "" : name + "-") + propName);
                String kvRule = formatLiteral(propName) + " space \":\" space " + propRuleName;
                String kvRuleName = addRule((name.isEmpty()? "" : name + "-") + propName + "-kv", kvRule);
                propKvRuleNames.put(propName, kvRuleName);
            }

            List<String> sortedProps = new ArrayList<>();
            for (Map.Entry<String, JsonNode> e : properties) {
                sortedProps.add(e.getKey());
            }
            List<String> requiredProps = new ArrayList<>();
            List<String> optionalProps = new ArrayList<>();
            for (String p : sortedProps) {
                if (requiredSet.contains(p)) {
                    requiredProps.add(p);
                } else {
                    optionalProps.add(p);
                }
            }

            if (additionalProps != null) {
                String subName = (name.isEmpty()? "" : name + "-") + "additional";
                String valueRule;
                if (additionalProps.isEmpty()) {
                    // means "true" => use "value"
                    valueRule = addPrimitive("value", PRIMITIVE_RULES.get("value"));
                } else {
                    // custom schema
                    valueRule = visit(additionalProps, subName + "-value");
                }
                if (sortedProps.isEmpty()) {
                    // just "string"
                    String keyName = addPrimitive("string", PRIMITIVE_RULES.get("string"));
                    String kvRule = keyName + " \":\" space " + valueRule;
                    String kvName = addRule(subName + "-kv", kvRule);
                    propKvRuleNames.put("*", kvName);
                    optionalProps.add("*");
                } else {
                    // exclude known property names
                    String notStringsRule = buildNotStrings(sortedProps);
                    String keyName = addRule(subName + "-k", notStringsRule);
                    String kvRule = keyName + " \":\" space " + valueRule;
                    String kvName = addRule(subName + "-kv", kvRule);
                    propKvRuleNames.put("*", kvName);
                    optionalProps.add("*");
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\"{\" space ");
            if (!requiredProps.isEmpty()) {
                for (int i = 0; i < requiredProps.size(); i++) {
                    if (i > 0) {
                        sb.append(" \",\" space ");
                    }
                    sb.append(propKvRuleNames.get(requiredProps.get(i)));
                }
            }
            if (!optionalProps.isEmpty()) {
                sb.append(" (");
                if (!requiredProps.isEmpty()) {
                    sb.append(" \",\" space ( ");
                }
                String union = buildOptionalPropsUnion(optionalProps, propKvRuleNames, name);
                sb.append(union);
                if (!requiredProps.isEmpty()) {
                    sb.append(" )");
                }
                sb.append(" )?");
            }
            sb.append(" \"}\" space");
            return sb.toString();
        }

        private String buildOptionalPropsUnion(List<String> optionalProps,
                                               Map<String, String> propKvRuleNames,
                                               String name) {
            class Helper {
                String getRecursiveRefs(List<String> ks, boolean firstOptional) {
                    if (ks.isEmpty()) return "";
                    String k = ks.get(0);
                    String kvName = propKvRuleNames.get(k);
                    String commaRef = "( \",\" space " + kvName + " )";
                    String res;
                    if (firstOptional) {
                        if (k.equals("*")) {
                            res = commaRef + "*";
                        } else {
                            res = commaRef + "?";
                        }
                    } else {
                        if (k.equals("*")) {
                            res = kvName + " " + commaRef + "*";
                        } else {
                            res = kvName;
                        }
                    }
                    if (ks.size() > 1) {
                        List<String> tail = ks.subList(1, ks.size());
                        String restRef = getRecursiveRefs(tail, true);
                        if (!restRef.isEmpty()) {
                            String restRuleName = addRule((name.isEmpty() ? "" : name + "-") + k + "-rest", restRef);
                            res += " " + restRuleName;
                        }
                    }
                    return res;
                }
            }
            Helper helper = new Helper();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < optionalProps.size(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                List<String> sub = optionalProps.subList(i, optionalProps.size());
                sb.append(helper.getRecursiveRefs(sub, false));
            }
            return sb.toString();
        }

        private String buildNotStrings(List<String> strings) {
            // Minimal approach: any string => "\"\"" [^"]* "\"\"" space
            return "\"\\\"\" [^\\\"]* \"\\\"\" space";
        }

        private String visitPattern(String pattern, String name) {
            if (!pattern.startsWith("^") || !pattern.endsWith("$")) {
                throw new RuntimeException("Pattern must start with '^' and end with '$': " + pattern);
            }
            pattern = pattern.substring(1, pattern.length() - 1);

            String dotPattern = dotAll ? "[\\U00000000-\\U0010FFFF]" : "[^\\x0A\\x0D]";
            String transformed = replaceUnescapedDot(pattern, dotPattern);

            if (rawPattern) {
                return addRule(name, transformed);
            } else {
                return addRule(name, "\"\\\"\" (" + transformed + ") \"\\\"\" space");
            }
        }

        private String replaceUnescapedDot(String pat, String dotReplacement) {
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            for (int i = 0; i < pat.length(); i++) {
                char c = pat.charAt(i);
                if (c == '\\') {
                    escaped = !escaped;
                    sb.append(c);
                } else {
                    if (c == '.' && !escaped) {
                        sb.append(dotReplacement);
                    } else {
                        sb.append(c);
                    }
                    escaped = false;
                }
            }
            return sb.toString();
        }

        private String resolveRef(String ref) {
            String refName = ref.substring(ref.lastIndexOf('/') + 1);
            if (!rules.containsKey(refName) && !refsBeingResolved.contains(ref)) {
                refsBeingResolved.add(ref);
                JsonNode resolved = refs.get(ref);
                refName = visit(resolved, refName);
                refsBeingResolved.remove(ref);
            }
            return refName;
        }

        private String formatLiteral(String literal) {
            StringBuilder sb = new StringBuilder();
            sb.append("\"");
            for (int i = 0; i < literal.length(); i++) {
                String ch = literal.substring(i, i + 1);
                if (GRAMMAR_LITERAL_ESCAPES.containsKey(ch)) {
                    sb.append(GRAMMAR_LITERAL_ESCAPES.get(ch));
                } else {
                    sb.append(ch);
                }
            }
            sb.append("\"");
            return sb.toString();
        }

        private String jsonValueAsString(JsonNode node) {
            try {
                // produces e.g. "123", "\"abc\"", "true", etc.
                return mapper.writeValueAsString(node).trim();
            } catch (Exception e) {
                return node.toString();
            }
        }

        private String generateUnionRule(String name, List<JsonNode> altSchemas) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < altSchemas.size(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                String altRuleName = visit(altSchemas.get(i), (name.isEmpty()? "" : name + "-") + "alternative-" + i);
                sb.append(altRuleName);
            }
            return sb.toString();
        }

        private String addRule(String name, String rule) {
            Matcher m = INVALID_RULE_CHARS_RE.matcher(name);
            String escName = m.replaceAll("-");
            if (!rules.containsKey(escName) || rules.get(escName).equals(rule)) {
                // okay
            } else {
                int i = 0;
                while (rules.containsKey(escName + i) && !rules.get(escName + i).equals(rule)) {
                    i++;
                }
                escName = escName + i;
            }
            rules.put(escName, rule);
            return escName;
        }

        private String addPrimitive(String name, BuiltinRule br) {
            String finalName = addRule(name, br.content);
            for (String dep : br.deps) {
                if (!rules.containsKey(dep)) {
                    BuiltinRule depBr = PRIMITIVE_RULES.get(dep);
                    if (depBr == null) {
                        depBr = STRING_FORMAT_RULES.get(dep);
                    }
                    if (depBr == null) {
                        throw new RuntimeException("Unknown built-in rule dependency: " + dep);
                    }
                    addPrimitive(dep, depBr);
                }
            }
            return finalName;
        }
    }

    /**
     * Builds a repetition snippet (like item?, item*, item+, etc.).
     */
    private static String buildRepetition(String itemRule, int minItems, Integer maxItems, String separatorRule) {
        if (minItems == 0 && maxItems != null && maxItems == 1) {
            return itemRule + "?";
        }
        if (separatorRule == null) {
            if (minItems == 1 && maxItems == null) {
                return itemRule + "+";
            } else if (minItems == 0 && maxItems == null) {
                return itemRule + "*";
            } else {
                return itemRule + "{" + minItems + (maxItems == null ? "," : "," + maxItems) + "}";
            }
        }
        int nextMin = Math.max(minItems - 1, 0);
        Integer nextMax = (maxItems == null) ? null : (maxItems - 1);
        String repeated = itemRule + " " + buildRepetition("(" + separatorRule + " " + itemRule + ")", nextMin, nextMax, null);
        if (minItems == 0) {
            return "(" + repeated + ")?";
        }
        return repeated;
    }

    /**
     * Generates an integer range snippet (like for minimum/maximum constraints).
     */
    private static void generateMinMaxInt(Integer minValue, Integer maxValue, StringBuilder out,
                                          int decimalsLeft, boolean topLevel) {
        boolean hasMin = (minValue != null);
        boolean hasMax = (maxValue != null);

        class RangeEmitter {
            void digitRange(char fromChar, char toChar) {
                out.append("[");
                if (fromChar == toChar) {
                    out.append(fromChar);
                } else {
                    out.append(fromChar).append("-").append(toChar);
                }
                out.append("]");
            }
            void moreDigits(int minDigits, int maxDigits) {
                out.append("[0-9]");
                if (minDigits == maxDigits && minDigits == 1) {
                    return;
                }
                out.append("{").append(minDigits);
                if (maxDigits != minDigits) {
                    out.append(",");
                    if (maxDigits != Integer.MAX_VALUE) {
                        out.append(maxDigits);
                    }
                }
                out.append("}");
            }
            void uniformRange(String fromStr, String toStr) {
                int i = 0;
                int len = Math.min(fromStr.length(), toStr.length());
                while (i < len && fromStr.charAt(i) == toStr.charAt(i)) {
                    i++;
                }
                if (i > 0) {
                    out.append("\"");
                    out.append(fromStr, 0, i);
                    out.append("\"");
                }
                if (i < fromStr.length()) {
                    if (i > 0) {
                        out.append(" ");
                    }
                    int subLen = fromStr.length() - i - 1;
                    if (subLen > 0) {
                        String fromSub = fromStr.substring(i + 1);
                        String toSub = toStr.substring(i + 1);
                        StringBuilder sbZeros = new StringBuilder();
                        StringBuilder sbNines = new StringBuilder();
                        for (int k = 0; k < subLen; k++) {
                            sbZeros.append('0');
                            sbNines.append('9');
                        }
                        String subZeros = sbZeros.toString();
                        String subNines = sbNines.toString();
                        boolean toReached = false;
                        out.append("(");
                        char fromChar = fromStr.charAt(i);
                        char toChar = toStr.charAt(i);
                        if (fromSub.equals(subZeros)) {
                            digitRange(fromChar, (char)(toChar - 1));
                            out.append(" ");
                            moreDigits(subLen, subLen);
                        } else {
                            out.append("[");
                            out.append(fromChar);
                            out.append("] ");
                            out.append("(");
                            uniformRange(fromSub, subNines);
                            out.append(")");
                            if ((int)fromChar < (int)toChar - 1) {
                                out.append(" | ");
                                if (toSub.equals(subNines)) {
                                    digitRange((char)(fromChar + 1), toChar);
                                    toReached = true;
                                } else {
                                    digitRange((char)(fromChar + 1), (char)(toChar - 1));
                                }
                                out.append(" ");
                                moreDigits(subLen, subLen);
                            }
                        }
                        if (!toReached) {
                            out.append(" | ");
                            digitRange(toChar, toChar);
                            out.append(" ");
                            uniformRange(subZeros, toSub);
                        }
                        out.append(")");
                    } else {
                        // single char difference
                        out.append("[");
                        out.append(fromStr.charAt(i)).append("-").append(toStr.charAt(i));
                        out.append("]");
                    }
                }
            }
        }
        RangeEmitter emitter = new RangeEmitter();

        if (hasMin && hasMax) {
            if (minValue < 0 && maxValue < 0) {
                out.append("\"-\" (");
                generateMinMaxInt(-maxValue, -minValue, out, decimalsLeft, true);
                out.append(")");
                return;
            }
            if (minValue < 0) {
                out.append("\"-\" (");
                generateMinMaxInt(0, -minValue, out, decimalsLeft, true);
                out.append(") | ");
                minValue = 0;
            }
            String minS = String.valueOf(minValue);
            String maxS = String.valueOf(maxValue);
            int minDigits = minS.length();
            int maxDigits = maxS.length();
            for (int d = minDigits; d < maxDigits; d++) {
                emitter.uniformRange(minS, repeatChar('9', d));
                out.append(" | ");
                // new minS => 1 + "0"*d
                StringBuilder sbd = new StringBuilder("1");
                for (int k = 0; k < d; k++) {
                    sbd.append("0");
                }
                minS = sbd.toString();
            }
            emitter.uniformRange(minS, maxS);
            return;
        }

        int lessDecimals = Math.max(decimalsLeft - 1, 1);

        if (hasMin) {
            if (minValue < 0) {
                out.append("\"-\" (");
                generateMinMaxInt(null, -minValue, out, decimalsLeft, false);
                out.append(") | [0] | [1-9] ");
                emitter.moreDigits(0, decimalsLeft - 1);
            } else if (minValue == 0) {
                if (topLevel) {
                    out.append("[0] | [1-9] ");
                    emitter.moreDigits(0, lessDecimals);
                } else {
                    emitter.moreDigits(1, decimalsLeft);
                }
            } else if (minValue <= 9) {
                char c = (char)('0' + minValue);
                char rangeStart = topLevel ? '1' : '0';
                if (c > rangeStart) {
                    emitter.digitRange(rangeStart, (char)(c - 1));
                    out.append(" ");
                    emitter.moreDigits(1, lessDecimals);
                    out.append(" | ");
                }
                emitter.digitRange(c, '9');
                out.append(" ");
                emitter.moreDigits(0, lessDecimals);
            } else {
                String minS = String.valueOf(minValue);
                int length = minS.length();
                char c = minS.charAt(0);
                if (c > '1') {
                    emitter.digitRange(topLevel ? '1' : '0', (char)(c - 1));
                    out.append(" ");
                    emitter.moreDigits(length, lessDecimals);
                    out.append(" | ");
                }
                emitter.digitRange(c, c);
                out.append(" (");
                int remainder = 0;
                if (minS.length() > 1) {
                    remainder = Integer.parseInt(minS.substring(1));
                }
                generateMinMaxInt(remainder, null, out, lessDecimals, false);
                out.append(")");
                if (c < '9') {
                    out.append(" | ");
                    emitter.digitRange((char)(c + 1), '9');
                    out.append(" ");
                    emitter.moreDigits(length - 1, lessDecimals);
                }
            }
            return;
        }

        if (hasMax) {
            if (maxValue >= 0) {
                if (topLevel) {
                    out.append("\"-\" [1-9] ");
                    emitter.moreDigits(0, lessDecimals);
                    out.append(" | ");
                }
                generateMinMaxInt(0, maxValue, out, decimalsLeft, true);
            } else {
                out.append("\"-\" (");
                generateMinMaxInt(-maxValue, null, out, decimalsLeft, false);
                out.append(")");
            }
            return;
        }

        throw new RuntimeException("At least one of min_value or max_value must be set");
    }

    private static String repeatChar(char c, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------

    /**
     * *Takes a JSON Schema and returns GBNF grammar as a String.
     *
     * @param schemaJson JSON Schema as a string
     * @param allowFetch Whether to allow fetching referenced schemas from https://
     * @param dotAll Whether '.' in patterns matches all characters (including newlines)
     * @param rawPattern If false, patterns are quoted in the grammar; if true, use them raw
     * @param propOrder A map from propertyName -> integer rank (lower = higher priority)
     * @return Grammar as a string
     */
    public static String convert(
            String schemaJson,
            boolean allowFetch,
            boolean dotAll,
            boolean rawPattern,
            Map<String, Integer> propOrder
    ) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode schema = mapper.readTree(schemaJson);

            // Create converter
            SchemaConverter converter = new SchemaConverter(propOrder, allowFetch, dotAll, rawPattern);

            // Resolve references
            schema = converter.resolveRefs(schema, "input-schema"); // a default "url" label

            // Visit schema => produce grammar
            converter.visit(schema, "");

            return converter.formatGrammar();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse schema JSON", e);
        }
    }
}