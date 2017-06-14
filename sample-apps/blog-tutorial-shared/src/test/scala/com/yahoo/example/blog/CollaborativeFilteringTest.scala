// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.example.blog

import org.apache.spark.ml.recommendation.ALSModel
import org.apache.spark.sql.SparkSession
import org.scalatest.Matchers._
import org.scalatest._

class CollaborativeFilteringTest extends FunSuite with BeforeAndAfter {

  var ss: SparkSession = _

  before {

    ss = SparkSession
      .builder()
      .appName("Unit Test")
      .master("local[*]")
      .getOrCreate()

  }

  after {
    ss.stop()
  }

  test("run method returns a MatrixFactorizationModel with latent factors of size 10 to user and item") {

    val file_path = getClass.getResource("/trainingSetIndicesSample.txt")

    val cf = new CollaborativeFiltering(ss)

    val model = cf.run(
      input_path = file_path.toString,
      rank = 10,
      numIterations = 10,
      lambda = 0.01)

    model shouldBe a [ALSModel]

    val product_feature_array = model.itemFactors.first().getSeq(1)
    assertResult(10){product_feature_array.length}

    val user_feature_array = model.userFactors.first().getSeq(1)
    assertResult(10){user_feature_array.length}

  }

  test("run_pipeline method returns a MatrixFactorizationModel with latent factors of size 10 to user and item") {

    val file_path = getClass.getResource("/trainingSetIndicesSample.txt")

    val cf = new CollaborativeFiltering(ss)

    val model = cf.run_pipeline(input_path = file_path.toString, numIterations = 10)

    model shouldBe a [ALSModel]

    val product_feature_array = model.itemFactors.first().getSeq(1)
    assertResult(10){product_feature_array.length}

    val user_feature_array = model.userFactors.first().getSeq(1)
    assertResult(10){user_feature_array.length}

  }

}
