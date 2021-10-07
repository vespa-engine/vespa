-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
-- REGISTER vespa-hadoop.jar  -- Not needed in tests

-- Define Vespa query for retrieving blog posts
DEFINE  BlogPostRecommendations
        com.yahoo.vespa.hadoop.pig.VespaQuery(
            'query=$ENDPOINT/search?query=<userid>&hits=100',
            'rootnode=root/children/children',
            'schema=rank:int,id:chararray,relevance:double,fields/id:chararray,fields/content:chararray'
        );

-- Load data from a local file
users = LOAD 'src/test/resources/user_ids.csv' AS (userid:chararray);
users = FILTER users BY userid IS NOT null;

-- Run a set of queries against Vespa
recommendations = FOREACH users GENERATE userid, FLATTEN(BlogPostRecommendations(*));

-- Output recommendations
DUMP recommendations;
