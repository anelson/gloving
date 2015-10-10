package gloving

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter

import org.apache.spark.mllib.linalg.{DenseVector, Vector}

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class KmeansStatistics(k: Int, iterations: Int, runs: Int, wsse: Double, meanDistanceToCentroid: Double, meanCosineDistance: Double)
