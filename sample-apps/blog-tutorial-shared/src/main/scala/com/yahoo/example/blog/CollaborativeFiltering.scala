package com.yahoo.example.blog

import org.apache.spark.ml.recommendation.{ALS, ALSModel}
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.mllib.recommendation.Rating
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Row, DataFrame}
import org.apache.spark.sql.functions.{col, explode}

import scala.collection.mutable
import scala.util.parsing.json.JSONObject

class CollaborativeFiltering(val ss: SparkSession) {

  import ss.implicits._

  def loadTrainingIndicesIntoDataFrame(input_path: String) = {

    val ratings = ss.sparkContext.textFile(input_path)
      .map(_.split("\t"))
      .map(p => (p(0), p(1), 1))
      .toDF("post_id", "user_id", "label")
      .filter(col("post_id").notEqual("null"))
      .filter(col("user_id").notEqual("null"))
      .select(col("post_id").cast(IntegerType).as("post_id"),
              col("user_id").cast(IntegerType).as("user_id"),
              col("label").cast(IntegerType).as("label"))

    ratings

  }

  def loadDataIntoDataFrame(input_path: String): DataFrame = {

    val dataset = ss.read.json(input_path)

    val setOne = udf(() => 1)

    val ratings = dataset.select(col("post_id").cast(IntegerType).as("post_id"),
                                 explode(col("likes")).as("likes_flat"))
      .select(col("post_id"), col("likes_flat.uid").cast(IntegerType).as("user_id"))
      .withColumn("label", setOne())

    ratings

  }

  def loadDataIntoRating(input_path: String): RDD[Rating] = {

    val dataset: DataFrame = ss.read.json(input_path)

    val ratings = dataset.select(col("post_id"), explode(col("likes")).as("likes_flat"))
      .select(col("post_id"), col("likes_flat.uid").as("user_id"))
      .rdd.map {
      case Row(post_id: String,
      user_id: String) =>
        Rating(user_id.toInt, post_id.toInt, 1)
    }

    ratings

  }

  def run(input_path: String, rank: Int, numIterations: Int, lambda: Double): ALSModel = {

    // Loading and preparing the data
    val ratings = loadTrainingIndicesIntoDataFrame(input_path)

    // Fitting the model
    val model = new ALS()
      .setItemCol("post_id")
      .setRatingCol("label")
      .setUserCol("user_id")
      .setImplicitPrefs(true)
      .setAlpha(lambda)
      .setMaxIter(numIterations)
      .setRank(rank)
      .fit(ratings)

    model

  }

  def run_pipeline(input_path: String, numIterations: Int): ALSModel = {

    // Loading and preparing the data
    val ratings = loadTrainingIndicesIntoDataFrame(input_path)

    // Configure an ML pipeline, which consists of three stages: tokenizer, hashingTF, and lr.
    val collaborative_filtering = new ALS()
      .setItemCol("post_id")
      .setRatingCol("label")
      .setUserCol("user_id")
      .setMaxIter(numIterations)

    val paramGrid = new ParamGridBuilder()
      .addGrid(collaborative_filtering.rank, Array(10, 50, 100))
      .addGrid(collaborative_filtering.alpha, Array(0.001, 0.01, 0.1))
      .build()

    val cv = new CrossValidator()
      .setEstimator(collaborative_filtering)
      .setEvaluator(new RegressionEvaluator)
      .setEstimatorParamMaps(paramGrid)
      .setNumFolds(2)  // Use 3+ in practice

    // Run cross-validation, and choose the best set of parameters.
    val cvModel = cv.fit(ratings)

    cvModel.bestModel.asInstanceOf[ALSModel]

  }

}

object CollaborativeFiltering {

  def writeModelFeaturesAsTensor[T] (modelFeatures:(Int, mutable.WrappedArray[T]), id_string:String) = {

    val id = modelFeatures._1
    val latentVector = modelFeatures._2
    var latentVectorMap:Map[String,T] = Map()
    var output:Map[String,Any] = Map()

    for ( i <- latentVector.indices ){

      latentVectorMap += (("user_item_cf:" + i.toString, latentVector(i)))

    }

    output += ((id_string, id))
    output += (("user_item_cf", JSONObject(latentVectorMap)))

    JSONObject(output)

  }

  def writeFeaturesAsVespaTensorText(model: ALSModel, output_path: String): Unit ={

    model
      .itemFactors.rdd
      .map {
        case Row(id: Int, features: mutable.WrappedArray[Double]) => writeModelFeaturesAsTensor((id, features), "post_id")
      }
      .saveAsTextFile(output_path + "/product_features")
    model
      .userFactors.rdd
      .map {
        case Row(id: Int, features: mutable.WrappedArray[Double]) => writeModelFeaturesAsTensor((id, features), "user_id")
      }
      .saveAsTextFile(output_path + "/user_features")

  }

}
