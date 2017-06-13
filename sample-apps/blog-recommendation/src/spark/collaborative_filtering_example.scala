import org.apache.spark.mllib.recommendation.ALS
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel
import org.apache.spark.mllib.recommendation.Rating
import scala.util.parsing.json.JSONObject

// Load and parse the data
val data = sc.textFile("blog-recommendation/trainPostsFinal_user_item_cf")
val ratings = data.map(_.split('\t') match { case Array(user, item, rate) =>
  Rating(user.toInt, item.toInt, rate.toDouble)
})

// Build the recommendation model using ALS
val rank = 10
val numIterations = 10
val model = ALS.train(ratings, rank, numIterations, 0.01)

// Evaluate the model on rating data
val usersProducts = ratings.map { case Rating(user, product, rate) =>
  (user, product)
}
val predictions =
  model.predict(usersProducts).map { case Rating(user, product, rate) =>
    ((user, product), rate)
  }
val ratesAndPreds = ratings.map { case Rating(user, product, rate) =>
  ((user, product), rate)
}.join(predictions)
val MSE = ratesAndPreds.map { case ((user, product), (r1, r2)) =>
  val err = (r1 - r2)
  err * err
}.mean()
println("Mean Squared Error = " + MSE)

def writeModelFeaturesAsTensor (modelFeatures:(Int, Array[Double]), id_string:String) = {

  val id = modelFeatures._1
  val latentVector = modelFeatures._2
  var latentVectorMap:Map[String,Double] = Map()
  var output:Map[String,Any] = Map()

  for ( i <- 0 until latentVector.length ){

    latentVectorMap += (("user_item_cf:" + i.toString, latentVector(i)))      

  }

  output += ((id_string, id))
  output += (("user_item_cf", scala.util.parsing.json.JSONObject(latentVectorMap)))

  JSONObject(output)

}

val product_features = model.productFeatures.map(x => writeModelFeaturesAsTensor(x, "post_id"))
product_features.saveAsTextFile("blog-recommendation/user_item_cf/product_features")
val user_features = model.userFeatures.map(x => writeModelFeaturesAsTensor(x, "user_id"))
user_features.saveAsTextFile("blog-recommendation/user_item_cf/user_features")


