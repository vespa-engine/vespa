-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
-- REGISTER $VESPA_HADOOP_JAR
REGISTER vespa-hadoop.jar
-- REGISTER parquet-pig-bundle-1.8.1.jar

-- Define Vespa query for retrieving blog posts
DEFINE  BlogPostRecommendations
        com.yahoo.vespa.hadoop.pig.VespaQuery(
            'query=http://ENDPOINT:8080/search/?user_id=<userid>&hits=100',
            'schema=rank:int,id:chararray,relevance:double,fields/post_id:chararray'
        );

-- Load test_set data from a local file
test_set = LOAD 'data/cv/test_set_exploded' AS (post_id:chararray, userid:chararray);
users = FOREACH test_set GENERATE userid;
users = FILTER users BY userid IS NOT null;
users = DISTINCT users;

users_limit = LIMIT users 10;

-- Run a set of queries against Vespa
recommendations = FOREACH users_limit GENERATE userid, 
                                               FLATTEN(BlogPostRecommendations(*)) AS (rank, id, relevance, post_id);
recommendations = FOREACH recommendations GENERATE userid, rank, post_id;

recommendations = FILTER recommendations BY rank IS NOT NULL AND post_id IS NOT NULL;                                              

-- Output recommendations
STORE recommendations INTO 'data/recommendations' USING PigStorage('\t', '-schema');
-- STORE recommendations INTO 'data/recommendations' USING org.apache.parquet.pig.ParquetStorer();
