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

import breeze.linalg.DenseVector
import breeze.linalg.functions.{euclideanDistance, cosineDistance}

import play.api.libs.json._
import play.api.libs.json.Json._

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

class WordVectorKMeansModel(val words: WordVectorRDD, val model: KMeansModel) {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  def sc = words.context

  /** Computes mean euclidean distance to centroid, and mean cosine distance to centroid for each vector */
  def computeDistances(): (Double, Double) = {

    val bModel = sc.broadcast(model)

    //Classify every vector in the set using this model
    val clusteredWords = words.map { wv =>
      //TODO: refactor this so it doesn't incurr an array copy
      val clusterCenterIndex = bModel.value.predict(Vectors.dense(wv.vector.data))
      val clusterCenterVector = bModel.value.clusterCenters(clusterCenterIndex)


      (DenseVector[Double](clusterCenterVector.toArray), wv.vector)
    }.cache().setName(s"${words.name}-clusteredWords")

    val distanceToCentroid = clusteredWords.map{ case(cluster, vector) =>
      euclideanDistance(vector, cluster)
    }.mean()

    val cosineDistance = clusteredWords.map{ case(cluster, vector) =>
      breeze.linalg.functions.cosineDistance(cluster, vector)
    }.mean()

    clusteredWords.unpersist()

    (distanceToCentroid, cosineDistance)
  }
}

object WordVectorKMeansModel {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  implicit def toKMeansModel(wvkm: WordVectorKMeansModel): KMeansModel = wvkm.model

  def load(words: WordVectorRDD, path: URI): WordVectorKMeansModel = {
    val model = KMeansModel.load(words.context, path.toString())

    new WordVectorKMeansModel(words, model)
  }
}