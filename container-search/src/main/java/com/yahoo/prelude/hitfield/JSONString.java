// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;

/**
 * A JSON wrapper. Contains XML-style rendering of a JSON structure.
 *
 * @author Steinar Knutsen
 */
public class JSONString implements Inspectable {

    private Inspector value;
    private String content;
    private boolean didInitContent = false;

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

    @Override
    public String toString() {
        if (value != null) {
            return renderFromInspector();
        }
        initContent();
        if (content.length() == 0) {
            return content;
        }
        inspect();
        return renderFromInspector();
    }

    public boolean fillWeightedSetItem(WeightedSetItem set) {
        initContent();
        inspect();
        if (value.type() != Type.ARRAY) return false;
        for (Inspector item : value.entries()) {
            if (item.entryCount() != 2) return false;
            if (item.entry(0).type() != Type.STRING) return false;
            if (item.entry(1).type() != Type.LONG && item.entry(1).type() != Type.DOUBLE) return false;
            set.addToken(item.entry(0).asString(), (int) item.entry(1).asLong());
        }
        return true;
    }

    public String getContent() {
        initContent();
        return content;
    }

    public String renderFromInspector() {
        return XmlRenderer.render(new StringBuilder(), value).toString();
    }

}
