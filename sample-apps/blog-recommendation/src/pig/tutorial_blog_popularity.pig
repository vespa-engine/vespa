-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
REGISTER '$VESPA_HADOOP_JAR'

-- UDF to create valid Vespa document operation in JSON format
DEFINE VespaUpdateOperationDoc
       com.yahoo.vespa.hadoop.pig.VespaDocumentOperation(
            'operation=update',
            'docid=id:blog-search:blog_post::<post_id>'
       );

-- UDF to send data to a Vespa endpoint
DEFINE VespaStorage
       com.yahoo.vespa.hadoop.pig.VespaStorage();

-- Load data from any source - here we load using JsonLoader
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

data = FILTER data BY likes IS NOT NULL;

data_likes = FOREACH data GENERATE 
  blog, 
  post_id, 
  blogname,
  FLATTEN(likes) AS (dt, uid);

-- data_likes_limit = LIMIT data_likes 10;

likes = FOREACH (GROUP data_likes ALL) 
     GENERATE COUNT(data_likes) as total_number;

blog_popularity = FOREACH (GROUP data_likes BY blog) GENERATE 
  group as blog,
  (double)COUNT(data_likes)/(double)likes.total_number AS popularity;

data_update = JOIN data_likes BY blog, blog_popularity BY blog;
data_update = FOREACH data_update GENERATE
  post_id, popularity;

-- Create valid Vespa put operations in JSON format
data_for_feed_json = FOREACH data_update GENERATE VespaUpdateOperationDoc(*);

-- Store into Vespa
STORE data_for_feed_json INTO '$ENDPOINT' USING VespaStorage();
