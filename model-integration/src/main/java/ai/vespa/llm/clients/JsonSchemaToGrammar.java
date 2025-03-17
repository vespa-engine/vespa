package ai.vespa.llm.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.api.annotations.Beta;

/**
 * Converts a JSON Schema to a GBNF grammar to be used for structured output with llama.cpp.
 * It is a port of Python implementation from llama.cpp repository.
 * It doesn't support regex patterns, resolving $ref or fetching schemas from URLs.
 */
@Beta
class JsonSchemaToGrammar {
    public static String convert(String schema) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootSchema;

        try {
            rootSchema = mapper.readTree(schema);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        var converter = new JsonSchemaToGrammar();
        converter.visit(rootSchema, null);
        return converter.formatGrammar() + "\n";
    }

    private record BuiltinRule(String content, List<String> dependencies) {
    }

    private static final String SPACE_RULE = "| \" \" | \"\\n\" [ \\t]{0,20}";

    private static final Map<String, BuiltinRule> PRIMITIVE_RULES = Map.ofEntries(
            Map.entry("boolean", new BuiltinRule("(\"true\" | \"false\") space", List.of())),
            Map.entry("decimal-part", new BuiltinRule("[0-9]{1,16}", List.of())),
            Map.entry("integral-part", new BuiltinRule("[0] | [1-9] [0-9]{0,15}", List.of())),
            Map.entry("number", new BuiltinRule("(\"-\"? integral-part) (\".\" decimal-part)? ([eE] [-+]? integral-part)? space", List.of("integral-part", "decimal-part"))),
            Map.entry("integer", new BuiltinRule("(\"-\"? integral-part) space", List.of("integral-part"))),
            Map.entry("value", new BuiltinRule("object | array | string | number | boolean | null", List.of("object", "array", "string", "number", "boolean", "null"))),
            Map.entry("object", new BuiltinRule("\"{\" space ( string \":\" space value (\",\" space string \":\" space value)* )? \"}\" space", List.of("string", "value"))),
            Map.entry("array", new BuiltinRule("\"[\" space ( value (\",\" space value)* )? \"]\" space", List.of("value"))),
            Map.entry("uuid", new BuiltinRule("\"\\\"\" [0-9a-fA-F]{8} \"-\" [0-9a-fA-F]{4} \"-\" [0-9a-fA-F]{4} \"-\" [0-9a-fA-F]{4} \"-\" [0-9a-fA-F]{12} \"\\\"\" space", List.of())),
            Map.entry("char", new BuiltinRule("[^\"\\\\\\x7F\\x00-\\x1F] | [\\\\] ([\"\\\\bfnrt] | \"u\" [0-9a-fA-F]{4})", List.of())),
            Map.entry("string", new BuiltinRule("\"\\\"\" char* \"\\\"\" space", List.of("char"))),
            Map.entry("null", new BuiltinRule("\"null\" space", List.of()))
    );

    private static final Map<String, BuiltinRule> FORMATTED_STRING_RULES = Map.of(
            "date", new BuiltinRule("[0-9]{4} \"-\" ( [0] [1-9] | [1] [0-2] ) \"-\" ( [0] [1-9] | [1-2] [0-9] | [3] [0-1] )", List.of()),
            "time", new BuiltinRule("([01] [0-9] | [2] [0-3]) \":\" [0-5] [0-9] \":\" [0-5] [0-9] ( \".\" [0-9]{3} )? ( \"Z\" | ( \"+\" | \"-\" ) ( [01] [0-9] | [2] [0-3] ) \":\" [0-5] [0-9] )", List.of()),
            "date-time", new BuiltinRule("date \"T\" time", List.of("date", "time")),
            "date-string", new BuiltinRule("\"\\\"\" date \"\\\"\" space", List.of("date")),
            "time-string", new BuiltinRule("\"\\\"\" time \"\\\"\" space", List.of("time")),
            "date-time-string", new BuiltinRule("\"\\\"\" date-time \"\\\"\" space", List.of("date-time"))
    );

    private static final Set<String> RESERVED_NAMES = new HashSet<>();
    static {
        RESERVED_NAMES.add("root");
        RESERVED_NAMES.add("dot");
        RESERVED_NAMES.addAll(PRIMITIVE_RULES.keySet());
        RESERVED_NAMES.addAll(FORMATTED_STRING_RULES.keySet());
    }

    private static final Pattern INVALID_RULE_CHARS_RE = Pattern.compile("[^a-zA-Z0-9-]+");
    private static final Pattern GRAMMAR_LITERAL_ESCAPE_RE = Pattern.compile("[\r\n\"]");
    private static final Map<String, String> GRAMMAR_LITERAL_ESCAPES = Map.of(
            "\r", "\\r",
            "\n", "\\n",
            "\"", "\\\"",
            "-", "\\-",
            "]", "\\]"
    );

    private final List<Visitor> visitors = List.of(
            new OneOfOrAnyOfVisitor(),
            new UnionOfSchemasVisitor(),
            new ConstVisitor(),
            new EnumVisitor(),
            new ObjectWithAdditionalPropertiesVisitor(),
            new ObjectWithAllOfVisitor(),
            new ArrayVisitor(),
            new UuidVisitor(),
            new FormattedStringVisitor(),
            new StringWithMinMaxLengthVisitor(),
            new IntegerWithMinMaxVisitor(),
            new ObjectVisitor(),
            new PrimitiveVisitor()
    );

    private final Map<String, String> rules;

    public JsonSchemaToGrammar() {
        this.rules = new HashMap<>();
        this.rules.put("space", SPACE_RULE);
    }

    private String addRule(String name, String content) {
        var finalName = INVALID_RULE_CHARS_RE.matcher(name).replaceAll("-");

        if (rules.containsKey(finalName) && !rules.get(finalName).equals(content)) {
            var index = 0;

            while (rules.containsKey(finalName + index) && !rules.get(finalName + index).equals(content)) {
                index += 1;
            }

            finalName = finalName + index;
        }

        rules.put(finalName, content);
        return finalName;
    }

    private String visit(JsonNode schema, String baseName) {
        String name;

        if (baseName == null) {
            name = "root";
        } else if (RESERVED_NAMES.contains(baseName)) {
            name = baseName + "-";
        } else {
            name = baseName;
        }

        for (var visitor : visitors) {
            var ruleContent = visitor.maybeVisit(schema, baseName, name);

            if (ruleContent.isPresent()) {
                return ruleContent.get();
            }
        }

        throw new IllegalArgumentException("Unrecognized schema: " + schema);
    }

    private interface Visitor {
        Optional<String> maybeVisit(JsonNode schema, String baseName, String name);
    }

    private class OneOfOrAnyOfVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            if (schema.has("oneOf") || schema.has("anyOf")) {
                var oneNode = schema.has("oneOf") ? schema.get("oneOf") : schema.get("anyOf");
                var oneChildren = new ArrayList<JsonNode>();
                oneNode.elements().forEachRemaining(oneChildren::add);
                var rule = addRule(name, generateUnion(baseName, oneChildren));
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class UnionOfSchemasVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var type = schema.get("type");

            if (type != null && type.isArray()) {
                var modSchemas = new ArrayList<JsonNode>();

                for (Iterator<JsonNode> it = type.elements(); it.hasNext(); ) {
                    var t = it.next();
                    var schemaCopy = schema.deepCopy();
                    ((ObjectNode) schemaCopy).set("type", t);
                    modSchemas.add(schemaCopy);
                }

                var rule = addRule(name, generateUnion(baseName, modSchemas));
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class ConstVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            if (schema.has("const")) {
                var rule = addRule(name, generateConstant(schema.get("const")) + " space");
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class EnumVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            if (schema.has("enum")) {
                var content = new StringBuilder();
                content.append("(");

                for (var i = 0; i < schema.get("enum").size(); i++) {
                    if (i > 0) {
                        content.append(" | ");
                    }

                    content.append(generateConstant(schema.get("enum").get(i)));
                }

                content.append(") space");
                var rule = addRule(name, content.toString());
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class ObjectWithAdditionalPropertiesVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var typeText = getTypeText(schema);

            if ((typeText == null || typeText.equals("object"))
                    && (schema.has("properties") ||
                    (schema.has("additionalProperties") &&  !schema.get("additionalProperties").asBoolean()))) {
                var required = new HashSet<String>();
                if (schema.has("required"))
                    schema.get("required").forEach(node -> required.add(node.asText()));

                var properties = new ArrayList<Map.Entry<String, JsonNode>>();
                if (schema.has("properties"))
                    schema.get("properties").fields().forEachRemaining(properties::add);

                var rule = addRule(name, generateObject(properties, required, baseName, schema.get("additionalProperties")));
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class ObjectWithAllOfVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var typeText = getTypeText(schema);

            if ((typeText == null || typeText.equals("object")) && schema.has("allOf")) {
                var required = new HashSet<String>();
                var properties = new ArrayList<Map.Entry<String, JsonNode>>();

                BiConsumer<JsonNode, Boolean> addComponent = (compNode, isRequired) -> {
                    if (compNode.has("properties")) {
                        compNode.get("properties").fields().forEachRemaining(properties::add);
                        if (isRequired) {
                            compNode.get("properties").fieldNames().forEachRemaining(required::add);
                        }
                    }
                };

                for (var allOfChildNode : schema.get("allOf")) {
                    if (allOfChildNode.has("anyOf")) {
                        for (JsonNode anyOfChild : allOfChildNode.get("anyOf")) {
                            addComponent.accept(anyOfChild, false);
                        }
                    } else {
                        addComponent.accept(allOfChildNode, true);
                    }
                }

                var rule = addRule(name, generateObject(properties, required, baseName, null));
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class ArrayVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var typeText = getTypeText(schema);

            if ((typeText == null || typeText.equals("array"))
                    && (schema.has("items") || schema.has("prefixItems"))) {
                var items = schema.has("items") ? schema.get("items") : schema.get("prefixItems");

                if (items.isArray()) {
                    var content = new StringBuilder();
                    content.append("\"[\" space ");

                    for (var i = 0; i < items.size(); i++) {
                        if (i > 0) {
                            content.append(" \",\" space ");
                        }

                        var item = items.get(i);
                        var itemRuleName = (baseName == null ? "" : baseName + "-") + "tuple-" + i;
                        var itemRule = visit(item, itemRuleName);
                        content.append(itemRule);
                    }

                    content.append(" \"]\" space");
                    var rule = addRule(name, content.toString());
                    return Optional.of(rule);
                } else {
                    var itemRuleName = (baseName == null ? "" : baseName + "-") + "item";
                    var visitedItemRuleName = visit(items, itemRuleName);

                    int minItems = schema.has("minItems") ? schema.get("minItems").asInt() : 0;
                    Integer maxItems = schema.has("maxItems") ? schema.get("maxItems").asInt() : null;

                    var content = "\"[\" space " +
                            generateRepetition(visitedItemRuleName, minItems, maxItems, "\",\" space") +
                            " \"]\" space";
                    var rule = addRule(name, content);
                    return Optional.of(rule);
                }
            }

            return Optional.empty();
        }
    }

    private class UuidVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var typeText = getTypeText(schema);
            var formatText = getFormatText(schema);

            if ((typeText == null || typeText.equals("string"))
                    && formatText != null && formatText.matches("^uuid[1-5]?$")) {
                var rule = addPrimitive(name.equals("root") ? "root" : formatText, PRIMITIVE_RULES.get("uuid"));
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class FormattedStringVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var typeText = getTypeText(schema);
            var formatText = getFormatText(schema);

            if ((typeText == null || typeText.equals("string"))
                    && FORMATTED_STRING_RULES.containsKey(formatText + "-string")) {
                var primitiveName = formatText + "-string";
                var primitive = addPrimitive(primitiveName, FORMATTED_STRING_RULES.get(primitiveName));
                var rule =  addRule(name, primitive);
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class StringWithMinMaxLengthVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var typeText = getTypeText(schema);

            if (typeText != null && typeText.equals("string")
                    && (schema.has("minLength") || schema.has("maxLength"))) {
                String charRule = addPrimitive("char", PRIMITIVE_RULES.get("char"));
                var minLen = schema.has("minLength") ? schema.get("minLength").asInt() : 0;
                var maxLen = schema.has("maxLength") ? schema.get("maxLength").asInt() : null;
                var conent = "\"\\\"\" " + generateRepetition(charRule, minLen, maxLen, null) + " \"\\\"\" space";
                var rule = addRule(name, conent);
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class IntegerWithMinMaxVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var typeText = getTypeText(schema);

            if ((typeText == null || typeText.equals("integer"))
                    && (schema.has("minimum") || schema.has("exclusiveMinimum")
                    || schema.has("maximum") || schema.has("exclusiveMaximum"))) {

                Integer minValue = null;
                Integer maxValue = null;

                if (schema.has("minimum")) {
                    minValue = schema.get("minimum").asInt();
                } else if (schema.has("exclusiveMinimum")) {
                    minValue = schema.get("exclusiveMinimum").asInt() + 1;
                }

                if (schema.has("maximum")) {
                    maxValue = schema.get("maximum").asInt();
                } else if (schema.has("exclusiveMaximum")) {
                    maxValue = schema.get("exclusiveMaximum").asInt() - 1;
                }

                var content = "(" + generateMinMaxInt(minValue, maxValue, 16, true) + ") space";
                var rule = addRule(name, content);
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class ObjectVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var typeText = getTypeText(schema);

            if (typeText == null || typeText.equals("object") || schema.isEmpty()) {
                var rule = addRule(name, addPrimitive("object", PRIMITIVE_RULES.get("object")));
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private class PrimitiveVisitor implements Visitor {
        public Optional<String> maybeVisit(JsonNode schema, String baseName, String name) {
            var schemaTypeText = getTypeText(schema);

            if (schemaTypeText != null && PRIMITIVE_RULES.containsKey(schemaTypeText)) {
                var rule = addPrimitive(name.equals("root") ? "root" : schemaTypeText, PRIMITIVE_RULES.get(schemaTypeText));
                return Optional.of(rule);
            }

            return Optional.empty();
        }
    }

    private String getTypeText(JsonNode schema) {
        if (schema.has("type"))
            return schema.get("type").asText();

        return null;
    }

    private String getFormatText(JsonNode schema) {
        if (schema.has("format"))
            return schema.get("format").asText();

        return null;
    }

    private String generateRepetition(String itemRule, int minItems, Integer maxItems, String separatorRule) {
        if (minItems == 0 && maxItems != null && maxItems == 1) {
            return itemRule + "?";
        }

        if (separatorRule == null) {
            if (minItems == 1 && maxItems == null) {
                return itemRule + "+";
            } else if (minItems == 0 && maxItems == null) {
                return itemRule + "*";
            } else {
                return itemRule + "{" + minItems + "," + (maxItems != null ? maxItems : "") + "}";
            }
        }

        String result = itemRule + " " + generateRepetition(
                "(" + separatorRule + " " + itemRule + ")",
                minItems > 0 ? minItems - 1 : 0,
                maxItems != null ? maxItems - 1 : null,
                null
        );

        return (minItems == 0) ? "(" + result + ")?" : result;
    }

    public static String generateDigitRange(char fromChar, char toChar) {
        if (fromChar == toChar) {
            return "[" + fromChar + "]";
        } else {
            return "[" + fromChar + "-" + toChar + "]";
        }
    }

    public static String generateMoreDigits(int minDigits, int maxDigits) {
        var content = new StringBuilder();
        content.append("[0-9]");

        if (minDigits == maxDigits && minDigits == 1) {
            return content.toString();
        }

        content.append("{")
                .append(minDigits);

        if (maxDigits != minDigits) {
            content.append(",");

            if (maxDigits != Integer.MAX_VALUE) {
                content.append(maxDigits);
            }
        }

        content.append("}");
        return content.toString();
    }

    public static String generateUniformRange(String fromStr, String toStr) {
        var content = new StringBuilder();
        int samePrefixIndex = 0;

        while (samePrefixIndex < fromStr.length() && fromStr.charAt(samePrefixIndex) == toStr.charAt(samePrefixIndex)) {
            samePrefixIndex += 1;
        }

        var samePrefix = fromStr.substring(0, samePrefixIndex);

        if (samePrefix.length() == 1) {
            content.append("[").append(samePrefix).append("]");
        } else if (samePrefix.length() > 1) {
            content.append("\"").append(samePrefix).append("\"");
        }

        if (samePrefixIndex < fromStr.length()) {
            if (samePrefixIndex > 0) {
                content.append(" ");
            }

            var subLen = fromStr.length() - samePrefixIndex - 1;

            if (subLen > 0) {
                var fromSub = fromStr.substring(samePrefixIndex + 1);
                var toSub = toStr.substring(samePrefixIndex + 1);
                var subZeros = "0".repeat(subLen);
                var subNines = "9".repeat(subLen);

                var toReached = false;
                content.append("(");

                if (fromSub.equals(subZeros)) {
                    content.append(generateDigitRange(fromStr.charAt(samePrefixIndex), (char) (toStr.charAt(samePrefixIndex) - 1)))
                            .append(" ")
                            .append(generateMoreDigits(subLen, subLen));
                } else {
                    content.append("[")
                            .append(fromStr.charAt(samePrefixIndex))
                            .append("] ")
                            .append("(")
                            .append(generateUniformRange(fromSub, subNines))
                            .append(")");

                    if (fromStr.charAt(samePrefixIndex) < toStr.charAt(samePrefixIndex) - 1) {
                        content.append(" | ");
                        if (toSub.equals(subNines)) {
                            content.append(generateDigitRange((char) (fromStr.charAt(samePrefixIndex) + 1), toStr.charAt(samePrefixIndex)));
                            toReached = true;
                        } else {
                            content.append(generateDigitRange((char) (fromStr.charAt(samePrefixIndex) + 1), (char) (toStr.charAt(samePrefixIndex) - 1)));
                        }
                        content.append(" ");
                        content.append(generateMoreDigits(subLen, subLen));
                    }
                }

                if (!toReached) {
                    content.append(" | ")
                            .append(generateDigitRange(toStr.charAt(samePrefixIndex), toStr.charAt(samePrefixIndex)))
                            .append(" ")
                            .append(generateUniformRange(subZeros, toSub));
                }

                content.append(")");
            } else {
                content.append("[")
                        .append(fromStr.charAt(samePrefixIndex))
                        .append("-")
                        .append(toStr.charAt(samePrefixIndex))
                        .append("]");
            }
        }

        return content.toString();
    }

    private String generateMinMaxInt(Integer min_value, Integer max_value, int decimals_left, boolean top_level) {
        var content = new StringBuilder();

        if (min_value != null && max_value != null) {
            if (min_value < 0 && max_value < 0) {
                content.append("\"-\" (")
                        .append(generateMinMaxInt(-max_value, -min_value, decimals_left, true))
                        .append(")");
                return content.toString();
            }

            if (min_value < 0) {
                content.append("\"-\" (")
                        .append(generateMinMaxInt(0, -min_value, decimals_left, true))
                        .append(") | ");
                min_value = 0;
            }

            var min_s = Integer.toString(min_value);
            var max_s = Integer.toString(max_value);
            var minDigits = min_s.length();
            var maxDigits = max_s.length();

            for (var digits = minDigits; digits < maxDigits; digits++) {
                content.append(generateUniformRange(min_s, "9".repeat(digits)));
                min_s = "1" + "0".repeat(digits);
                content.append(" | ");
            }

            content.append(generateUniformRange(min_s, max_s));
            return content.toString();
        }

        if (min_value != null) {
            if (min_value < 0) {
                content.append("\"-\" (")
                        .append(generateMinMaxInt(null, -min_value, decimals_left, false))
                        .append(") | [0] | [1-9] ")
                        .append(generateMoreDigits(0, decimals_left - 1));
            } else if (min_value == 0) {
                if (top_level) {
                    content.append("[0] | [1-9] ")
                            .append(generateMoreDigits(0, decimals_left - 1));
                } else {
                    content.append(generateMoreDigits(1, decimals_left));
                }
            } else if (min_value <= 9) {
                var c = Integer.toString(min_value);
                var rangeStart = top_level ? "1" : "0";
                if (c.compareTo(rangeStart) > 0) {
                    content.append(generateUniformRange(rangeStart, Character.toString((char) (c.charAt(0) - 1))))
                            .append(" ")
                            .append(generateMoreDigits(1, decimals_left - 1))
                            .append(" | ");
                }

                content.append(generateUniformRange(c, "9"))
                        .append(" ")
                        .append(generateMoreDigits(0, decimals_left - 1));
            } else {
                var min_s = Integer.toString(min_value);
                var length = min_s.length();
                var c = min_s.charAt(0);

                if (c > '1') {
                    content.append(generateUniformRange(top_level ? "1" : "0", Character.toString((char) (c - 1))))
                            .append(" ")
                            .append(generateMoreDigits(length, decimals_left - 1))
                            .append(" | ");
                }

                content.append(generateUniformRange(Character.toString(c), Character.toString(c)))
                        .append(" (")
                        .append(generateMinMaxInt(Integer.parseInt(min_s.substring(1)), null, decimals_left - 1, false))
                        .append(")");

                if (c < '9') {
                    content.append(" | ")
                            .append(generateUniformRange(Character.toString((char) (c + 1)), "9"))
                            .append(" ")
                            .append(generateMoreDigits(length - 1, decimals_left - 1));
                }
            }
            return content.toString();
        }

        if (max_value != null) {
            if (max_value >= 0) {
                if (top_level) {
                    content.append("\"-\" [1-9] ")
                            .append(generateMoreDigits(0, decimals_left - 1))
                            .append(" | ");
                }
                content.append(generateMinMaxInt(0, max_value, decimals_left, true));
            } else {
                content.append("\"-\" (")
                        .append(generateMinMaxInt(-max_value, null, decimals_left, false))
                        .append(")");
            }

            return content.toString();
        }

        throw new RuntimeException("At least one of min_value or max_value must be set");
    }


    private String formatLiteral(String literal) {
        var escaped = GRAMMAR_LITERAL_ESCAPE_RE.matcher(literal).replaceAll(
                m -> GRAMMAR_LITERAL_ESCAPES.getOrDefault(m.group(0), m.group(0)));

        return "\"" + escaped + "\"";
    }

    private String generateConstant(JsonNode value) {
        var valueStr = value.toString().replace("\"", "\\\"");
        return formatLiteral(valueStr);
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean isEndOfString = false;

        public void insert(String string) {
            var node = this;

            for (var c : string.toCharArray()) {
                node = node.children.computeIfAbsent(c, k -> new TrieNode());
            }

            node.isEndOfString = true;
        }
    }

    private String generateNotStrings(Set<String> strings) {
        var trie = new TrieNode();

        for (var s : strings) {
            trie.insert(s);
        }

        String charRule = addPrimitive("char", PRIMITIVE_RULES.get("char"));

        var content = new StringBuilder();
        content.append("[\"] ( ");
        generateNotStringsVisit(trie, charRule, content);
        content.append(" )")
                .append(trie.isEndOfString ? "" : "?")
                .append(" [\"] space");

        return content.toString();
    }

    private void generateNotStringsVisit(TrieNode node, String charRule, StringBuilder content) {
        var rejects = new ArrayList<String>();
        var first = true;

        for (var c : node.children.keySet().stream().sorted().toList()) {

            var child = node.children.get(c);
            rejects.add(c.toString());

            if (!first) {
                content.append(" | ");
            }

            content.append("[").append(c).append("]");

            if (!child.children.isEmpty()) {
                content.append(" (");
                generateNotStringsVisit(child, charRule, content);
                content.append(")");
            } else if (child.isEndOfString) {
                content.append(" ").append(charRule).append("+");
            }

            first = false;
        }

        if (!node.children.isEmpty()) {
            if (!first) {
                content.append(" | ");
            }

            content
                    .append("[^\"")
                    .append(String.join("", rejects))
                    .append("] ")
                    .append(charRule)
                    .append("*");
        }
    }

    private String generateUnion(String name, List<JsonNode> altSchemas) {
        var content = new StringBuilder();

        for (var i = 0; i < altSchemas.size(); i++) {
            var altSchema = altSchemas.get(i);

            if (i > 0) {
                content.append(" | ");
            }

            content.append(visit(altSchema, (name == null ? "alternative-" : name + "-") + i));
        }

        return content.toString();
    }

    private String addPrimitive(String name, BuiltinRule builtinRule) {
        var rule = addRule(name, builtinRule.content);

        for (var dep : builtinRule.dependencies) {
            var depRule = PRIMITIVE_RULES.containsKey(dep) ? PRIMITIVE_RULES.get(dep) : FORMATTED_STRING_RULES.get(dep);

            if (depRule == null) {
                throw new IllegalArgumentException("Rule " + dep + " not known");
            }

            if (!rules.containsKey(dep)) {
                addPrimitive(dep, depRule);
            }
        }

        return rule;
    }

    private String generateObject(List<Map.Entry<String, JsonNode>> properties, Set<String> required, String name,
                                  JsonNode additionalProperties) {
        var propertyRules = new HashMap<String, String>();

        for (var property : properties) {
            var propertyKey = property.getKey();
            var propertySchema = property.getValue();
            var propertyValue = visit(propertySchema, (name == null ? "" : name + "-") + propertyKey);
            var propertyRuleName = (name == null ? "" : name + "-") + propertyKey + "-kv";
            var propertyRuleContent = formatLiteral("\\\"" + propertyKey + "\\\"") + " space \":\" space " + propertyValue;
            var propertyRule =  addRule(propertyRuleName, propertyRuleContent);
            propertyRules.put(propertyKey, propertyRule);
        }

        var requiredProperties = properties.stream()
                .map(Map.Entry::getKey)
                .filter(required::contains)
                .toList();

        var optionalProperties = properties.stream()
                .map(Map.Entry::getKey)
                .filter(k -> !required.contains(k))
                .collect(Collectors.toList());

        if (additionalProperties != null && (!additionalProperties.isBoolean() || additionalProperties.asBoolean())) {
            var subName = (name == null ? "" : name + "-") + "additional";

            var valueRule = additionalProperties.isObject()
                    ? visit(additionalProperties, subName + "-value")
                    : addPrimitive("value", PRIMITIVE_RULES.get("value"));

            var keyRule = properties.isEmpty()
                    ? addPrimitive("string", PRIMITIVE_RULES.get("string"))
                    : addRule(
                    subName + "-k",
                    generateNotStrings(properties.stream().map(Map.Entry::getKey).collect(Collectors.toSet()))
            );

            propertyRules.put("*",
                    addRule(subName + "-kv", keyRule + " \":\" space " + valueRule));
            optionalProperties.add("*");
        }

        var content = new StringBuilder("\"{\" space ");
        content.append(requiredProperties.stream().map(propertyRules::get)
                .collect(Collectors.joining(" \",\" space ")));

        if (!optionalProperties.isEmpty()) {
            content.append(" (");

            if (!requiredProperties.isEmpty()) {
                content.append(" \",\" space ( ");
            }

            content.append(optionalProperties.stream()
                    .map(k -> generateObjectRuleGetRecursiveRefs(
                            name, propertyRules,
                            optionalProperties.subList(optionalProperties.indexOf(k), optionalProperties.size()),
                            false
                    ))
                    .collect(Collectors.joining(" | ")));

            if (!requiredProperties.isEmpty()) {
                content.append(" )");
            }

            content.append(" )?");
        }

        content.append(" \"}\" space");
        return content.toString();
    }

    private String generateObjectRuleGetRecursiveRefs(
            String name, Map<String, String> propertyRules, List<String> properties, boolean firstIsOptional) {
        var headProperty = properties.get(0);
        var tailProperties = properties.subList(1, properties.size());
        var headPropertyRule = propertyRules.get(headProperty);
        var commaRef = "( \",\" space " + headPropertyRule + " )";

        var content = new StringBuilder();

        if (firstIsOptional) {
            content.append(commaRef);

            if (headProperty.equals("*")) {
                content.append("*");
            } else {
                content.append("?");
            }
        } else {
            content.append(headPropertyRule);

            if (headProperty.equals("*")) {
                content.append(" ").append(commaRef).append("*");
            }
        }

        if (!tailProperties.isEmpty()) {
            content.append(" ");
            var subruleName = (name == null ? "" : name + "-") + headProperty + "-rest";
            var subruleContent = generateObjectRuleGetRecursiveRefs(name, propertyRules, tailProperties, true);
            var subrule = addRule(subruleName, subruleContent);
            content.append(subrule);
        }

        return content.toString();
    }

    private String formatGrammar() {
        return rules.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(kv -> kv.getKey() + " ::= " + kv.getValue())
                .collect(Collectors.joining("\n"));
    }
}