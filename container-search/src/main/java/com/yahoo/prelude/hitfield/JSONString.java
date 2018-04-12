// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.text.Utf8;
import com.yahoo.text.XML;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Type;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.slime.Slime;
import com.yahoo.slime.JsonDecoder;
import java.util.Iterator;

/**
 * A JSON wrapper. Contains XML-style rendering of a JSON structure.
 *
 * @author Steinar Knutsen
 */
public class JSONString implements Inspectable {

    private static final long serialVersionUID = -3929383619752472712L;
    private Inspector value;
    private String content;
    private boolean didInitContent = false;
    private Object parsedJSON;
    private boolean didInitJSON = false;

    public JSONString(Inspector value) {
        if (value == null) {
            throw new IllegalArgumentException("JSONString does not accept null value.");
        }
        this.value = value;
    }

    public Inspector inspect() {
        if (value == null) {
            JsonDecoder decoder = new JsonDecoder();
            Slime slime = decoder.decode(new Slime(), Utf8.toBytes(content));
            if (slime.get().field("error_message").valid() &&
                slime.get().field("partial_result").valid() &&
                slime.get().field("offending_input").valid())
            {
                // probably a json parse error...
                value = new Value.StringValue(content);
            } else if (slime.get().type() == com.yahoo.slime.Type.OBJECT ||
                       slime.get().type() == com.yahoo.slime.Type.ARRAY)
            {
                // valid json object or array
                value = new SlimeAdapter(slime.get());
            } else {
                // 'valid' json, but leaf value
                value = new Value.StringValue(content);
            }
        }
        return value;
    }

    private void initContent() {
        if (didInitContent) {
            return;
        }
        didInitContent = true;
        if (value.type() == Type.EMPTY) {
            content = "";
        } else if (value.type() == Type.STRING) {
            content = value.asString();
        } else {
            // This will be json, because we know there is Slime below
            content = value.toString();
        }
    }

    /**
     * @throws IllegalArgumentException Does not accept null content
     */
    public JSONString(String content) {
        if (content == null) {
            throw new IllegalArgumentException("JSONString does not accept null content.");
        }
        this.content = content;
        didInitContent = true;
    }

    public String toString() {
        if (value != null) {
            return renderFromInspector();
        }
        initContent();
        if (content.length() == 0) {
            return content;
        }
        initJSON();
        if (parsedJSON == null) {
            return content;
        } else if (parsedJSON.getClass() == JSONArray.class) {
            return render((JSONArray) parsedJSON);
        } else if (parsedJSON.getClass() == JSONObject.class) {
            return render((JSONObject) parsedJSON);
        } else {
            return content;
        }
    }

    public boolean fillWeightedSetItem(WeightedSetItem item) {
        initContent();
        initJSON();
        try {
            if (parsedJSON instanceof JSONArray) {
                JSONArray seq = (JSONArray)parsedJSON;
                for (int i = 0; i < seq.length(); i++) {
                    JSONArray wsi = seq.getJSONArray(i);
                    String name = (String)wsi.get(0);
                    Number weight = (Number) wsi.get(1);
                    item.addToken(name, weight.intValue());
                }
                return true;
            }
        } catch (JSONException | ClassCastException e) {
        }
        return false;
    }

    private void initJSON() {
        initContent();
        if (didInitJSON) {
            return;
        }
        didInitJSON = true;
        if (content.charAt(0) == '[') {
            try {
                parsedJSON = new JSONArray(content);
            } catch (JSONException e) {
                // System.err.println("bad json: "+e);
                return;
            }
        } else {
            try {
                parsedJSON = new JSONObject(content);
            } catch (JSONException e) {
                // System.err.println("bad json: "+e);
                return;
            }
        }
    }

    private static String render(JSONArray sequence) {
        return FieldRenderer.renderMapOrArray(new StringBuilder(), sequence, 2).toString();
    }

    private static String render(JSONObject structure) {
        return FieldRenderer.renderStruct(new StringBuilder(), structure, 2).toString();
    }

    private static abstract class FieldRenderer {

        protected static void indent(StringBuilder renderTarget, int nestingLevel) {
            for (int i = 0; i < nestingLevel; ++i) {
                renderTarget.append("  ");
            }
        }

        public static StringBuilder renderMapOrArray(StringBuilder renderTarget,
                                                     JSONArray sequence,
                                                     int nestingLevel)
        {
            if (sequence.length() == 0) return renderTarget;

            if (MapFieldRenderer.isMap(sequence)) {
                MapFieldRenderer.renderMap(renderTarget, sequence, nestingLevel + 1);
            } else {
                ArrayFieldRenderer.renderArray(renderTarget, sequence, nestingLevel + 1);
            }
            indent(renderTarget, nestingLevel);
            return renderTarget;
        }

        public static StringBuilder renderStruct(StringBuilder renderTarget, JSONObject object, int nestingLevel) {
            StructureFieldRenderer.renderStructure(renderTarget, object, nestingLevel + 1);
            indent(renderTarget, nestingLevel);
            return renderTarget;
        }

        public abstract void render(StringBuilder renderTarget, Object value, int nestingLevel);

        public abstract void closeTag(StringBuilder renderTarget, int nestingLevel, String closing);

        /** Returns a value from an object, or null if not found */
        protected static Object get(String field,JSONObject source) {
            try {
                return source.get(field);
            }
            catch (JSONException e) { // not found
                return null;
            }
        }

        protected static void renderValue(Object value,StringBuilder renderTarget,int nestingLevel) {
            if (value.getClass() == JSONArray.class) {
                renderMapOrArray(renderTarget, (JSONArray) value, nestingLevel);
            } else if (value instanceof Number) {
                NumberFieldRenderer.renderNumber(renderTarget, (Number) value);
            } else if (value.getClass() == String.class) {
                StringFieldRenderer.renderString(renderTarget, (String) value);
            } else if (value.getClass() == JSONObject.class) {
                renderStruct(renderTarget, (JSONObject) value, nestingLevel);
            } else {
                renderTarget.append(value.toString());
            }
        }

    }

    private static class MapFieldRenderer extends FieldRenderer {

        @Override
        public void render(StringBuilder renderTarget, Object value, int nestingLevel) {
            renderMap(renderTarget, (JSONArray) value, nestingLevel);
        }

        /** Returns true if the given JSON object contains a map - a list of pairs called "key" and "value" */
        private static boolean isMap(JSONArray array) {
            Object firstObject=get(0,array);
            if ( ! (firstObject instanceof JSONObject)) return false;
            JSONObject first=(JSONObject)firstObject;
            if (first.length()!=2) return false;
            if ( ! first.has("key")) return false;
            if ( ! first.has("value")) return false;
            return true;
        }

        public static void renderMap(StringBuilder renderTarget, JSONArray sequence, int nestingLevel) {
            int limit = sequence.length();
            if (limit == 0) return;
            for (int i = 0; i < limit; ++i)
                renderMapItem(renderTarget, (JSONObject)get(i,sequence), nestingLevel);
            renderTarget.append("\n");
        }

        public static void renderMapItem(StringBuilder renderTarget, JSONObject object, int nestingLevel) {
            renderTarget.append('\n');
            indent(renderTarget, nestingLevel);
            renderTarget.append("<item><key>");
            renderValue(get("key",object), renderTarget, nestingLevel);
            renderTarget.append("</key><value>");
            renderValue(get("value",object), renderTarget, nestingLevel);
            renderTarget.append("</value></item>");
        }

        /** Returns a value from an array, or null if it does not exist */
        private static Object get(int index,JSONArray source) {
            try {
                return source.get(index);
            }
            catch (JSONException e) { // not found
                return null;
            }
        }

        @Override
        public void closeTag(StringBuilder renderTarget, int nestingLevel, String closing) {
            indent(renderTarget, nestingLevel);
            renderTarget.append(closing);
        }
    }

    private static class StructureFieldRenderer extends FieldRenderer {
        @Override
        public void render(StringBuilder renderTarget, Object value, int nestingLevel) {
            renderStructure(renderTarget, (JSONObject) value, nestingLevel);
        }

        public static void renderStructure(StringBuilder renderTarget, JSONObject structure, int nestingLevel) {
            for (Iterator<?> i = structure.keys(); i.hasNext();) {
                String key = (String) i.next();
                Object value=get(key,structure);
                if (value==null) continue;
                renderTarget.append('\n');
                indent(renderTarget, nestingLevel);
                renderTarget.append("<struct-field name=\"").append(key).append("\">");
                renderValue(value, renderTarget, nestingLevel);
                renderTarget.append("</struct-field>");
            }
            renderTarget.append('\n');
        }

        @Override
        public void closeTag(StringBuilder renderTarget, int nestingLevel, String closing) {
            indent(renderTarget, nestingLevel);
            renderTarget.append(closing);
        }
    }

    private static class NumberFieldRenderer extends FieldRenderer {
        @Override
        public void render(StringBuilder renderTarget, Object value, int nestingLevel) {
            renderNumber(renderTarget, (Number) value);
        }

        public static void renderNumber(StringBuilder renderTarget, Number number) {
            renderTarget.append(number.toString());
        }

        @Override
        public void closeTag(StringBuilder renderTarget, int nestingLevel, String closing) {
            renderTarget.append(closing);
        }
    }

    private static class StringFieldRenderer extends FieldRenderer {
        @Override
        public void render(StringBuilder renderTarget, Object value, int nestingLevel) {
            renderString(renderTarget, (String) value);
        }

        public static void renderString(StringBuilder renderTarget, String value) {
            renderTarget.append(XML.xmlEscape(value, false));
        }

        @Override
        public void closeTag(StringBuilder renderTarget, int nestingLevel, String closing) {
            renderTarget.append(closing);
        }
    }

    private static class ArrayFieldRenderer extends FieldRenderer {
        protected static FieldRenderer structureFieldRenderer = new StructureFieldRenderer();
        protected static FieldRenderer stringFieldRenderer = new StringFieldRenderer();
        protected static FieldRenderer numberFieldRenderer = new NumberFieldRenderer();

        @Override
        public void render(StringBuilder renderTarget, Object value, int nestingLevel) {
            // Only for completeness
            renderArray(renderTarget, (JSONArray) value, nestingLevel);
        }

        public static void renderArray(StringBuilder renderTarget, JSONArray seq, int nestingLevel) {
            FieldRenderer renderer;
            int limit = seq.length();
            if (limit == 0) return;
            Object sniffer;
             try {
                sniffer = seq.get(0);
            } catch (JSONException e) {
                return;
            }
            if (sniffer.getClass() == JSONArray.class) {
                renderWeightedSet(renderTarget, seq, nestingLevel);
                return;
            } else if (sniffer.getClass() == JSONObject.class) {
                renderer = structureFieldRenderer;
            } else if (sniffer instanceof Number) {
                renderer = numberFieldRenderer;
            } else if (sniffer.getClass() == String.class) {
                renderer = stringFieldRenderer;
            } else {
                return;
            }
            renderTarget.append('\n');
            for (int i = 0; i < limit; ++i) {
                Object value;
                try {
                    value = seq.get(i);
                } catch (JSONException e) {
                    continue;
                }
                indent(renderTarget, nestingLevel);
                renderTarget.append("<item>");
                renderer.render(renderTarget, value, nestingLevel + 1);
                renderer.closeTag(renderTarget, nestingLevel, "</item>\n");
            }
        }

        protected static void renderWeightedSet(StringBuilder renderTarget,
                                                JSONArray seq, int nestingLevel) {
            int limit = seq.length();
            Object sniffer;
            FieldRenderer renderer;

            try {
                JSONArray first = seq.getJSONArray(0);
                sniffer = first.get(0);
            } catch (JSONException e) {
                return;
            }

            if (sniffer.getClass() == JSONObject.class) {
                renderer = structureFieldRenderer;
            } else if (sniffer instanceof Number) {
                renderer = numberFieldRenderer;
            } else if (sniffer.getClass() == String.class) {
                renderer = stringFieldRenderer;
            } else {
                return;
            }
            renderTarget.append('\n');
            for (int i = 0; i < limit; ++i) {
                JSONArray value;
                Object name;
                Number weight;

                try {
                    value = seq.getJSONArray(i);
                    name = value.get(0);
                    weight = (Number) value.get(1);

                } catch (JSONException e) {
                    continue;
                }
                indent(renderTarget, nestingLevel);
                renderTarget.append("<item weight=\"").append(weight).append("\">");
                renderer.render(renderTarget, name, nestingLevel + 1);
                renderer.closeTag(renderTarget, nestingLevel, "</item>\n");
            }
        }

        @Override
        public void closeTag(StringBuilder renderTarget, int nestingLevel, String closing) {
            indent(renderTarget, nestingLevel);
            renderTarget.append(closing);
        }
    }

    public String getContent() {
        initContent();
        return content;
    }

    public Object getParsedJSON() {
        initContent();
        if (parsedJSON == null) {
            initJSON();
        }
        return parsedJSON;
    }

    public void setParsedJSON(Object parsedJSON) {
        this.parsedJSON = parsedJSON;
    }

    public String renderFromInspector() {
        return XmlRenderer.render(new StringBuilder(), value).toString();
    }

}
