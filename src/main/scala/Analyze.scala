package gloving

import java.net.{URI, URL}
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

object Analyze {
  case class CliOptions(gloveVectorsUrl: URI = null,
    outputUrl: Option[URI] = None)

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("cluster") {
      head("gloving", "SNAPSHOT")
      opt[URI]('v', "vectors") required() action { (x, c) =>
        c.copy(gloveVectorsUrl = x) } text("URL containing GloVe pre-trained word vectors")
      opt[URI]('o', "outputdir") optional() action { (x, c) =>
        c.copy(outputUrl = Some(x)) } text("URL at which output is written")
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving-analyze")
    val sc = new SparkContext(conf)

    analyze(sc, config)
  }

  def analyze(sc: SparkContext, config: CliOptions) {
    val name = new File(config.gloveVectorsUrl.getPath()).getName

    val words = WordVectors.load(sc, config.gloveVectorsUrl).cache()

    val analyses: Map[String, VectorAnalysis] = HashMap()

    println("Analyzing vectors")
    analyses(name) = analyze(words)

    println("Analyzing standardized vectors")
    analyses(name + "-std") = analyze(WordVectors.vectorsToStandardScoreVectors(words, WordVectors.computeDimensionStats(words)))

    println("Analyzing normalized vectors")
    analyses(name + "-norm") = analyze(WordVectors.vectorsToUnitVectors(words))

    implicit val fmt = Json.format[VectorAnalysis]
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

  def analyze(words: RDD[WordVector]): VectorAnalysis = {
    val pnorms = words.map { wv => Vectors.norm(wv.vector, 2.0) }

    VectorAnalysis(words = words.count(),
      dimensionality = words.first().vector.size,
      dimensionStats = WordVectors.computeDimensionStats(words),
      pnormStats = Statistics.fromStatCounter(pnorms.stats))
  }
}
