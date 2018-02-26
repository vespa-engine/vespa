// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.Search;
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

    public MakeAliases(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        List<String> usedAliases = new ArrayList<>();
        for (SDField field : search.allConcreteFields()) {
            for (Map.Entry<String, String> e : field.getAliasToName().entrySet()) {
                String alias = e.getKey();
                String name = e.getValue();
                String errMsg = "For search '" + search.getName() + "': alias '" + alias + "' ";
                if (validate && search.existsIndex(alias)) {
                    throw new IllegalArgumentException(errMsg + "is illegal since it is the name of an index.");
                }
                if (validate && search.getAttribute(alias) != null) {
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
