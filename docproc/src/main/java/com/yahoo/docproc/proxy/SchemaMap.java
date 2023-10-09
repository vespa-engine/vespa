// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.proxy;

import com.yahoo.collections.Pair;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.config.docproc.SchemamappingConfig.Fieldmapping;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Can be used to map field names from input doc into names used in a docproc that was
 * written with generic field names.
 * 
 * @author vegardh
 */
public class SchemaMap implements ConfigSubscriber.SingleSubscriber<SchemamappingConfig> {

    /** Map key. Doctype can be null, not the others. */
    class SchemaMapKey {

        private final String chain;
        private final String docproc;
        private final String doctype;
        private final String inDocument;

        public SchemaMapKey(String chain, String docproc, String doctype, String from) {
            this.chain = chain;
            this.docproc = docproc;
            this.doctype = doctype;
            this.inDocument = from;
            if (chain==null) throw new IllegalArgumentException("'chain' cannot be null in schema map.");
            if (docproc==null) throw new IllegalArgumentException("'docproc' cannot be null in schema map.");
            if (from==null) throw new IllegalArgumentException("'from' cannot be null in schema map.");
        }
        public String getChain() {
            return chain;
        }
        public String getDocproc() {
            return docproc;
        }
        public String getDoctype() {
            return doctype;
        }
        public String getInDocument() {
            return inDocument;
        }

        private boolean equalType(SchemaMapKey other) {
            if (doctype==null) return other.getDoctype()==null;
            return doctype.equals(other.getDoctype());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SchemaMapKey)) return false;
            SchemaMapKey other = (SchemaMapKey)obj;
            return other.getChain().equals(chain) &&
              other.getDocproc().equals(docproc) &&
              other.getInDocument().equals(inDocument) &&
              equalType(other);
        }
        @Override
        public int hashCode() {
            return chain.hashCode()+docproc.hashCode()+(doctype!=null?doctype.hashCode():0)+inDocument.hashCode();
        }

    }

    // (key->inProcessor),...

    private Map<SchemaMapKey, String> fields = new HashMap<>();

    void addMapping(String chain, String docproc, String doctype, String inDocument, String inProcessor) {
        fields.put(new SchemaMapKey(chain, docproc, doctype, inDocument), inProcessor);
    }

    @Override
    public void configure(SchemamappingConfig config) {
        if (config == null) {
            return;
        }
        fields.clear();
        for (Fieldmapping m: config.fieldmapping()) {
            SchemaMapKey key = new SchemaMapKey(m.chain(), m.docproc(), ("".equals(m.doctype())?null:m.doctype()), m.indocument());
            fields.put(key, m.inprocessor());
        }
    }

    /**
     * The map for a given chain,docproc:
     * "Reverses" the direction, this is the mapping a docproc should do when a
     * doc comes in. The doctype is null if not given in map.
     *
     * @return (doctype,inProcessor)→inDocument
     */
    public Map<Pair<String,String>, String> chainMap(String chain, String docproc) {
        Map<Pair<String, String>, String> ret = new HashMap<>();
        for (Entry<SchemaMapKey, String> e : fields.entrySet()) {
            SchemaMapKey key = e.getKey();
            if (key.getChain().equals(chain) && key.getDocproc().equals(docproc)) {
                // Reverse direction here
                ret.put(new Pair<>(key.getDoctype(),e.getValue()), key.getInDocument());
            }
        }
        return ret;
    }

}
