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
}

object Statistics {
  def fromStatCounter(stats: StatCounter): Statistics = {
    Statistics(stats.max,
      stats.min,
      stats.mean,
      stats.stdev,
      stats.variance)
  }
}

case class VectorStatistics(words: Long,
  dimensionality: Int,
  dimensionStats: Array[Statistics],
  normStats: Statistics)

case class VectorAnalysis(unprocessed: VectorStatistics,
  normalized: VectorStatistics)

object VectorAnalysis {
  implicit val statisticsFmt = Json.format[Statistics]
  implicit val vectorStatisticsFmt = Json.format[VectorStatistics]
  implicit val vectorAnalysisFmt = Json.format[VectorAnalysis]
}