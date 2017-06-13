-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
REGISTER '$VESPA_HADOOP_JAR'

-- Create valid Vespa put operations
DEFINE VespaPutOperationDoc
       com.yahoo.vespa.hadoop.pig.VespaDocumentOperation(
            'operation=put',
            'docid=id:blog-recommendation:blog_post::<post_id>',
            'create-tensor-fields=user_item_cf',
            'simple-array-fields=tags,categories'
       );

DEFINE VespaPutOperationUser
       com.yahoo.vespa.hadoop.pig.VespaDocumentOperation(
            'operation=put',
            'docid=id:blog-recommendation:user::<user_id>',
            'create-tensor-fields=user_item_cf',
            'simple-array-fields=has_read_items'
       );

-- Transform tabular data to a Vespa document operation JSON format
DEFINE VespaStorage
       com.yahoo.vespa.hadoop.pig.VespaStorage();

-- Load data 
data = LOAD '$DATA_PATH' USING 
  JsonLoader('date_gmt:chararray, 
              language:chararray, 
              author:chararray, 
              url:chararray, 
              title:chararray, 
              blog:chararray, 
              post_id:chararray, 
              tags:{T:(tag_name:chararray)}, 
              blogname:chararray, 
              date:chararray, 
              content:chararray, 
              categories:{T:(category_name:chararray)}, 
              likes:{T:(dt:chararray, uid:chararray)}');

data_for_feed = FOREACH data GENERATE 
  date_gmt, 
  language, 
  author, 
  url, 
  title, 
  blog, 
  post_id, 
  tags, 
  blogname,
  content, 
  categories;

-- Feed only blog posts that belong to test set
test_indices = LOAD '$TEST_INDICES' AS (post_id, user_id);
test_indices = FOREACH test_indices GENERATE post_id;
test_indices = DISTINCT test_indices;

test_data_for_feed = FOREACH (JOIN data_for_feed BY post_id, test_indices BY post_id) 
  GENERATE date_gmt AS date_gmt, 
           language AS language, 
           author AS author, 
           url AS url, 
           title AS title, 
           blog AS blog, 
           data_for_feed::post_id AS post_id, 
           tags AS tags, 
           blogname AS blogname,
           content AS content, 
           categories AS categories;

-- Load Blog post CF latent factors
data_doc = LOAD '$BLOG_POST_FACTORS' USING 
  JsonLoader('post_id:chararray, 
              user_item_cf:[double]');

-- Join data and latent factors
data_content_and_doc_tensor = JOIN test_data_for_feed BY post_id LEFT, data_doc BY post_id;
data_content_and_doc_tensor = FOREACH data_content_and_doc_tensor GENERATE
  date_gmt AS date_gmt, 
  language AS language, 
  author AS author, 
  url AS url, 
  title AS title, 
  blog AS blog, 
  test_data_for_feed::post_id as post_id, 
  tags AS tags, 
  blogname AS blogname,
  content AS content, 
  categories AS categories,
  user_item_cf AS user_item_cf,
  (user_item_cf IS NOT NULL ? 1 : 0) AS has_user_item_cf;

-- Generate valid Vespa JSON format
data_content_and_doc_tensor_feed = FOREACH data_content_and_doc_tensor GENERATE VespaPutOperationDoc(*);

-- Load User CF latent factors
data_user = LOAD '$USER_FACTORS' USING 
  JsonLoader('user_id:chararray, 
              user_item_cf:[double]');
data_user = FOREACH data_user GENERATE  
  user_id AS user_id,
  user_item_cf AS user_item_cf;
  
-- Articles already liked
data_likes = FOREACH data GENERATE post_id, FLATTEN(likes) AS (dt, uid); 

post_liked_per_user = GROUP data_likes BY uid;
post_liked_per_user = FOREACH post_liked_per_user GENERATE 
  group AS user_id, 
  data_likes.post_id AS has_read_items;

-- Join user data
data_user = JOIN post_liked_per_user BY user_id FULL, 
                 data_user BY user_id;

data_user = FOREACH data_user GENERATE 
  (post_liked_per_user::user_id IS NOT NULL ? post_liked_per_user::user_id : data_user::user_id) AS user_id,
  user_item_cf AS user_item_cf,
  (user_item_cf IS NOT NULL ? 1 : 0) AS has_user_item_cf,
  has_read_items AS has_read_items;

data_user = FILTER data_user BY user_id IS NOT NULL;

-- Generate valid Vespa JSON format
data_user_for_feed = FOREACH data_user GENERATE VespaPutOperationUser(*);

joint_content_tensors = UNION data_content_and_doc_tensor_feed, data_user_for_feed;

-- Store into Vespa
STORE joint_content_tensors INTO '$ENDPOINT' USING VespaStorage();




