package gloving

import java.net.URI
import java.io.{File, PrintWriter}

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}
import org.apache.spark.mllib.linalg.{Vectors, Vector}

object Cluster {
  case class CliOptions(modelUrl: URI = null,
    gloveVectorsUrl: URI = null,
    outputUrl: Option[URI] = None,
    numClusters: Int = 1000,
    numIterations: Int = 5,
    numRuns: Int = 40)

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("cluster") {
      head("gloving", "SNAPSHOT")
      opt[URI]('m', "model") required() action { (x, c) =>
        c.copy(modelUrl = x) } text("URL to model to store K-Means model data")
      opt[URI]('v', "vectors") required() action { (x, c) =>
        c.copy(gloveVectorsUrl = x) } text("URL containing GloVe pre-trained word vectors")
      opt[Int]('k', "clusters") optional() action { (x, c) =>
        c.copy(numClusters = x) } text("Number of clusters to model")
      opt[Int]('i', "iterations") optional() action { (x, c) =>
        c.copy(numIterations = x) } text("Number of iterations to train the model")
      opt[URI]('o', "outputdir") optional() action { (x, c) =>
        c.copy(outputUrl = Some(x)) } text("URL at which output is written")
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving")
    val sc = new SparkContext(conf)

    cluster(sc, config)
  }

  def cluster(sc: SparkContext, config: CliOptions) {
    val words = WordVectors.load(sc, config.gloveVectorsUrl)

    println("Computing vector stats...")
    val stats = WordVectors.computeDimensionStats(words)

    println("Vector stats by dimension: ")
    stats.zipWithIndex.foreach { case(dimStats, index) =>
      println(s"$index: $dimStats")
    }

    val vectors = words.map{x => x.vector}.cache()

    println("K-means clustering the words")

    val clusters = KMeans.train(vectors,
      config.numClusters,
      config.numIterations,
      1,
      KMeans.RANDOM)

    println("Saving model")
    clusters.save(sc, config.modelUrl.toString())

    val wsse = clusters.computeCost(vectors)
    println(s"WSSE: $wsse")
  }
}
