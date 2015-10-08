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
    val words = WordVectors.load(sc, config.gloveVectorsUrl).cache()
    val variants: Map[String, Future[RDD[Vector]]] = Map(
      (name -> Future.successful(words.map(_.vector).persist().setName(s"$name-vectors")))//,
      //(s"$name-std" -> Future { WordVectors.vectorsToStandardScoreVectors(words, WordVectors.computeDimensionStats(words)).map(_.vector).persist().setName(s"$name-std-vectors") }),
      //(s"$name-norm" -> Future { WordVectors.vectorsToUnitVectors(words).map(_.vector).persist().setName(s"$name-norm-vectors") })
    )

    val analyses = variants.map{ case (name, vectors) =>
      vectors.map{v => (name, cluster(sc, name, v, config)) }
    }

    val statsMap = Await.result(Future.sequence(analyses), Duration.Inf).toMap

    implicit val fmt = Json.format[KmeansStatistics]

    val jsonAnalyses = statsMap.map{ case(k,v) => (k, Json.toJson(v)) }
    val jsonObj = JsObject(jsonAnalyses.toSeq)
    val json = Json.prettyPrint(jsonObj)

    val file = new File(s"$name-k-${config.numClusters}.json")
    new PrintWriter(file) { write(json); close }

    logger.info(s"Wrote analysis to $file")

    config.outputUrl.map { url =>
      S3Helper.writeToS3(url.resolve(file.getName), file)
    }
  }

  def cluster(sc: SparkContext, name: String, vectors: RDD[Vector], config: CliOptions): List[KmeansStatistics] = {
    logger.info(s"K-means clustering the words for $name")

    val analyses: Map[String, List[KmeansStatistics]] = HashMap()

    val k = config.numClusters
    val runs = config.numRuns
    def iterationName(iteration: Int) = { s"$name-kmeans-k-${k}-runs-${runs}-iteration-${iteration}.model" }

    var model: Option[KMeansModel] = config.resumeIteration match {
      case Some(i) => Some(KMeansModel.load(sc, config.modelUrl.resolve(iterationName(i)).toString()))
      case None => None
    }

    val iterationsPerCheckpoint = config.numIterations / config.numCheckpoints
    val startingIteration = config.resumeIteration match {
      case Some(i) => i + iterationsPerCheckpoint
      case None => iterationsPerCheckpoint
    }

    //Figure out which iteration counts to checkpoint at.
    //The last iteration is always a checkpoint.
    val checkpoints: List[Int] = (startingIteration to config.numIterations by iterationsPerCheckpoint).toList :::
      (if (iterationsPerCheckpoint * config.numCheckpoints == config.numIterations) (Nil) else (List(config.numIterations-1)))

    val stats = for (checkpoint <- checkpoints) yield {
      val variantName = iterationName(checkpoint)
      val kmeans = new KMeans()
        .setK(k)
        .setRuns(1)
        .setMaxIterations(iterationsPerCheckpoint)
        .setInitializationMode(KMeans.RANDOM)

      //If there is a model from the previous iteration, initialize with that
      //if there is no model, then perform this first step with the specified number of runs
      model match {
        case Some(m) => kmeans.setInitialModel(m)
        case None => kmeans.setRuns(config.numRuns)
      }

      logger.info(s"Training model $variantName")
      val clusters = kmeans.run(vectors)

      //Thanks to a serious bug in Spark 1.5.0, https://issues.apache.org/jira/browse/SPARK-10548,
      //concurrent calls to save the model, which internally uses Spark SQL, are not thread safe.
      //Serialize on the SparkContext object as a workaround
      sc.synchronized {
        logger.info(s"Saving model $variantName")
        clusters.save(sc, config.modelUrl.resolve(variantName).toString())
      }

      val wsse = clusters.computeCost(vectors)

      val bClusters = sc.broadcast(clusters)

      //Classify every vector in the set using this model
      val clusteredWords = vectors.map(v => (bClusters.value.predict(v), v)).cache().setName(s"$variantName-clusteredWords")

      val distanceToCentroid = clusteredWords.map{ case(cluster, vector) =>
        val centroid = bClusters.value.clusterCenters(cluster)
        val distance = math.sqrt(Vectors.sqdist(vector, centroid))

        distance
      }.mean()

      val cosineDistance = clusteredWords.map{ case(cluster, vector) =>
        val centroid = bClusters.value.clusterCenters(cluster)

        val dotProduct = centroid.toArray.zip(vector.toArray).map(pair => pair._1 * pair._2).sum
        val magProduct = Vectors.norm(centroid, 2.0) * Vectors.norm(vector, 2.0)
        val cosineDistance = dotProduct / magProduct

        cosineDistance
      }.mean()

      clusteredWords.unpersist()

      val kms = KmeansStatistics(k = k,
        iterations = checkpoint,
        runs = runs,
        wsse = wsse,
        meanDistanceToCentroid = distanceToCentroid,
        meanCosineDistance = cosineDistance)

      logger.info(s"Stats for model $variantName: $kms")

      model = Some(clusters)

      kms
    }

    stats.toList
  }
}
