package com.yahoo.example;

import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Inspector;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.tensor.MapTensorBuilder;
import com.yahoo.tensor.Tensor;

import java.util.Iterator;
import java.util.Map;

public class UserProfileSearcher extends Searcher {

    public Result search(Query query, Execution execution) {

        Object userIdProperty = query.properties().get("user_id");
        if (userIdProperty != null) {

            // Retrieve user profile...
            Tensor userProfile = retrieveUserProfile(userIdProperty.toString(), execution);

            // ... and add user profile to query properties so BlogTensorSearcher can pick it up
            query.properties().set(new CompoundName("user_item_cf"), userProfile);

            if (query.isTraceable(9)) {
                String tensorRepresentation = userProfile != null ? userProfile.toString() : "";
                query.trace("Setting user profile to :" + tensorRepresentation, 9);
            }
        }

        return execution.search(query);
    }


    private Tensor retrieveUserProfile(String userId, Execution execution) {
        Query query = new Query();
        query.getModel().setRestrict("user");
        query.getModel().getQueryTree().setRoot(new WordItem(userId, "user_id"));
        query.setHits(1);

        SearchChain vespaChain = execution.searchChainRegistry().getComponent("vespa");
        Result result = new Execution(vespaChain, execution.context()).search(query);

        // This is needed to get the actual summary data
        execution.fill(result);

        Hit hit = getFirstHit(result);
        if (hit != null) {
            Object userItemCf = hit.getField("user_item_cf");
            if (userItemCf instanceof Inspectable) {
                return convertTensor((Inspectable) userItemCf);
            }
        }
        return null;
    }

    private Hit getFirstHit(Result result) {
        Iterator<Hit> hiterator = result.hits().deepIterator();
        return hiterator.hasNext() ? hiterator.next() : null;
    }

    private Tensor convertTensor(Inspectable field) {
        MapTensorBuilder tensorBuilder = new MapTensorBuilder();

        Inspector cells = field.inspect().field("cells");
        for (Inspector cell : cells.entries()) {
            MapTensorBuilder.CellBuilder cellBuilder = tensorBuilder.cell();

            Inspector address = cell.field("address");
            for (Map.Entry<String, Inspector> entry : address.fields()) {
                String dim = entry.getKey();
                String label = entry.getValue().asString();
                cellBuilder.label(dim, label);
            }

            Inspector value = cell.field("value");
            cellBuilder.value(value.asDouble());
        }
        return tensorBuilder.build();
    }

}
