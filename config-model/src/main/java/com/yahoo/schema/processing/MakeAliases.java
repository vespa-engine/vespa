// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.Index;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Takes the aliases set on field by parser and sets them on correct Index or Attribute
 *
 * @author vegardh
 */
public class MakeAliases extends Processor {

    public MakeAliases(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        List<String> usedAliases = new ArrayList<>();
        for (SDField field : schema.allConcreteFields()) {
            for (Map.Entry<String, String> e : field.getAliasToName().entrySet()) {
                String alias = e.getKey();
                String name = e.getValue();
                String errMsg = "For " + schema + ": alias '" + alias + "' ";
                if (validate && schema.existsIndex(alias)) {
                    throw new IllegalArgumentException(errMsg + "is illegal since it is the name of an index.");
                }
                if (validate && schema.getAttribute(alias) != null) {
                    throw new IllegalArgumentException(errMsg + "is illegal since it is the name of an attribute.");
                }
                if (validate && usedAliases.contains(alias)) {
                    throw new IllegalArgumentException(errMsg + "specified more than once.");
                }
                usedAliases.add(alias);

                Index index = field.getIndex(name);
                Attribute attribute = field.getAttributes().get(name);
                if (index != null) {
                    index.addAlias(alias); // alias will be for index in this case, since it is the one used in a search
                } else if (attribute != null && ! field.doesIndexing()) {
                    attribute.getAliases().add(alias);
                } else {
                    index = new Index(name);
                    index.addAlias(alias);
                    field.addIndex(index);
                }
            }
        }
    }

}
