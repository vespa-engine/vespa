-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
REGISTER $VESPA_HADOOP_JAR

DEFINE  BlogPostRecommendations
        com.yahoo.vespa.hadoop.pig.VespaQuery(
            'query=http://$ENDPOINT/search/?user_id=<user_id>&hits=$NUMBER_RECOMMENDATIONS&ranking=$RANKING_NAME',
            'schema=rank:int,id:chararray,relevance:double,fields/post_id:chararray'
        );

-- Load test_set data from a local file
test_indices = LOAD '$TEST_INDICES' AS (post_id:chararray, user_id:chararray);
users = FOREACH test_indices GENERATE user_id;
users = FILTER users BY user_id IS NOT null;
users = DISTINCT users;

-- Run a set of queries against Vespa
recommendations = FOREACH users GENERATE user_id, 
                                         FLATTEN(BlogPostRecommendations(*)) AS (rank, id, relevance, post_id);
recommendations = FOREACH recommendations GENERATE user_id, rank, post_id;
recommendations = FILTER recommendations BY rank IS NOT NULL AND post_id IS NOT NULL;                                              

STORE recommendations INTO '$OUTPUT';
