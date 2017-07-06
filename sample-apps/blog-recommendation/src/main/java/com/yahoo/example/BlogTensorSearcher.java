// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.example;

import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Inspector;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.querytransform.QueryTreeUtil;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;

import java.util.ArrayList;
import java.util.List;

public class BlogTensorSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {

        Object userItemCfProperty = query.properties().get("user_item_cf");
        if (userItemCfProperty != null) {

            // Modify the query by restricting to blog_posts...
            query.getModel().setRestrict("blog_post");

            // ... that has a tensor field fed and does not contain already read items.
            NotItem notItem = new NotItem();
            notItem.addItem(new IntItem(1, "has_user_item_cf"));
            for (String item : getReadItems(query)) {
                notItem.addItem(new WordItem(item, "post_id"));
            }
            QueryTreeUtil.andQueryItemWithRoot(query, notItem);

            // Modify the ranking by using the 'tensor' rank-profile (as defined in blog_post.sd)...
            if (query.properties().get("ranking") == null) {
                query.properties().set(new CompoundName("ranking"), "tensor");
            }

            // ... and setting 'query(user_item_cf)' used in that rank-profile
            query.getRanking().getFeatures().put("query(user_item_cf)", toTensor(userItemCfProperty));
        }

        return execution.search(query);
    }

    private Tensor toTensor(Object tensor) {
        if (tensor instanceof Tensor) {
            return (Tensor) tensor;
        }
        return Tensor.from(tensor.toString());
    }

    private List<String> getReadItems(Query query) {
        List<String> items = new ArrayList<>();
        Object readItems = query.properties().get("has_read_items");
        if (readItems instanceof Inspectable) {
            for (Inspector entry : ((Inspectable)readItems).inspect().entries()) {
                items.add(entry.asString());
            }
        }
        return items;
    }

}
