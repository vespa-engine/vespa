package com.yahoo.example.blog

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.scalatest.Matchers._
import org.scalatest._

class SplitFullSetIntoTrainAndTestSetsTest extends FunSuite with BeforeAndAfter {

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

  test("SplitFullSetIntoTrainAndTestSets should return an Array of DataFrame") {

    val file_path = getClass.getResource("/trainPostsSample.json")

    val splitter = new SplitFullSetIntoTrainAndTestSets(ss)

    val sets = splitter.run(input_file_path = file_path.toString,
      test_perc_stage1 = 0.05,
      test_perc_stage2 = 0.15,
      seed = 123)

    sets shouldBe a [Array[DataFrame]]

  }

}

