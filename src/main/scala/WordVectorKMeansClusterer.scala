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

class WordVectorKMeansClusterer(val words: WordVectorRDD) {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))
  val nakedVectors = words.map(_.vector).persist().setName(s"${words.name}-nakedVectors")
  val count = nakedVectors.count()

  def unpersist() { nakedVectors.unpersist() }

  def sc = words.context

  def train(k: Int, iterations: Int, initialModel: WordVectorKMeansModel): WordVectorKMeansModel = {
    train(k, iterations, runs = None, initialModel = Some(initialModel))
  }

  def train(k: Int, iterations: Int, runs: Int): WordVectorKMeansModel = {
    train(k, iterations, runs = Some(runs), initialModel = None)
  }

  def train(k: Int, iterations: Int, runs: Option[Int], initialModel: Option[WordVectorKMeansModel]): WordVectorKMeansModel = {
    //Either the number of runs OR an initial model can be specified, but not both
    //The whole purpose of having multiple runs is to run clustering with multiple random initial models to see which one
    //performs best, but if there's a user-defined initial model then there's no point in running it multiple times.
    assert(runs.isDefined || initialModel.isDefined)
    assert(!(runs.isDefined && initialModel.isDefined))

    val kmeans = new KMeans()
      .setK(k)
      .setMaxIterations(iterations)
      .setInitializationMode(KMeans.RANDOM)

    //If there is a model from the previous iteration, initialize with that
    //if there is no model, then perform this first step with the specified number of runs
    initialModel.map { m => kmeans.setInitialModel(m) }
    runs.map { r => kmeans.setRuns(r) }

    logger.info(s"Training model on $count vectors in ${words.name} with k=${k}, iterations=${iterations}, runs=${runs}, initialModel=${initialModel.isDefined}")
    new WordVectorKMeansModel(words, kmeans.run(nakedVectors))
  }

  def trainInSteps[A](k: Int,
    totalIterations: Int,
    iterationsPerStep: Int,
    runs: Int,
    initialState: Option[WordVectorKMeansClusterer.TrainingState],
    stepFunc: WordVectorKMeansClusterer.TrainingState => A): Seq[A] = {
    assert(totalIterations % iterationsPerStep == 0)

    var model = initialState.map{s => s.model}
    val startingIteration = initialState match {
      case Some(s) => s.iteration + iterationsPerStep
      case None => iterationsPerStep
    }

    //Figure out which iteration counts to checkpoint at.
    //The last iteration is always a checkpoint.
    val steps: List[Int] = (startingIteration to totalIterations by iterationsPerStep).toList

    val results = for (iteration <- steps) yield {
      logger.info(s"Training model for $iterationsPerStep iterations starting with iteration $iteration")

      val nextModel = model match {
        case Some(m) => train(k, iterationsPerStep, m)
        case None => train(k, iterationsPerStep, runs)
      }

      model = Some(nextModel)

      stepFunc(WordVectorKMeansClusterer.TrainingState(iteration, nextModel))
    }

    results.toSeq
  }
}

object WordVectorKMeansClusterer {
  case class TrainingState(iteration: Int, model: WordVectorKMeansModel)
}
