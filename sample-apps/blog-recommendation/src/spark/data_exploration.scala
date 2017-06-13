// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// sc is an existing SparkContext.
val sqlContext = new org.apache.spark.sql.SQLContext(sc)

val original_train_post_path = "blog-recommendation-support/data/original_data/trainPosts.json"
val original_train_post_thin_path = "blog-recommendation-support/data/original_data/trainPostsThin.json"
val original_test_post_thin_path = "blog-recommendation-support/data/original_data/testPostsThin.json"

val original_train_post = sqlContext.read.json(original_train_post_path)
val original_train_post_thin = sqlContext.read.json(original_train_post_thin_path)
val original_test_post_thin = sqlContext.read.json(original_test_post_thin_path)

val count_original_train = original_train_post.count()
val count_original_train_thin = original_train_post_thin.count()
val count_original_test_thin = original_test_post_thin.count()

// The inferred schema can be visualized using the printSchema() method.
original_train_post.printSchema()
original_train_post_thin.printSchema()
original_test_post_thin.printSchema()

// No intersection between train and test data
original_train_post_thin.join(original_test_post_thin, original_train_post_thin("post_id") == original_test_post_thin("post_id")).count(2)

// original_train_minimal_df
var original_train_minimal_df = original_train_post.select($"date_gmt", $"post_id", size($"likes").as("number_likes"), $"likes")
// no duplicate post_id
original_train_minimal_df.select("post_id").dropDuplicates().count() - original_train_minimal_df.select("post_id").count()

// CHECK THIS DECISION - I SHOULD NOT EXLUDE POST_ID WITH ZERO LIKES
// OTHERWISE THERE WILL BE NO DOCUMENT IN THE TEST SET THAT NO ONE HAS LIKED,
// WHICH MAKES THE EXERCISE MUCH EASIER
// only post_id with at least one like
// original_train_minimal_df = original_train_minimal_df.filter("number_likes > 0")

// Set some post_id aside to be present only on the test set 
var sets = original_train_minimal_df.randomSplit(Array(0.95, 0.05), 123)

var training_set = sets(0)
var test_set = sets(1)

// flat dataframe so that each line is a combination of post_id and user
training_set = training_set.select($"post_id", explode($"likes").as("likes_flat"))
training_set = training_set.select("post_id", "likes_flat.uid")

test_set = test_set.select($"post_id", explode($"likes").as("likes_flat"))
test_set = test_set.select("post_id", "likes_flat.uid")

// randomly move some (post_id, uid) from training set to test set
sets = training_set.randomSplit(Array(0.85, 0.15), 123)

training_set = sets(0)
var additional_test_set = sets(1)

// concatenate test_set and additional_test_set
test_set = test_set.unionAll(additional_test_set)

// see number of likes distribution
val like_dist = original_train_minimal_df.groupBy("number_likes").count().orderBy(asc("number_likes")).collect()
like_dist.map(println)




