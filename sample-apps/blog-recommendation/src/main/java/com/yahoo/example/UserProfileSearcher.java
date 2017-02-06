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
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;
import java.util.Map;

public class UserProfileSearcher extends Searcher {

    public Result search(Query query, Execution execution) {
        Object userIdProperty = query.properties().get("user_id");
        if (userIdProperty != null) {
            Hit userProfile = retrieveUserProfile(userIdProperty.toString(), execution);
            if (userProfile != null) {
                addUserProfileTensorToQuery(query, userProfile);
                addReadItemsToQuery(query, userProfile);
            }
        }
        return execution.search(query);
    }


    private Hit retrieveUserProfile(String userId, Execution execution) {
        Query query = new Query();
        query.getModel().setRestrict("user");
        query.getModel().getQueryTree().setRoot(new WordItem(userId, "user_id"));
        query.setHits(1);

        SearchChain vespaChain = execution.searchChainRegistry().getComponent("vespa");
        Result result = new Execution(vespaChain, execution.context()).search(query);

        execution.fill(result); // this is needed to get the actual summary data

        Iterator<Hit> hiterator = result.hits().deepIterator();
        return hiterator.hasNext() ? hiterator.next() : null;
    }

    private void addReadItemsToQuery(Query query, Hit userProfile) {
        Object readItems = userProfile.getField("has_read_items");
        if (readItems != null && readItems instanceof Inspectable) {
            query.properties().set(new CompoundName("has_read_items"), readItems);
        }
    }

    private void addUserProfileTensorToQuery(Query query, Hit userProfile) {
        Object userItemCf = userProfile.getField("user_item_cf");
        if (userItemCf != null && userItemCf instanceof Inspectable) {
            Tensor.Builder tensorBuilder = Tensor.Builder.of(new TensorType.Builder().indexed("user_item_cf", 10).build());
            Inspector cells = ((Inspectable)userItemCf).inspect().field("cells");
            for (Inspector cell : cells.entries()) {
                Tensor.Builder.CellBuilder cellBuilder = tensorBuilder.cell();

                Inspector address = cell.field("address");
                for (Map.Entry<String, Inspector> entry : address.fields()) {
                    String dim = entry.getKey();
                    String label = entry.getValue().asString();
                    cellBuilder.label(dim, label);
                }

                Inspector value = cell.field("value");
                cellBuilder.value(value.asDouble());
            }
            query.properties().set(new CompoundName("user_item_cf"), tensorBuilder.build());
        }
    }
}
