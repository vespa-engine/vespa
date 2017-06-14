// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.demo;

import com.google.common.base.Splitter;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.response.DataList;
import com.yahoo.yolean.chain.Before;
import com.yahoo.yolean.chain.Provides;

import java.util.ArrayList;
import java.util.List;

/**
 * A processor which processes the incoming query property "terms", then checks
 * whether there are any smurfs, i.e. DemoData instances containing the string
 * {@link DemoComponent#SMURF}, and adds errors if that is the case.
 */
@Provides(AnnotatingProcessor.DemoProperty.NAME)
@Before(DataProcessor.DemoData.NAME)
public class AnnotatingProcessor extends Processor {

    public static class DemoProperty {

        public static final String NAME = "demo.property";
        public static final CompoundName NAME_AS_COMPOUND = new CompoundName(NAME);

        private final List<String> terms = new ArrayList<>();

        public void add(String term) {
            terms.add(term);
        }

        public List<String> terms() {
            return terms;
        }
    }

    private final DemoConfig defaultTermSet;

    public final CompoundName TERMS = new CompoundName("terms");

    private static final Splitter splitter = Splitter.on(' ').omitEmptyStrings();

    public AnnotatingProcessor(DemoConfig defaultTermSet) {
        this.defaultTermSet = defaultTermSet;
    }

    @Override
    public Response process(Request request, Execution execution) {
        Response response;
        List<?> d;
        DemoProperty p = new DemoProperty();
        String terms = request.properties().getString(TERMS);

        if (terms != null) {
            for (String s : splitter.split(terms)) {
                p.add(s);
            }
        } else {
            for (DemoConfig.Demo demo : defaultTermSet.demo()) {
                p.add(demo.term());
            }
        }
        request.properties().set(DemoProperty.NAME_AS_COMPOUND, p);
        response = execution.process(request);
        d = response.data().asList();
        traverse(d, response.data().request().errors());
        return response;
    }

    private boolean traverse(List<?> list, List<ErrorMessage> topLevelErrors) {
        boolean smurfFound = false;
        // traverse the tree in the response, and react to the known types
        for (Object data : list) {
            if (data instanceof DataList) {
                smurfFound = traverse(((DataList<?>) data).asList(),
                        topLevelErrors);
            } else if (data instanceof DataProcessor.DemoData) {
                DataProcessor.DemoData content = (DataProcessor.DemoData) data;
                if (DemoComponent.SMURF.equals(content.content())) {
                    topLevelErrors.add(new ErrorMessage("There's a smurf!"));
                    smurfFound = true;
                }
            }
            if (smurfFound) {
                break;
            }
        }
        return smurfFound;
    }

}
