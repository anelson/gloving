package gloving

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter

import org.apache.spark.mllib.linalg.{DenseVector, Vector}

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Statistics(max: Double,
  min: Double,
  mean: Double,
  stdev: Double,
  variance: Double) {

  override def toString(): String = {
    Json.stringify(Json.toJson(this)(Statistics.fmt))
  }
}

object Statistics {
  implicit val fmt = Json.format[Statistics]

  def fromStatCounter(stats: StatCounter): Statistics = {
    Statistics(stats.max,
      stats.min,
      stats.mean,
      stats.stdev,
      stats.variance)
  }
}

case class VectorAnalysis(words: Long,
  dimensionality: Int,
  dimensionStats: Array[Statistics],
  pnormStats: Statistics)


