// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.example.blog

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.functions._

class SplitFullSetIntoTrainAndTestSets(val ss: SparkSession) {

  private def loadAndSimplifyFullDataset(input_file_path: String): DataFrame = {

    // Load full dataset
    val full_dataset = ss.read.json(input_file_path)
    val full_dataset_simple = full_dataset.select(col("post_id"), size(col("likes")).as("number_likes"), col("likes"))

    full_dataset_simple

  }

  private def splitSimplifiedDatasetIntoTrainAndTestSets(full_dataset_simple: DataFrame,
                                                         test_perc_stage1: Double,
                                                         test_perc_stage2: Double,
                                                         seed: Int): Array[DataFrame] = {

    // Set some blog posts aside to be present only on the test set
    var sets = full_dataset_simple.randomSplit(Array(1 - test_perc_stage1, test_perc_stage1), seed)

    val training_set = sets(0)
    val training_set_null = training_set.filter("number_likes = 0")
    var training_set_exploded = training_set.select(col("post_id"), explode(col("likes")).as("likes_flat"))
    training_set_exploded = training_set_exploded.select("post_id", "likes_flat.uid")

    val test_set = sets(1)
    val test_set_null = test_set.filter("number_likes = 0")
    var test_set_exploded = test_set.select(col("post_id"), explode(col("likes")).as("likes_flat"))
    test_set_exploded = test_set_exploded.select("post_id", "likes_flat.uid")

    // randomly move some (post_id, uid) from training set to test set
    sets = training_set_exploded.randomSplit(Array(1 - test_perc_stage2, test_perc_stage2), seed)

    training_set_exploded = sets(0)

    val additional_test_set_exploded = sets(1)
    test_set_exploded = test_set_exploded.union(additional_test_set_exploded)

    // concatenate exploded set with null set
    val getNull = udf(() => None: Option[String])
    training_set_exploded = training_set_exploded.union(training_set_null.select("post_id").withColumn("uid", getNull()))
    test_set_exploded = test_set_exploded.union(test_set_null.select("post_id").withColumn("uid", getNull()))

    Array(training_set_exploded, test_set_exploded)

  }

  def run(input_file_path: String, test_perc_stage1: Double, test_perc_stage2:Double, seed: Int): Array[DataFrame] = {

    val full_dataset_simple = loadAndSimplifyFullDataset(input_file_path)

    splitSimplifiedDatasetIntoTrainAndTestSets(full_dataset_simple,
      test_perc_stage1,
      test_perc_stage2,
      seed)

  }

}

object SplitFullSetIntoTrainAndTestSets {

  def writeTrainAndTestSetsIndices(train_and_test_sets: Array[DataFrame], output_path: String): Unit = {

    val training_set_exploded = train_and_test_sets(0)
    val test_set_exploded = train_and_test_sets(1)

    // Write to disk
    training_set_exploded.rdd.map(x => x(0) + "\t" + x(1)).saveAsTextFile(output_path + "/training_set_ids")
    test_set_exploded.rdd.map(x => x(0) + "\t" + x(1)).saveAsTextFile(output_path + "/testing_set_ids")

  }

}
