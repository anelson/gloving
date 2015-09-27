package gloving

import java.net.URI
import java.io.{File, PrintWriter}

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}
import org.apache.spark.mllib.linalg.{Vectors, Vector}

case class CliOptions(command: String = "",
  modelUrl: Option[URI] = None,
  gloveVectorsUrl: Option[URI] = None,
  numClusters: Int = 1000,
  numIterations: Int = 5)

object Main {
  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("gloving") {
      head("gloving", "SNAPSHOT")
      arg[String]("<command>") required() action { (x, c) =>
        c.copy(command = x) } text("Operation to perform")
      opt[URI]('m', "model") required() action { (x, c) =>
        c.copy(modelUrl = Some(x)) } text("URL to model to store K-Means model data")
      opt[URI]('v', "vectors") required() action { (x, c) =>
        c.copy(gloveVectorsUrl = Some(x)) } text("URL containing GloVe pre-trained word vectors")
      opt[Int]('k', "clusters") optional() action { (x, c) =>
        c.copy(numClusters = x) } text("Number of clusters to model")
      opt[Int]('i', "iterations") optional() action { (x, c) =>
        c.copy(numIterations = x) } text("Number of iterations to train the model")
      checkConfig { c =>
        c.command match {
          case "train" => {
            c.gloveVectorsUrl match {
              case Some(_) => success
              case None => failure("The GloVe vectors URL must be provided when training")
            }
          }

          case "dump" => success

          case _ => failure(s"Unrecognized command ${c.command}")
        }
      }
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving")
    val sc = new SparkContext(conf)

    config.command match {
      case "train" => train(sc, config)
      case "dump" => dump(sc, config)
    }
  }

  def train(sc: SparkContext, config: CliOptions) {
    val tuples = readTuples(sc, config.gloveVectorsUrl.get)
    val vectors = tuples.map(pair => Vectors.dense(pair._2)).persist()

    println("K-means clustering the words")

    val clusters = KMeans.train(vectors, config.numClusters, config.numIterations, 1, KMeans.K_MEANS_PARALLEL)

    println("Saving model")
    clusters.save(sc, "kmeans.model")

    val wsse = clusters.computeCost(vectors)
    println(s"WSSE: $wsse")
  }

  def dump(sc: SparkContext, config: CliOptions) {
    val tuples: RDD[(String, Vector)] = readTuples(sc, config.gloveVectorsUrl.get).map(pair => (pair._1, Vectors.dense(pair._2)))

    val model = KMeansModel.load(sc, config.modelUrl.get.toString())

    //For each word with a vector, figure out which cluster that word belongs to, building
    //a RDD of (Int, String) tuples
    val wordClusterAssignments: RDD[(Int, (String, Double))] = tuples.map { pair =>
      val word = pair._1
      val vector = pair._2

      val clusterGroupIndex = model.predict(vector)
      val distanceFromClusterCenter = Vectors.sqdist(model.clusterCenters(clusterGroupIndex), vector)
      (clusterGroupIndex, (word, distanceFromClusterCenter))
    }

    //Group the assignments so all words assigned to a given cluster are together
    val clusterIndexWords: RDD[(Int, Iterable[(String, Double)])] = wordClusterAssignments.groupByKey()

    //Find the nearest word to the center point of each cluster, and use that word to identify the cluster
    //instead of its index
    val clusterWords: RDD[(String, Iterable[String])] = clusterIndexWords.map { pair =>
      val index: Int = pair._1
      val words: Iterable[(String, Double)] = pair._2

      val ordering = new Ordering[(String, Double)]() {
        override def compare(x: (String, Double), y: (String, Double)): Int = Ordering[Double].compare(x._2, y._2)
      }

      val nearest = words.min(ordering)

      (nearest._1, words.map(_._1))
    }

    clusterWords.map(pair => pair._1).collect().par.foreach { centerWord =>
      println(s"Found cluster with center word $centerWord")

      val wordsInCluster = clusterWords.filter(_._1 == centerWord).collect().head._2

      val normalizedWord = centerWord.replaceAll("[^a-zA-Z0-9.-]", "_")
      val writer = new PrintWriter(new File(s"words/$normalizedWord.txt"))
      wordsInCluster.foreach { word =>
        writer.write(word)
        writer.write("\n")
      }
      writer.close()
    }
  }

  def readTuples(sc: SparkContext, path: URI): RDD[(String, Array[Double])] = {
    val file = sc.textFile(path.toString)
    val tuples = file
      .map(line => line.split(" "))
      .map(arr => (arr.head, arr.tail))
      .map(pair => (pair._1, pair._2.map(_.toDouble)))

    tuples
  }
}
