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

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

import gloving.WordVectorRDD._

object Analyze {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  case class CliOptions(vectorUrls: Seq[URI] = Seq(),
    outputUrl: URI = new URI("./vector-analysis.json"))

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("cluster") {
      head("gloving", "SNAPSHOT")
      arg[URI]("vector file [vector file...]") unbounded() required() action { (x, c) =>
        c.copy(vectorUrls = c.vectorUrls :+ x) } text("URLs or paths to vector files created by the load command")
      opt[URI]('o', "output") optional() action { (x, c) =>
        c.copy(outputUrl = x) } text("Path and file name to which the analysis JSON file is written")
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving-analyze")
    val sc = new SparkContext(conf)

    analyze(sc, config)
  }

  def analyze(sc: SparkContext, config: CliOptions) {
    val statsMap = config.vectorUrls.map { vectorUrl =>
      val name = new File(vectorUrl.getPath()).getName

      val words = WordVectorRDD.load(sc, vectorUrl)

      logger.info(s"Analyzing vectors $name")
      val stats = words.computeStats()

      (name, stats)
    }

    //analysis is a collection of (name, analysis) tuples.  Convert it into
    //a JSON map with 'name' as the key
    import gloving.VectorStatistics._

    val jsonAnalyses = statsMap.map{ case(k,v) => (k, Json.toJson(v)) }
    val jsonObj = JsObject(jsonAnalyses.toSeq)
    val json = Json.prettyPrint(jsonObj)

    val file = new File(config.outputUrl.toString())
    new PrintWriter(file) { write(json); close }

    logger.info(s"Wrote analysis to $file")
  }
}
