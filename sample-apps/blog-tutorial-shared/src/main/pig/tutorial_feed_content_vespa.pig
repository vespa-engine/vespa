-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
REGISTER '$VESPA_HADOOP_JAR'

-- UDF to create valid Vespa document operation in JSON format
DEFINE VespaPutOperationDoc
       com.yahoo.vespa.hadoop.pig.VespaDocumentOperation(
            'operation=put',
            'docid=id:blog-search:blog_post::<post_id>',
            'simple-array-fields=tags,categories'
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

-- Select fields that will be sent to Vespa.
-- This should follow blog_post.sd
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

-- Create valid Vespa put operations in JSON format
data_for_feed_json = FOREACH data_for_feed GENERATE VespaPutOperationDoc(*);

-- Store into Vespa
STORE data_for_feed_json INTO '$ENDPOINT' USING VespaStorage();
