val test_file_path = "data/cv/test_set_exploded"
val blog_recom_file_path = "data/recommendations"
val size_recommendation_list = 100

val sqlContext = new org.apache.spark.sql.SQLContext(sc)

val test_set = sc.textFile(test_file_path).
  map(_.split("\t")).map(p => (p(0).toString, p(1).toString)).
  toDF("post_id", "user_id")

val recommendations = sc.textFile(blog_recom_file_path).
  map(_.split("\t")).map(p => (p(0).toString, p(1).toString, p(2).toString)).
  toDF("user_id", "rank", "post_id")

// val recommendations = sqlContext.createDataFrame(Seq(
//   ("16966742", "5", "1009088"),
//   ("30463255", "10", "1044974")
// )).toDF("user_id", "rank", "post_id")

// join data
var joined_data = test_set.
  join(recommendations, 
       test_set("post_id") === recommendations("post_id") && 
        test_set("user_id") === recommendations("user_id")).
  select(test_set("post_id"), 
         test_set("user_id"), 
         recommendations("rank"))

// transform and add a column
joined_data = joined_data.withColumn("percentile", joined_data("rank")/size_recommendation_list)

val expected_percentile = joined_data.
  // groupBy($"user_id").
  groupBy().
  agg(sum($"percentile").as("sum_percentile"), 
      count($"post_id").as("number_read")).
  withColumn("expected_percentile", $"sum_percentile" / $"number_read")

expected_percentile.show()
