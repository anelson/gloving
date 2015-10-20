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
  percentiles: Map[String, Double],
  histogram: Array[Int]) {
}

object Statistics {
  val empty = Statistics(0, 0, 0, 0, 0, Map(), Array())

  def fromDescriptiveStatistics(stats: DescriptiveStatistics, numBins: Int = 10): Statistics = {
    val percentiles = Map(
      "2"-> stats.getPercentile(2),
      "9"-> stats.getPercentile(9),
      "25" -> stats.getPercentile(25),
      "50" -> stats.getPercentile(50),
      "75" -> stats.getPercentile(75),
      "91" -> stats.getPercentile(91),
      "98" -> stats.getPercentile(98)
    )

    Statistics(min = stats.getMin(),
      max = stats.getMax(),
      mean = stats.getMean(),
      stdev = stats.getStandardDeviation(),
      variance = stats.getVariance(),
      percentiles = percentiles,
      histogram = computeHistogram(stats, numBins))
  }

  def computeHistogram(stats: DescriptiveStatistics, numBins: Int): Array[Int] = {
    val min = stats.getMin()
    val max = stats.getMax()
    val result = new Array[Int](numBins)
    val binSize = (max - min) / numBins

    val values = stats.getValues()
    val bins = values.map {
      case x if x == min => 0
      case x if x == max => numBins - 1
      case x => ((x - min) / binSize).toInt
    }
    val binCounts = bins.groupBy(identity).mapValues(_.size)

    binCounts.foreach { case(bin, count) => result(bin) = count }

    result
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