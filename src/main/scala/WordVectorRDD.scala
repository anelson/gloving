package gloving

import java.net.URI
import java.io.File

import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SaveMode
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter

import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}
import org.apache.spark.mllib.linalg.{Vectors, Vector}

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

class WordVectorRDD(val rdd: RDD[WordVector]) {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  def name = rdd.name

  def computeStats(): VectorStatistics = {
    VectorStatistics(words = rdd.count(),
      dimensionality = rdd.first().vector.size,
      dimensionStats = computeDimensionStats(),
      normStats = computeNormStatistics)
  }

  def computeDimensionStats(): Array[Statistics] = {
    val vectorAsArray = rdd.map(_.vector.toArray).setName(s"${name}-asArray").persist(StorageLevel.MEMORY_AND_DISK)

    try {
      val dimensionality = rdd.first().vector.size
      val n = vectorAsArray.count()

      //Compute the stats, including median and inter-quartile range (IQR) for each dimension.  This is going to suck.
      // I can't figure a good way to do this within the confines of an RDD, so ALL values in a given dimension are pulled down to the driver
      // God have mercy.
      val stats = for (dim <- 0 until dimensionality) yield {
        // Get all of the values of this particular dimension, sort them so the smallest value is first, and compute the stats
        val values = vectorAsArray.map(_(dim)).collect()
        val desc = new DescriptiveStatistics(values)

        Statistics.fromDescriptiveStatistics(desc)
      }

      stats.toArray
    } finally {
      vectorAsArray.unpersist()
    }
  }

  def computeNormStatistics: Statistics = {
    //Someday I'll figure how to compute median and IQR within Spark and this won't hurt so bad
    val norms = rdd.map { wv => Vectors.norm(wv.vector, 2.0) }
    val desc = new DescriptiveStatistics(norms.collect())
    Statistics.fromDescriptiveStatistics(desc)
  }

  def toStandardScoreVectors(): WordVectorRDD = {
    toStandardScoreVectors(computeDimensionStats())
  }

  def toStandardScoreVectors(dimStats: Array[Statistics]): WordVectorRDD = {
    val bDimStats = rdd.context.broadcast(dimStats)

    try {
      rdd.map { wv =>
        val arr = wv.vector.toArray

        val newArr = bDimStats.value.zip(arr).map { case (stat, value) =>
          (value - stat.mean) / (if (stat.stdev <= 0) (1.0) else ( stat.stdev ))
        }

        wv.copy(vector = Vectors.dense(newArr))
      }
    } finally {
      bDimStats.unpersist()
    }
  }

  def toUnitVectors(): WordVectorRDD = {
    rdd.map(_.normalize)
  }

  def save(path: URI, mode: SaveMode = SaveMode.Overwrite) {
    val sqlContext = new SQLContext(rdd.context)
    import sqlContext.implicits._

    logger.info(s"Saving ${name} to $path")
    rdd.toDF().write.mode(mode).format("parquet").save(path.toString())
  }
}

object WordVectorRDD {
  implicit def RDD2WordVectorRDD(value: RDD[WordVector]): WordVectorRDD = {
    new WordVectorRDD(value)
  }

  implicit def WordVectorRDD2RDD(value: WordVectorRDD): RDD[WordVector] = value.rdd

	def load(sc: SparkContext, path: URI): WordVectorRDD = {
    val name = new File(path.getPath()).getName
    val sqlContext = new SQLContext(sc)
    sqlContext.read.parquet(path.toString()).map(row => WordVector.apply(row.getLong(0), row.getString(1), row.getAs[Vector](2)))
      .setName(s"$name-wordvectors")
	}

  // Given an array of doubles, figures out the median, and returns two arrays, one with
  // the lower half and one with the upper half.
  def splitOnMedian(values: Array[Double]): (Array[Double], Double, Array[Double]) = {
    //If the number of values is odd, the median is the middle value,
    //else it's the mean of the two values in the middle
    values.length match {
      case 0 => (Array(), 0.0, Array())
      case 1 => (Array(), values.head, Array())
      case x if x % 2 == 0 => {
        //Even number of elements in the array
        val midpoint = x / 2

        val median = (values(midpoint-1)+values(midpoint)) / 2
        val lower = values.slice(0, midpoint-1)
        val upper = values.slice(midpoint, values.length)

        (lower, median, upper)
      }

      case x => {
        //Odd number of elements int he array
        val midpoint = x / 2

        val median = values(midpoint)
        val lower = values.slice(0, midpoint)
        val upper = values.slice(midpoint + 1, values.length)

        (lower, median, upper)
      }
    }
  }

  def iqr(lower: Array[Double], upper: Array[Double]): (Double, Double) = {
    val (_, q1, _) = splitOnMedian(lower)
    val (_, q3, _) = splitOnMedian(upper)

    (q1, q3)
  }
}
