package gloving

import java.net.URI
import java.io.{File, Serializable}

import org.apache.spark.Partitioner
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

class WordVectorRDD(val rdd: RDD[WordVector]) extends Serializable {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  def name = rdd.name

  def computeStats(histogramBins: Int = 20): VectorStatistics = {
    VectorStatistics(words = rdd.count(),
      dimensionality = rdd.first().vector.size,
      dimensionStats = computeDimensionStats(histogramBins),
      normStats = computeNormStatistics(histogramBins))
  }

  def computeDimensionStats(histogramBins: Int): Array[Statistics] = {
    val vectorAsArray = rdd.map(_.vector.toArray).setName(s"${name}-asArray").persist(StorageLevel.MEMORY_AND_DISK)

    try {
      val dimensionality = vectorAsArray.first().length
      val n = vectorAsArray.count.toInt

      val stats: Array[(Int, Statistics)] = (0 until dimensionality).grouped(n / 4).toSeq.par.flatMap { dimensions =>
        for (dim <- dimensions) yield {
          val values = vectorAsArray.map(_(dim)).collect()

          val desc = new DescriptiveStatistics(values)
          (dim, Statistics.fromDescriptiveStatistics(desc, histogramBins))
        }
      }.toArray

      stats.sortBy(_._1).map(_._2)
    } finally {
      vectorAsArray.unpersist()
    }
  }

  def computeNormStatistics(histogramBins: Int): Statistics = {
    //Someday I'll figure how to compute median and IQR within Spark and this won't hurt so bad
    val norms = rdd.map { wv => Vectors.norm(wv.vector, 2.0) }
    val desc = new DescriptiveStatistics(norms.collect())
    Statistics.fromDescriptiveStatistics(desc, histogramBins)
  }

  def toUnitVectors(): WordVectorRDD = {
    rdd.map(_.normalize)
  }

  def findWord(word: String):  Option[WordVector] = {
    rdd.filter(_.word == word).take(1).headOption
  }

  /* In Scala, as in life, vectorization is critical to good performance.  Word lookups are expensive, so
  its better to do them all at once.  This vectorized version of findWord looks up a bunch of words, and returns a
  map mapping the words to the corresponding WordVector.  If one of the words isn't found, it simply doesn't appear in
  the map */
  def findWords(words: Set[String]): Map[String, WordVector] = {
    val bWords = rdd.context.broadcast(words)

    val wordMap = rdd.filter(wv => bWords.value.contains(wv.word)).map( wv => (wv.word, wv)).collect().toMap

    bWords.unpersist

    wordMap
  }

  def findNearest(n: Int, distanceFunction: (Vector) => Double, lowerIsBetter: Boolean): Array[(WordVector, Double)] = {
    //Compute the distance between this vector and all the word vectors
    val distances: RDD[(WordVector, Double)] = rdd.map { wv =>
      val distance = distanceFunction(wv.vector)

      (wv, distance)
    }

    //Create an ordering for the results that we can pass to top.  Top is looking for the 'largets' n elements,
    //so if lowerIsBetter is true, that means we need an ordering that places lower values as 'greater than' higher values,
    //and vice versa is lowerIsBetter is false.  That's why the ordering below seems like it's exactly the opposite of what you
    //would want if you were sorting the results and taking the first n
    val ordering: Ordering[(WordVector, Double)] = Ordering.by(
      if (lowerIsBetter) { (r) => -r._2 }
      else { (r) => r._2 }
    )

    distances.top(n)(ordering)
  }

  def findNearestEuclidean(vector: Vector, n: Int): Array[(WordVector, Double)] = findNearest(n, WordVectorRDD.euclideanDistance(vector), true)

  def findNearestCosine(vector: Vector, n: Int): Array[(WordVector, Double)] = {
    findNearest(n, WordVectorRDD.cosineSimilarity(vector), false)
  }

  def save(path: URI, mode: SaveMode = SaveMode.Overwrite) {
    val sqlContext = new SQLContext(rdd.context)
    import sqlContext.implicits._

    logger.info(s"Saving ${name} to $path")
    rdd.toDF().write.mode(mode).format("parquet").save(path.toString())
  }
}

object WordVectorRDD {
  import VectorImplicits._

  implicit def RDD2WordVectorRDD(value: RDD[WordVector]): WordVectorRDD = {
    new WordVectorRDD(value)
  }

  implicit def WordVectorRDD2RDD(value: WordVectorRDD): RDD[WordVector] = value.rdd

  def euclideanDistance(v1: Vector): Vector => Double = (v2: Vector) => Math.sqrt(Vectors.sqdist(v1, v2))

  def cosineSimilarity(v1: Vector): Vector => Double = {
    val norm = Vectors.norm(v1, 2.0)

    (v2: Vector) => ( v1 dot v2) / (norm * Vectors.norm(v2, 2.0))
  }

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
