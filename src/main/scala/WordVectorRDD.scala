package gloving

import java.net.URI
import java.io.File

import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SaveMode
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter

import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}
import org.apache.spark.mllib.linalg.{Vectors, Vector}

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

class WordVectorRDD(val rdd: RDD[WordVector]) {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  def name = rdd.name

  def computeDimensionStats(): Array[Statistics] = {
    val vectorAsArray = rdd.map(_.vector.toArray).setName(s"${name}-asArray")

    val dimensionality = rdd.first().vector.size
    val n = vectorAsArray.count()

    //Compute the stats for each dimension
    val stats = vectorAsArray.aggregate(Array.fill(dimensionality) { new StatCounter() }) (
      (a, b) => a.zip(b).map { case (stats, value) => stats.merge(value) },
      (a, b) => a.zip(b).map { case (stats1, stats2) => stats1.merge(stats2) }
    )

    stats.map { stat => Statistics.fromStatCounter(stat) }
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

  def save(path: URI) {
    val sqlContext = new SQLContext(rdd.context)
    import sqlContext.implicits._

    logger.info(s"Saving ${name} to $path")
    rdd.toDF().write.mode(SaveMode.Overwrite).format("parquet").save(path.toString())
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
}
