// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.example.blog

import org.apache.spark.sql.SparkSession

object BlogRecommendationApp {
  val usage = """
    Usage: spark-submit \
                |  --class "BlogRecommendationApp" \
                |  --master local[4] \
                |  JAR_FILE
                |  --task task_command [TASK RELATED OPTIONS]

           spark-submit \
                |  --class "BlogRecommendationApp" \
                |  --master local[4] \
                |  JAR_FILE
                |  --task collaborative_filtering
                |  --input_file path
                |  --rank value
                |  --numIterations value
                |  --lambda value
                |  --output_path path

           spark-submit \
                |  --class "BlogRecommendationApp" \
                |  --master local[4] \
                |  JAR_FILE
                |  --task collaborative_filtering_cv
                |  --input_file path
                |  --numIterations value
                |  --output_path path
                |

           spark-submit \
                |  --class "BlogRecommendationApp" \
                |  --master local[4] \
                |  JAR_FILE
                |  --task split_set
                |  --input_file path
                |  --test_perc_stage1 value
                |  --test_perc_stage2 value
                |  --seed value
                |  --output_path path
              """

  private val COLLABORATIVE_FILTERING = "collaborative_filtering"
  private val COLLABORATIVE_FILTERING_CV = "collaborative_filtering_cv"
  private val SPLIT_SET_INTO_TRAIN_AND_TEST = "split_set"

  type OptionMap = Map[Symbol, Any]

  def main(args: Array[String]) {

    val options = parseCommandLineOptions(args)
    val task_name = options('task).toString

    task_name match {
      case COLLABORATIVE_FILTERING => CollaborativeFilteringExample(options)
      case COLLABORATIVE_FILTERING_CV => CollaborativeFilteringCV(options)
      case SPLIT_SET_INTO_TRAIN_AND_TEST => SplitSetIntoTrainingAndTestSets(options)
    }

  }

  private def SplitSetIntoTrainingAndTestSets(options: OptionMap) = {

    val spark = SparkSession
      .builder()
      .appName("Split Full Data Into Train and Test Sets")
      .getOrCreate()

    val splitter = new SplitFullSetIntoTrainAndTestSets(spark)

    val sets = splitter.run(input_file_path = options('input_file).toString,
      test_perc_stage1 = options('test_perc_stage1).toString.toDouble,
      test_perc_stage2 = options('test_perc_stage2).toString.toDouble,
      seed = options('seed).toString.toInt)

    SplitFullSetIntoTrainAndTestSets.writeTrainAndTestSetsIndices(sets, options('output_path).toString)

  }

  private def CollaborativeFilteringExample(options: OptionMap) = {

    // TODO: Check if output_path already exist

    val spark = SparkSession
      .builder()
      .appName("Collaborative Filtering")
      .getOrCreate()

    val cf = new CollaborativeFiltering(spark)

    val model = cf.run(
      input_path = options('input_file).toString,
      rank = options('rank).toString.toInt,
      numIterations = options('num_iterations).toString.toInt,
      lambda = options('lambda).toString.toDouble)

    CollaborativeFiltering.writeFeaturesAsVespaTensorText(model, options('output_path).toString)

  }

  private def CollaborativeFilteringCV(options: OptionMap) = {

    // TODO: Check if output_path already exist

    val spark = SparkSession
      .builder()
      .appName("Collaborative Filtering CV")
      .getOrCreate()

    val cf = new CollaborativeFiltering(spark)

    val model = cf.run_pipeline(
      input_path = options('input_file).toString,
      numIterations = options('num_iterations).toString.toInt)

    CollaborativeFiltering.writeFeaturesAsVespaTensorText(model, options('output_path).toString)

  }

  private def parseCommandLineOptions(args: Array[String]): OptionMap = {

    def findTask(list: List[String]) : String = {
      list match {
        case Nil => println("Please, define a valid task" + "\n" + usage)
          sys.exit(1)
        case "--task" :: value :: tail =>
          value
        case option :: tail => findTask(tail)
      }
    }

    def ParseCollaborativeFilteringOptions(map : OptionMap, list: List[String]) : OptionMap = {
      list match {
        case Nil => map
        case "--input_file" :: value :: tail =>
          ParseCollaborativeFilteringOptions(map ++ Map('input_file -> value.toString), tail)
        case "--rank" :: value :: tail =>
          ParseCollaborativeFilteringOptions(map ++ Map('rank -> value.toInt), tail)
        case "--numIterations" :: value :: tail =>
          ParseCollaborativeFilteringOptions(map ++ Map('num_iterations -> value.toInt), tail)
        case "--lambda" :: value :: tail =>
          ParseCollaborativeFilteringOptions(map ++ Map('lambda -> value.toDouble), tail)
        case "--output_path" :: value :: tail =>
          ParseCollaborativeFilteringOptions(map ++ Map('output_path -> value.toString), tail)
        case option :: tail =>
          ParseCollaborativeFilteringOptions(map, tail)
      }
    }

    def ParseCollaborativeFilteringCVOptions(map : OptionMap, list: List[String]) : OptionMap = {
      list match {
        case Nil => map
        case "--input_file" :: value :: tail =>
          ParseCollaborativeFilteringOptions(map ++ Map('input_file -> value.toString), tail)
        case "--numIterations" :: value :: tail =>
          ParseCollaborativeFilteringOptions(map ++ Map('num_iterations -> value.toInt), tail)
        case "--output_path" :: value :: tail =>
          ParseCollaborativeFilteringOptions(map ++ Map('output_path -> value.toString), tail)
        case option :: tail =>
          ParseCollaborativeFilteringOptions(map, tail)
      }
    }

    def ParseSplitSetOptions(map : OptionMap, list: List[String]) : OptionMap = {
      list match {
        case Nil => map
        case "--input_file" :: value :: tail =>
          ParseSplitSetOptions(map ++ Map('input_file -> value.toString), tail)
        case "--test_perc_stage1" :: value :: tail =>
          ParseSplitSetOptions(map ++ Map('test_perc_stage1 -> value.toDouble), tail)
        case "--test_perc_stage2" :: value :: tail =>
          ParseSplitSetOptions(map ++ Map('test_perc_stage2 -> value.toDouble), tail)
        case "--seed" :: value :: tail =>
          ParseSplitSetOptions(map ++ Map('seed -> value.toInt), tail)
        case "--output_path" :: value :: tail =>
          ParseSplitSetOptions(map ++ Map('output_path -> value.toString), tail)
        case option :: tail =>
          ParseSplitSetOptions(map , tail)
      }
    }

    if (args.length == 0) println(usage)
    val arglist = args.toList

    val task_name = findTask(arglist)

    val options = task_name match {
      case COLLABORATIVE_FILTERING => ParseCollaborativeFilteringOptions(Map('task -> task_name), arglist)
      case COLLABORATIVE_FILTERING_CV => ParseCollaborativeFilteringCVOptions(Map('task -> task_name), arglist)
      case SPLIT_SET_INTO_TRAIN_AND_TEST => ParseSplitSetOptions(Map('task -> task_name), arglist)
    }

    options

  }

}


