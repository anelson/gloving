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
  outputUrl: Option[URI] = None,
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
      opt[URI]('o', "outputdir") required() action { (x, c) =>
        c.copy(outputUrl = Some(x)) } text("URL at which output is written")
      checkConfig { c =>
        c.command match {
          case "train" => {
            c.gloveVectorsUrl match {
              case Some(_) => success
              case None => failure("The GloVe vectors URL must be provided when training")
            }
          }

          case "dump" => {
            c.outputUrl match {
              case Some(_) => success
              case None => failure("Dump requires an output directory to be specified")
            }
          }

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
    val vectors = tuples.map(pair => Vectors.dense(pair._3)).persist()

    println("K-means clustering the words")

    val clusters = KMeans.train(vectors, config.numClusters, config.numIterations, 1, KMeans.RANDOM)

    println("Saving model")
    clusters.save(sc, config.modelUrl.get.toString())

    val wsse = clusters.computeCost(vectors)
    println(s"WSSE: $wsse")
  }

  def dump(sc: SparkContext, config: CliOptions) {
    println("Loading tuples")
    val tuples: RDD[(Long, String, Vector)] = readTuples(sc, config.gloveVectorsUrl.get).map {
        case(index, word, vector) => (index, word, Vectors.dense(vector))
      }
      .persist()

    println("Loading model")
    val model = KMeansModel.load(sc, config.modelUrl.get.toString())

    //For each word with a vector, figure out which cluster that word belongs to, building
    //a RDD of (Int, String) tuples
    val wordClusterAssignments: RDD[(Int, (Long, String, Double))] = tuples.map { case(index,word,vector) =>

      val clusterGroupIndex = model.predict(vector)
      val distanceFromClusterCenter = Vectors.sqdist(model.clusterCenters(clusterGroupIndex), vector)
      (clusterGroupIndex, (index, word, distanceFromClusterCenter))
    }

    //Group the assignments so all words assigned to a given cluster are together
    val clusterIndexWords: RDD[(Int, Iterable[(Long, String, Double)])] = wordClusterAssignments.groupByKey()

    //For each cluster, find the word in that cluster with the lowest line number, since the vector file
    //is sorted by word frequency this will allow us to choose the most common word in a group to represent
    //that group
    val clusterWords: RDD[(String, List[String])] = clusterIndexWords.map { pair =>
      val index: Int = pair._1
      val words: List[(Long, String, Double)] = pair._2.toList

      val sortedWords = words.sortBy(_._1)

      val nearest = sortedWords.head

      (nearest._2, sortedWords.map(_._2))
    }.persist()

    //For S3 output, transform to an RDD of Iterable[String] lists, and write that to a text file
    clusterWords.map(_._2).coalesce(1).saveAsTextFile(config.outputUrl.get.toString)

/*
    clusterWords.map(pair => pair._1).collect().par.foreach { centerWord =>
      println(s"Found cluster with center word $centerWord")

      val wordsInCluster = clusterWords.filter(_._1 == centerWord).first()._2

      val normalizedWord = centerWord.replaceAll("[^a-zA-Z0-9.-]", "_")
      val writer = new PrintWriter(new File(s"words/$normalizedWord.txt"))
      wordsInCluster.foreach { word =>
        writer.write(word)
        writer.write("\n")
      }
      writer.close()
    }
    */
  }

  def readTuples(sc: SparkContext, path: URI): RDD[(Long, String, Array[Double])] = {
    val file = sc.textFile(path.toString)
    val tuples = file
      .repartition(sc.defaultParallelism * 3) //Because gzip-ed textfiles aren't splittable natively, oops!
      .zipWithIndex()
      .map{case(line,index) => (index, line.split(" "))}
      .map{case(index,arr) => (index, arr.head, arr.tail)}
      .map{case(index, word, vector) => (index, word, vector.map(_.toDouble))}


    tuples
  }
}
