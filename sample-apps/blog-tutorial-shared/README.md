# Vespa tutorial utility scripts

## From raw JSON to Vespa Feeding format

    $ python parse.py trainPosts.json > somefile.json

Parses JSON from the file trainPosts.json downloaded from Kaggle during the [blog search tutorial](https://git.corp.yahoo.com/pages/vespa/documentation/documentation/tutorials/blog-search.html) and format it according to Vespa Document JSON format.

    $ python parse.py -p trainPosts.json > somefile.json
    
Give it the flag "-p" or "--popularity", and the script also calculates and adds the field `popularity`, as introduced [in the tutorial](https://git.corp.yahoo.com/pages/vespa/documentation/documentation/tutorials/blog-search.html#blog-popularity-signal).

## Building and running the Spark script for calculating latent factors

1. Install the latest version of [Apache Spark](http://spark.apache.org/) and [sbt](http://www.scala-sbt.org/download.html).

2. Clone this repository and build the Spark script with `sbt package` (in the root directory of this repo).

3. Use the resulting jar file when running spark jobs included in the tutorials.