package gloving

import java.net.URI
import java.io.{File, PrintWriter}

import scala.collection.mutable.{Map,HashMap}

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}
import org.apache.spark.mllib.linalg.{Vectors, Vector}

import play.api.libs.json._
import play.api.libs.json.Json._

object Cluster {
  case class CliOptions(modelUrl: URI = null,
    gloveVectorsUrl: URI = null,
    outputUrl: Option[URI] = None)

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("cluster") {
      head("gloving", "SNAPSHOT")
      opt[URI]('m', "model") required() action { (x, c) =>
        c.copy(modelUrl = x) } text("URL to model to store K-Means model data")
      opt[URI]('v', "vectors") required() action { (x, c) =>
        c.copy(gloveVectorsUrl = x) } text("URL containing GloVe pre-trained word vectors")
      opt[URI]('o', "outputdir") optional() action { (x, c) =>
        c.copy(outputUrl = Some(x)) } text("URL at which output is written")
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving")
    val sc = new SparkContext(conf)

    cluster(sc, config)
  }

  def cluster(sc: SparkContext, config: CliOptions) {
    val name = new File(config.gloveVectorsUrl.getPath()).getName
    val words = WordVectors.load(sc, config.gloveVectorsUrl)
    val vectors = words.map{x => x.vector}.cache()

    println("K-means clustering the words")

    val analyses: Map[String, List[KmeansStatistics]] = HashMap()

    val stats = for (k <- Seq(1000, 5000, 10000);
      iterations <- Seq(10, 20, 40, 60, 80, 100);
      runs <- Seq(1, 20, 30, 40)) yield {
      val variantName = s"$name-kmeans-$k-$iterations-$runs.model"

      val clusters = KMeans.train(vectors,
        k,
        iterations,
        runs,
        KMeans.RANDOM)

      println("Saving model")
      clusters.save(sc, config.modelUrl.resolve(variantName).toString())

      val wsse = clusters.computeCost(vectors)

      //Classify every vector in the set using this model
      val clusteredWords = vectors.map(v => (clusters.predict(v), v))

      val distanceToCentroid = clusteredWords.map{ case(cluster, vector) =>
        val centroid = clusters.clusterCenters(cluster)
        val distance = math.sqrt(Vectors.sqdist(vector, centroid))

        distance
      }.mean()

      val kms = KmeansStatistics(k = k, iterations = iterations, runs = runs, wsse = wsse, meanDistanceToCentroid = distanceToCentroid)

      println(kms)

      kms
    }

    analyses(name) = stats.toList

    implicit val fmt = Json.format[KmeansStatistics]

    val jsonAnalyses = analyses.map{ case(k,v) => (k, Json.toJson(v)) }
    val jsonObj = JsObject(jsonAnalyses.toSeq)
    val json = Json.prettyPrint(jsonObj)

    val file = new File(s"$name.json")
    new PrintWriter(file) { write(json); close }

    println(s"Wrote analysis to $file")

    config.outputUrl.map { url =>
      S3Helper.writeToS3(url.resolve(file.getName), file)
    }
  }
}
