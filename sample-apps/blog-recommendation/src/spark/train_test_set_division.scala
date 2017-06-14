import org.apache.spark.sql.functions.udf

// Inputs
val input_file_path = "data/original_data/trainPosts.json"
val test_perc_stage1 = 0.05
val test_perc_stage2 = 0.15
val training_file_path = "data/cv/training_set_exploded"
val test_file_path = "data/cv/test_set_exploded"
val seed = 123

val sqlContext = new org.apache.spark.sql.SQLContext(sc)

// Load full dataset
val full_dataset = sqlContext.read.json(input_file_path)
val full_dataset_simple = full_dataset.select($"post_id", size($"likes").as("number_likes"), $"likes")

// Set some blog posts aside to be present only on the test set 
var sets = full_dataset_simple.randomSplit(Array(1 - test_perc_stage1, test_perc_stage1), seed)

var training_set = sets(0)
val training_set_null = training_set.filter("number_likes == 0")
var training_set_exploded = training_set.select($"post_id", explode($"likes").as("likes_flat"))
training_set_exploded = training_set_exploded.select("post_id", "likes_flat.uid")

var test_set = sets(1)
val test_set_null = test_set.filter("number_likes == 0")
var test_set_exploded = test_set.select($"post_id", explode($"likes").as("likes_flat"))
test_set_exploded = test_set_exploded.select("post_id", "likes_flat.uid")

// randomly move some (post_id, uid) from training set to test set
sets = training_set_exploded.randomSplit(Array(1 - test_perc_stage2, test_perc_stage2), seed)

training_set_exploded = sets(0)

var additional_test_set_exploded = sets(1)
test_set_exploded = test_set_exploded.unionAll(additional_test_set_exploded)

// concatenate exploded set with null set
val getNull = udf(() => None: Option[String])
training_set_exploded = training_set_exploded.unionAll(training_set_null.select("post_id").withColumn("uid", getNull()))
test_set_exploded = test_set_exploded.unionAll(test_set_null.select("post_id").withColumn("uid", getNull()))

// Write to disk
training_set_exploded.rdd.map(x => x(0) + "\t" + x(1)).saveAsTextFile(training_file_path)
test_set_exploded.rdd.map(x => x(0) + "\t" + x(1)).saveAsTextFile(test_file_path)
