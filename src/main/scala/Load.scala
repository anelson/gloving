package gloving

import java.net.URI
import java.io.{File, PrintWriter, FileWriter}
import java.nio.file.Paths

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.linalg.{Vectors, Vector}

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

object Load {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  case class CliOptions(inputUrls: Seq[URI] = Nil,
    dump: Boolean = false,
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
      opt[Unit]('d', "dump") optional() action { (x, c) =>
        c.copy(dump = true) } text("Enables diagnostic dumping.  Each word loaded will be dumped to a text file for further investigation")
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

    val handler = (name: String, words: WordVectorRDD) => load(name, words, config)
    config.format match {
      case "glove" => loadGlove(sc, config, handler)
      case "word2vec" => loadWord2Vec(sc, config, handler)
    }
  }

  def load(name: String, words: WordVectorRDD, config: CliOptions) {
    words.cache()

    words.save(Paths.get(config.outputUrl.toString(), name).toUri)
    words.toUnitVectors().save(Paths.get(config.outputUrl.toString(), s"$name-normalized").toUri)

    if (config.dump) {
      val dumpFile = new File(s"$name-dump.txt")

      logger.info(s"Dumping $name to ${dumpFile}")

      val delim = ","
      words.foreachPartition { part =>
        val writer = new FileWriter(dumpFile.getAbsoluteFile(), true)
        part.foreach { wv => writer.write(s"${wv.index}\t${wv.word}\t${wv.vector.toArray.mkString(delim)}\n") }
        writer.close()
      }

      logger.info(s"Dumped ${words.count} words to $dumpFile")
    }

    words.unpersist()
  }

  def loadGlove(sc: SparkContext, config: CliOptions, handler: (String, WordVectorRDD) => Unit) {
    config.inputUrls.foreach { inputUrl =>
      val name = new File(inputUrl.getPath()).getName.replace(".gz", "").replace(".txt", "")
      val loader = new GloVeWordVectorLoader(inputUrl)
      val words = loader.load(sc)

      handler(name, words)
    }
  }

  def loadWord2Vec(sc: SparkContext, config: CliOptions, handler: (String, WordVectorRDD) => Unit) {
    config.inputUrls.foreach { inputUrl =>
      val name = new File(inputUrl.getPath()).getName.replace(".gz", "").replace(".bin", "")
      val loader = new Word2VecWordVectorLoader(inputUrl)
      val words = loader.load(sc)

      handler(name, words)
    }
  }
}
