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

-- join data
joined_data = JOIN test_indices BY (post_id, user_id), recommendations BY (post_id, user_id);
joined_data = FOREACH joined_data GENERATE 
  test_indices::post_id AS post_id, 
  test_indices::user_id AS user_id, 
  rank;

-- transform and add a column
joined_data = FOREACH joined_data 
  GENERATE post_id, 
           user_id, 
           rank, 
           (double)rank/(double)$NUMBER_RECOMMENDATIONS AS percentile;

grouped_data = GROUP joined_data BY user_id;
grouped_data = FOREACH grouped_data 
  GENERATE group AS user_id,
           SUM(joined_data.percentile) AS sum_percentile,
           COUNT(joined_data.post_id) AS number_read,
           (double)SUM(joined_data.percentile)/(double)COUNT(joined_data.post_id) AS expected_percentile;

STORE grouped_data INTO '$OUTPUT';