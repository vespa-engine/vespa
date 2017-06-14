import org.apache.spark.mllib.recommendation.ALS
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel
import org.apache.spark.mllib.recommendation.Rating
import scala.util.parsing.json.JSONObject

// Prepare data

val data_path = "data/original_data/trainPosts.json"

val sqlContext = new org.apache.spark.sql.SQLContext(sc)

val full_dataset = sqlContext.read.json(data_path)

var data = full_dataset.select($"post_id", explode($"likes").as("likes_flat"))
data = data.select($"likes_flat.uid".as("uid"), $"post_id")

data = data.filter("uid is not null and uid != '' and post_id is not null and post_id != ''")

val ratings = data.rdd.map(x => (x(0).toString, x(1).toString) match { 
  case (user, item) =>  Rating(user.toInt, item.toInt, 1)
})

// Train the model

val rank = 10
val numIterations = 10
val model = ALS.train(ratings, rank, numIterations, 0.01)

// Convert latent vectors from model to Vespa Tensor model

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

// Write user and item latent factors to disk

val product_features = model.productFeatures.map(x => writeModelFeaturesAsTensor(x, "post_id"))
product_features.saveAsTextFile("data/user_item_cf/product_features")
val user_features = model.userFeatures.map(x => writeModelFeaturesAsTensor(x, "user_id"))
user_features.saveAsTextFile("data/user_item_cf/user_features")




