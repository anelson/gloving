package gloving

import java.net.URI
import java.io.{File, PrintWriter}
import java.nio.file.Paths

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.linalg.{Vectors, Vector}

object Load {
  case class CliOptions(inputUrls: Seq[URI] = Nil,
    outputUrl: URI = null,
    format: String = null)

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("gloving-load") {
      head("gloving", "SNAPSHOT")
      arg[URI]("input file [input file...]") unbounded() required() action { (x, c) =>
        c.copy(inputUrls = c.inputUrls :+ x) } text("URLs or paths to input vector file(s)")
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
    config.inputUrls.foreach { inputUrl =>
      val name = new File(inputUrl.getPath()).getName.replace(".gz", "").replace(".txt", "")
      val loader = new GloVeWordVectorLoader(inputUrl)
      val words = loader.load(sc)
      words.save(Paths.get(config.outputUrl.toString(), name).toUri)
    }
  }

  def loadWord2Vec(sc: SparkContext, config: CliOptions) {
    config.inputUrls.foreach { inputUrl =>
      val name = new File(inputUrl.getPath()).getName.replace(".gz", "").replace(".bin", "")
      val loader = new Word2VecWordVectorLoader(inputUrl)
      val words = loader.load(sc)
      words.save(Paths.get(config.outputUrl.toString(), name).toUri)
    }
  }
}
