package gloving

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.linalg.{DenseVector, Vector}

import play.api.libs.json._
import play.api.libs.functional.syntax._

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

case class Statistics(max: Double,
  min: Double,
  mean: Double,
  stdev: Double,
  variance: Double,
  median: Double,
  q1: Double,
  q3: Double) {
}

object Statistics {
  def fromDescriptiveStatistics(stats: DescriptiveStatistics): Statistics = {
    Statistics(min = stats.getMin(),
      max = stats.getMax(),
      mean = stats.getMean(),
      stdev = stats.getStandardDeviation(),
      variance = stats.getVariance(),
      median = stats.getPercentile(50),
      q1 = stats.getPercentile(25),
      q3 = stats.getPercentile(75))
  }
}

case class VectorStatistics(words: Long,
  dimensionality: Int,
  dimensionStats: Array[Statistics],
  normStats: Statistics)

object VectorStatistics {
  implicit val statisticsFmt = Json.format[Statistics]
  implicit val vectorStatisticsFmt = Json.format[VectorStatistics]
}