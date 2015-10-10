package gloving

import java.net.URI
import java.io.{File, PrintWriter}

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.linalg.{Vectors, Vector}

object Load {
  case class CliOptions(inputUrl: URI = null,
    outputUrl: URI = null,
    format: String = null)

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("gloving-load") {
      head("gloving", "SNAPSHOT")
      opt[URI]('i', "input") required() action { (x, c) =>
        c.copy(inputUrl = x) } text("URL or path to input vector file")
      opt[URI]('o', "output") required() action { (x, c) =>
        c.copy(outputUrl = x) } text("URL or path to output file in a format usable by the other downstream processing tasks")
      opt[String]('f', "format") required() action { (x, c) =>
        c.copy(format = x) } text("The format of the input.  must be either 'glove' or 'word2vec'")
      checkConfig { c =>
        c.format match {
          case "glove" | "word2vec" => success
          case _ => failure(s"${c.format} is not a recognized vector format")
        }
      }
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving-loader")
    val sc = new SparkContext(conf)

    config.format match {
      case "glove" => loadGlove(sc, config)
      case "word2vec" => loadWord2Vec(sc, config)
    }
  }

  def loadGlove(sc: SparkContext, config: CliOptions) {
    val loader = new GloVeWordVectorLoader(config.inputUrl)
    val words = loader.load(sc)
    words.save(config.outputUrl)
  }

  def loadWord2Vec(sc: SparkContext, config: CliOptions) = ???
}
