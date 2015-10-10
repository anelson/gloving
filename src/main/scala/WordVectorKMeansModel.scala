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

class WordVectorKMeansModel(val words: WordVectorRDD, val model: KMeansModel) {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  def sc = words.context

  /** Computes mean euclidean distance to centroid, and mean cosine distance to centroid for each vector */
  def computeDistances(): (Double, Double) = {

    val bModel = sc.broadcast(model)

    //Classify every vector in the set using this model
    val clusteredWords = words.map(v => (bModel.value.predict(v.vector), v.vector)).cache().setName(s"${words.name}-clusteredWords")

    val distanceToCentroid = clusteredWords.map{ case(cluster, vector) =>
      val centroid = bModel.value.clusterCenters(cluster)
      math.sqrt(Vectors.sqdist(vector, centroid))
    }.mean()

    val cosineDistance = clusteredWords.map{ case(cluster, vector) =>
      val centroid = bModel.value.clusterCenters(cluster)

      val dotProduct = centroid.toArray.zip(vector.toArray).map(pair => pair._1 * pair._2).sum
      val magProduct = Vectors.norm(centroid, 2.0) * Vectors.norm(vector, 2.0)
      val cosineDistance = dotProduct / magProduct

      cosineDistance
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