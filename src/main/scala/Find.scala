package gloving

import java.net.{URI, URL}
import java.io.{File, PrintWriter}

import scala.collection.parallel.ParSeq
import scala.collection.immutable.Map

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

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import gloving.WordVectorRDD._

object Find {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  case class CliOptions(vectorUrl: URI = null,
    caseSensitive: Boolean = false,
    substring: Boolean = false,
    query: String = null)

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("find") {
      head("gloving", "SNAPSHOT")
      arg[String]("query") required() action { (x, c) =>
        c.copy(query = x) } text("String to search for")
      opt[Unit]('c', "casesensitive") optional() action { (x, c) =>
        c.copy(caseSensitive = true) } text("Perform a case-sensitive search.  Default is case-insensitive")
      opt[Unit]('s', "substring") optional() action { (x, c) =>
        c.copy(substring = true) } text("Perform a substring search.  Default is whole-word")
      opt[URI]('v', "vectors") required() action { (x, c) =>
        c.copy(vectorUrl = x) } text("Path and file name of the word vector model to search")
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving-find")
    val sc = new SparkContext(SparkHelpers.config(conf))

    find(sc, config)
  }

  def find(sc: SparkContext, config: CliOptions) {
    logger.info(s"Loading vector model ${config.vectorUrl}")
    val words = WordVectorRDD.load(sc, config.vectorUrl)

    logger.info(s"Searching for ${config.query}")

    val matches = words.filter(_.word.equalsIgnoreCase(config.query)).take(10)

    logger.info(s"Found ${matches.length} matches: ")

    matches.foreach { wv =>
      logger.info(s" ${wv.index}: ${wv.word}")
    }
  }
}
