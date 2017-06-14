-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
REGISTER vespa-hadoop.jar

-- Create valid Vespa put operations
DEFINE VespaPutOperationDoc
       com.yahoo.vespa.hadoop.pig.VespaDocumentOperation(
            'operation=put',
            'docid=id:blog-recommendation:blog_post::<post_id>',
            'create-tensor-fields=user_item_cf'
       );

DEFINE VespaPutOperationUser
       com.yahoo.vespa.hadoop.pig.VespaDocumentOperation(
            'operation=put',
            'docid=id:blog-recommendation:user::<user_id>',
            'create-tensor-fields=user_item_cf'
       );

-- Transform tabular data to a Vespa document operation JSON format
DEFINE VespaStorage
       com.yahoo.vespa.hadoop.pig.VespaStorage();


data_doc = LOAD 'blog-recommendation/user_item_cf/product_features' USING JsonLoader('post_id:chararray, user_item_cf:[double]');
data_doc_for_feed = FOREACH data_doc GENERATE VespaPutOperationDoc(*);


data_user = LOAD 'blog-recommendation/user_item_cf/user_features' USING JsonLoader('user_id:chararray, user_item_cf:[double]');
data_user_for_feed = FOREACH data_user GENERATE VespaPutOperationUser(*);


-- Store into Vespa
STORE data_doc_for_feed INTO '$ENDPOINT' USING VespaStorage();
STORE data_user_for_feed INTO '$ENDPOINT' USING VespaStorage();




