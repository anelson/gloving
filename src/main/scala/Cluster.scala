package gloving

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

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

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

import WordVectorKMeansClusterer.TrainingState

object Cluster {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  case class CliOptions(modelUrl: URI = null,
    gloveVectorsUrl: URI = null,
    outputUrl: Option[URI] = None,
    numClusters: Int = 1000,
    numIterations: Int = 5,
    numCheckpoints: Int = 5,
    numRuns: Int = 1,
    resumeIteration: Option[Int] = None)

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("cluster") {
      head("gloving", "SNAPSHOT")
      opt[URI]('m', "model") required() action { (x, c) =>
        c.copy(modelUrl = x) } text("URL to model to store K-Means model data")
      opt[URI]('v', "vectors") required() action { (x, c) =>
        c.copy(gloveVectorsUrl = x) } text("URL containing GloVe pre-trained word vectors")
      opt[Int]('k', "clusters") required() action { (x, c) =>
        c.copy(numClusters = x) } text("Number of clusters to model")
      opt[Int]('i', "iterations") required() action { (x, c) =>
        c.copy(numIterations = x) } text("Number of iterations to train the model")
      opt[Int]('c', "checkpoints") required() action { (x, c) =>
        c.copy(numCheckpoints = x) } text("Number of times during training to stop after some number of iterations and store the model")
      opt[Int]('r', "runs") required() action { (x, c) =>
        c.copy(numRuns = x) } text("Number of iterations to train the model")
      opt[Int]("resumeIteration") optional() action { (x, c) =>
        c.copy(resumeIteration = Some(x)) } text("Resume execution after this iteration")
      opt[URI]('o', "outputdir") optional() action { (x, c) =>
        c.copy(outputUrl = Some(x)) } text("URL at which output is written")
      checkConfig { c =>
        if (c.numIterations < c.numCheckpoints) {
          failure("the number of checkpoints must be less than the number of iterations")
        } else {
          success
        }
      }
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving")
    val sc = new SparkContext(conf)

    cluster(sc, config)
  }

  def cluster(sc: SparkContext, config: CliOptions) {
    val name = new File(config.gloveVectorsUrl.getPath()).getName
    val words = WordVectorRDD.load(sc, config.gloveVectorsUrl).cache()
    val variants: Map[String, Future[WordVectorRDD]] = Map(
      (name -> Future.successful(words))//,
      //(s"$name-std" -> Future { WordVectors.vectorsToStandardScoreVectors(words, WordVectors.computeDimensionStats(words)).map(_.vector).persist().setName(s"$name-std-vectors") }),
      //(s"$name-norm" -> Future { WordVectors.vectorsToUnitVectors(words).map(_.vector).persist().setName(s"$name-norm-vectors") })
    )

    val clusterFutures = variants.map { case (name, vectors) =>
      vectors.map { v => (name, cluster(sc, name, v, config)) }
    }

    Await.result(Future.sequence(clusterFutures), Duration.Inf)
  }

  def cluster(sc: SparkContext, name: String, words: WordVectorRDD, config: CliOptions) = {
    logger.info(s"K-means clustering the words for $name")

    val clusterer = new WordVectorKMeansClusterer(words)

    try {
      def iterationName(iteration: Int) = s"$name-kmeans-k-${config.numClusters}-runs-${config.numRuns}-iteration-${iteration}.model"

      val initialState = config.resumeIteration match {
        case Some(i) => Some(TrainingState(i, WordVectorKMeansModel.load(words, config.modelUrl.resolve(iterationName(i)))))
        case None => None
      }

      clusterer.trainInSteps(k = config.numClusters,
        totalIterations = config.numIterations,
        iterationsPerStep = config.numIterations / config.numCheckpoints,
        config.numRuns,
        initialState,
        (state) => {
          val variantName = iterationName(state.iteration)

          //Thanks to a serious bug in Spark 1.5.0, https://issues.apache.org/jira/browse/SPARK-10548,
          //concurrent calls to save the model, which internally uses Spark SQL, are not thread safe.
          //Serialize on the SparkContext object as a workaround
          sc.synchronized {
            logger.info(s"Saving model $variantName")
            state.model.save(sc, config.modelUrl.resolve(variantName).toString())
          }
        }
      )
    } finally {
      clusterer.unpersist()
    }
  }
}
