package gloving

import java.net.URI

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter

import org.apache.spark.mllib.linalg.{Vectors, Vector}

object WordVectors {
	def load(sc: SparkContext, path: URI): RDD[WordVector] = {
		val tuples = readTuples(sc, path)
		tuples.map { case(index, word, vector) => WordVector(index, word, Vectors.dense(vector)) }
	}

  def readTuples(sc: SparkContext, path: URI): RDD[(Long, String, Array[Double])] = {
    val file = sc.textFile(path.toString)
    val tuples = file
      .zipWithIndex()
      .repartition(sc.defaultParallelism * 3) //Because gzip-ed textfiles aren't splittable natively, oops!
      .map{case(line,index) => (index, line.split(" "))}
      .map{case(index,arr) => (index, arr.head, arr.tail)}
      .map{case(index, word, vector) => (index, word, vector.map(_.toDouble))}


    tuples
  }

  def computeDimensionStats(vects: RDD[WordVector]): Array[Statistics] = {
    val vectorAsArray = vects.map(_.vector.toArray).cache()
    val dimensionality = vects.first().vector.size
    val n = vectorAsArray.count()

    //Compute the stats for each dimension
    val stats = vectorAsArray.aggregate(Array.fill(dimensionality) { new StatCounter() }) (
      (a, b) => a.zip(b).map { case (stats, value) => stats.merge(value) },
      (a, b) => a.zip(b).map { case (stats1, stats2) => stats1.merge(stats2) }
    )

    stats.map { stat => Statistics.fromStatCounter(stat) }
  }

  def vectorsToStandardScoreVectors(vects: RDD[WordVector]): RDD[WordVector] = {
    vectorsToStandardScoreVectors(vects, computeDimensionStats(vects))
  }

  def vectorsToStandardScoreVectors(vects: RDD[WordVector], dimStats: Array[Statistics]): RDD[WordVector] = {
    vects.map { wv =>
      val arr = wv.vector.toArray

      val newArr = dimStats.zip(arr).map { case (stat, value) =>
        (value - stat.mean) / (if (stat.stdev <= 0) (1.0) else ( stat.stdev ))
      }

      wv.copy(vector = Vectors.dense(newArr))
    }.cache()
  }

  def vectorsToUnitVectors(vects: RDD[WordVector]): RDD[WordVector] = {
    val pnorms = vects.map { wv => Vectors.norm(wv.vector, 2.0) }
    val max = pnorms.max

    vects.map { wv =>
      val newArr = wv.vector.toArray.map { value => value / max }

      wv.copy(vector = Vectors.dense(newArr))
    }.cache()
  }
}
