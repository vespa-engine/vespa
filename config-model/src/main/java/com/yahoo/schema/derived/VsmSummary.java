// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.Schema;
import com.yahoo.schema.document.GeoPos;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.config.search.vsm.VsmsummaryConfig;

import java.util.*;

/**
 * Vertical streaming matcher summary specification
 *
 * @author bratseth
 */
public class VsmSummary extends Derived implements VsmsummaryConfig.Producer {

    private final Map<SummaryField, List<String>> summaryMap = new java.util.LinkedHashMap<>(1);

    public VsmSummary(Schema schema) {
        derive(schema);
    }

    @Override
    protected void derive(Schema schema) {
        // Use the default class, as it is the superset
        derive(schema, schema.getSummary("default"));
    }

    private void derive(Schema schema, DocumentSummary documentSummary) {
        if (documentSummary == null) return;
        for (SummaryField summaryField : documentSummary.getSummaryFields().values()) {
            List<String> from = toStringList(summaryField.sourceIterator());

            if (doMapField(schema, summaryField)) {
                SDField sdField = schema.getConcreteField(summaryField.getName());
                if (sdField != null && GeoPos.isAnyPos(sdField)) {
                    summaryMap.put(summaryField, Collections.singletonList(summaryField.getName()));
                } else {
                    summaryMap.put(summaryField, from);
                }
            }
        }
    }

    /**
     * Don't include field in map if sources are the same as the struct sub fields for the SDField.
     * But do map if not all do summarying.
     * Don't map if not struct either.
     * @param summaryField a {@link SummaryField}
     */
    private boolean doMapField(Schema schema, SummaryField summaryField) {
        SDField sdField = schema.getConcreteField(summaryField.getName());
        SDDocumentType document = schema.getDocument();
        if (sdField==null || ((document != null) && (document.getField(summaryField.getName()) == sdField))) {
            return true;
        }
        if (summaryField.getVsmCommand().equals(SummaryField.VsmCommand.FLATTENJUNIPER)) {
            return true;
        }
        if (!sdField.usesStructOrMap()) {
            return !(sdField.getName().equals(summaryField.getName()));
        }
        if (summaryField.getSourceCount()==sdField.getStructFields().size()) {
            for (SummaryField.Source source : summaryField.getSources()) {
                if (!sdField.getStructFields().contains(new SDField(schema.getDocument(), source.getName(), sdField.getDataType()))) { // equals() uses just name
                    return true;
                }
                if (sdField.getStructField(source.getName())!=null && !sdField.getStructField(source.getName()).doesSummarying()) {
                    return true;
                }
            }
            // The sources in the summary field are the same as the sub-fields in the SD field.
            // All sub fields do summarying.
            // Don't map.
            return false;
        }
        return true;
    }

    private List<String> toStringList(Iterator<SummaryField.Source> i) {
        List<String> ret = new ArrayList<>();
        while (i.hasNext()) {
            ret.add(i.next().getName());
        }
        return ret;
    }

    @Override
    public String getDerivedName() {
        return "vsmsummary";
    }

    @Override
    public void getConfig(VsmsummaryConfig.Builder vB) {
        for (Map.Entry<SummaryField, List<String>> entry : summaryMap.entrySet()) {
            VsmsummaryConfig.Fieldmap.Builder fmB = new VsmsummaryConfig.Fieldmap.Builder().summary(entry.getKey().getName());
            for (String field : entry.getValue()) {
                fmB.document(new VsmsummaryConfig.Fieldmap.Document.Builder().field(field));
            }
            fmB.command(VsmsummaryConfig.Fieldmap.Command.Enum.valueOf(entry.getKey().getVsmCommand().toString()));
            vB.fieldmap(fmB);
        }
    }
    
}
