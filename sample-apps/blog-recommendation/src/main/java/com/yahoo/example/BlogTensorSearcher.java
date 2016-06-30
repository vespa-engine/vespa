package com.yahoo.example;

import com.yahoo.prelude.query.IntItem;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.querytransform.QueryTreeUtil;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;

public class BlogTensorSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {

        Object userItemCfProperty = query.properties().get("user_item_cf");
        if (userItemCfProperty != null) {

            // Modify the query by restricting to blog_posts...
            query.getModel().setRestrict("blog_post");

            // ... that has a tensor field fed
            QueryTreeUtil.andQueryItemWithRoot(query, new IntItem(1, "has_user_item_cf"));

            // Modify the ranking by using the 'tensor' rank-profile (as defined in blog_post.sd)...
            query.properties().set(new CompoundName("ranking"), "tensor");

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

}
