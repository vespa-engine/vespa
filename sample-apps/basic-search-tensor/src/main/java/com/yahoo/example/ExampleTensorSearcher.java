// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.example;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.tensor.Tensor;

public class ExampleTensorSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {

        Object tensorProperty = query.properties().get("tensor");
        if (tensorProperty != null) {

            // Construct a Tensor object based on the query parameter
            Tensor tensor = Tensor.from(tensorProperty.toString());

            // Create a new rank feature using this tensor
            query.getRanking().getFeatures().put("query(tensor)", tensor);

            // Set the rank profile to use
            query.properties().set(new CompoundName("ranking"), "simple_tensor_ranking");
        }

        return execution.search(query);
    }

}
