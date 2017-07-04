# Vespa tutorial utility scripts

## Vespa Tutorial pt. 1

### From raw JSON to Vespa Feeding format

    $ python parse.py trainPosts.json > somefile.json

Parses JSON from the file trainPosts.json downloaded from Kaggle during the [blog search tutorial](https://git.corp.yahoo.com/pages/vespa/documentation/documentation/tutorials/blog-search.html) and format it according to Vespa Document JSON format.

    $ python parse.py -p trainPosts.json > somefile.json

Give it the flag "-p" or "--popularity", and the script also calculates and adds the field `popularity`, as introduced [in the tutorial](https://git.corp.yahoo.com/pages/vespa/documentation/documentation/tutorials/blog-search.html#blog-popularity-signal).

## Vespa Tutorial pt. 2

### Building and running the Spark script for calculating latent factors

1. Install the latest version of [Apache Spark](http://spark.apache.org/) and [sbt](http://www.scala-sbt.org/download.html).

2. Clone this repository and build the Spark script with `sbt package` (in the root directory of this repo).

3. Use the resulting jar file when running spark jobs included in the tutorials.

## Vespa Tutorial pt.3

Pre-computed data used throughout the tutorial will be made available shortly.

### Create Training Dataset

    $ ./generateDataset.R -d blog_job/user_item_cf_cv/product.json \
                          -u blog_job/user_item_cf_cv/user.json \
                          -t blog_job/training_and_test_indices/train.txt \
                          -o blog_job/nn_model/training_set.txt

### Train model with TensorFlow

Train the model with

    $ python vespaModel.py --product_features_file_path vespa_tutorial_data/user_item_cf_cv/product.json \
                           --user_features_file_path vespa_tutorial_data/user_item_cf_cv/user.json \
                           --dataset_file_path vespa_tutorial_data/nn_model/training_set.txt

Model parameters and summary statistics will be saved at folder ```runs/${start_time}``` with ```${start_time}``` representing the time you started to train the model.

Visualize the accuracy and loss metrics with

    $ tensorboard --logdir runs/1473845959/summaries/

**Note**: The folder ```1473845959``` depends on the time you start to train the model and will be different in your case.

### Export model parameters to Tensor Vespa format

```checkpoint_dir``` holds the folder that TensorFlow writes the learned model parameters (stored using protobuf) and ```output_dir``` is the folder that we will output the model parameters in
Vespa Tensor format.

    import vespaModel

    checkpoint_dir = "./runs/1473845959/checkpoints"
    output_dir = "application_package/constants"

    serializer = serializeVespaModel(checkpoint_dir, output_dir)
    serializer.serialize_to_disk(variable_name = "W_hidden", dimension_names = ['input', 'hidden'])
    serializer.serialize_to_disk(variable_name = "b_hidden", dimension_names = ['hidden'])
    serializer.serialize_to_disk(variable_name = "W_final", dimension_names = ['hidden', 'final'])
    serializer.serialize_to_disk(variable_name = "b_final", dimension_names = ['final'])

The python code containing the class ```serializeVespaModel``` can be found at: ```src/python/vespaModel.py```

### Offline evaluation

Query Vespa using the rank-profile ```tensor``` for users in the test set and return 100 blog post recommendations. Use those recommendations in the information contained in the test set to compute
metrics defined in the Tutorial pt. 2.

    pig -x local -f tutorial_compute_metric.pig \
      -param VESPA_HADOOP_JAR=vespa-hadoop.jar \
      -param TEST_INDICES=blog-job/training_and_test_indices/testing_set_ids \
      -param ENDPOINT=$(hostname):8080
      -param NUMBER_RECOMMENDATIONS=100
      -param RANKING_NAME=tensor
      -param OUTPUT=blog-job/cf-metric

Repeat the process, but now using the rank-profile ```nn_tensor```.

    pig -x local -f tutorial_compute_metric.pig \
      -param VESPA_HADOOP_JAR=vespa-hadoop.jar \
      -param TEST_INDICES=blog-job/training_and_test_indices/testing_set_ids \
      -param ENDPOINT=$(hostname):8080
      -param NUMBER_RECOMMENDATIONS=100
      -param RANKING_NAME=nn_tensor
      -param OUTPUT=blog-job/cf-metric
