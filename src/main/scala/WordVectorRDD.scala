package gloving

import scala.language.implicitConversions

import java.net.URI
import java.io.{File, Serializable}

import org.apache.spark.Partitioner
import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SaveMode
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter

import org.apache.spark.mllib.linalg.{ DenseVector => SDenseVector }

import breeze.linalg.DenseVector

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
    val norms = rdd.map { wv => breeze.linalg.norm(wv.vector, 2) }
    val desc = new DescriptiveStatistics(norms.collect())
    Statistics.fromDescriptiveStatistics(desc, histogramBins)
  }

  def toUnitVectors(): WordVectorRDD = {
    rdd.map(_.normalize)
  }

  def findAll(predicate: (WordVector) => Boolean): Array[WordVector] = {
    rdd.filter(predicate).collect()
  }

  /** Finds the first n words which match the specified predicate, where the results are sorted
  in ascending order of index, which means in descending order of popularity in the training corpus */
  def find(predicate: (WordVector) => Boolean, n: Int): Seq[WordVector] = {
    import Top._

    //Use an ordering that considers index 1 to be higher than index 2.  An easy way to do this is negatve them
    implicit val ordering: Ordering[WordVector] = Ordering.by(wv => -wv.index)

    findAll(predicate).top(n)
  }

  /** Performs a search for the word, matching without regard to case unless caseSensitive is set to true. */
  def findWord(word: String, caseSensitive: Boolean = false):  Option[WordVector] = {
    val filterFunction: (WordVector) => Boolean =
      if (caseSensitive) { (wv: WordVector) => wv.word == word }
      else { (wv: WordVector) => wv.word.equalsIgnoreCase(word) }

    val matches = find(WordVectorRDD.stringMatch(word, caseSensitive), 1)

    //In almost all cases, there is only one match.  However in the event of an ambiguity, use the one
    //with the lowest index value.  That should mean it's the more common one, which is probably what the caller wants
    matches.headOption
  }

  /* In Scala, as in life, vectorization is critical to good performance.  Word lookups are expensive, so
  its better to do them all at once.  This vectorized version of findWord looks up a bunch of words, and returns a
  map mapping the words to the corresponding WordVector.  If one of the words isn't found, it simply doesn't appear in
  the map */
  def findWords(words: Set[String], caseSensitive: Boolean = false): Map[String, WordVector] = {
    val bWords = rdd.context.broadcast(words)

    val filterFunction: (WordVector) => Boolean =
      if (caseSensitive) {  (wv: WordVector) => bWords.value.contains(wv.word) }
      else { (wv: WordVector) => bWords.value.exists(word => wv.word.equalsIgnoreCase(word)) }

    val mapFunction: (WordVector) => (String, WordVector) =
      if (caseSensitive) {  (wv: WordVector) => (wv.word, wv) }
      else { (wv: WordVector) => (bWords.value.find(word => wv.word.equalsIgnoreCase(word)).get, wv) }

    val wordMap: Seq[(String, WordVector)] = findAll(filterFunction).map(mapFunction)

    bWords.unpersist

    //For case-sensitive matches, we're done.  For case-insensitive, wordMap could have multiple matches for each word,
    //with different cases.  For each word we must choose the most appropriate match
    if (caseSensitive) {
      wordMap.toMap
    } else {
      wordMap.groupBy(_._1).map { case (word, matches) =>
        //A given word can have multiple matches.  Pick the match that has the lowest index
        (word, matches.minBy(_._2.index)._2)
      }.toMap
    }
  }

  def findNearest(n: Int, distanceFunction: (DenseVector[Double]) => Double, lowerIsBetter: Boolean): Array[(WordVector, Double)] = {
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

  /** Finds the nearest n words for multiple distance functions in parallel.  This may be more efficient than querying one at a time
  if you have a lot of these nearest queries to perform */
  def findNearestMulti(n: Int, distanceFunctions: Array[(DenseVector[Double]) => Double], lowerIsBetter: Boolean): Array[Array[(WordVector, Double)]] = {
    require(n > 0)

    import gloving.Top._

    //Create an ordering for the results that we can pass to top.  Top is looking for the 'largets' n elements,
    //so if lowerIsBetter is true, that means we need an ordering that places lower values as 'greater than' higher values,
    //and vice versa is lowerIsBetter is false.  That's why the ordering below seems like it's exactly the opposite of what you
    //would want if you were sorting the results and taking the first n
    implicit val ordering: Ordering[(WordVector, Double)] = Ordering.by(
      if (lowerIsBetter) { (r) => -r._2 }
      else { (r) => r._2 }
    )

    //Compute the distances between this vector and all the word vectors
    //Note that as an optimization we do not include the actual vector of the WordVectors here.  We dont' need it and it wastes memory
    val distances: RDD[(WordVector, Array[Double])] = rdd.map { wv =>
      val distances = distanceFunctions.map(_(wv.vector))

      (wv.copy(vector = null), distances)
    }.setName(s"${rdd.name}-top${n}-${distanceFunctions.length}distances")

    //Within each partition, find the top n matches for each of the distance functions.
    //The key is to avoid traversing the 'distances' RDD more than once
    val topCandidates: RDD[Array[(WordVector, Double)]] = distances.mapPartitions { rows =>
      //Right now 'rows' is an Iterator of tuples.  Each tuple is a WordVector in the RDD, and
      //an array of distances, one for each distance function.  The task is to find the top n WordVectors for each
      //distance function.
      //
      //Complicating matters is that 'rows' is an Iterator, meaning we can make only one pass over the data.
      //Normally a quick toArray clears this up, but rows can have tens of thousands up to millions of rows in it,
      //and we can't assume it fits in memory.  Thus, contort ourselves in order to perform the top n calculation on each
      //of the distances individually, with only one pass through the iterator

      //Right now, the type yielded by rows is (WordVector, Array[Double]).  We need it to be an Array of (WordVector, Double) tuples,
      //so we can do a top n computation on each column separately.
      val rowsArray: Iterator[Array[(WordVector, Double)]] = rows.map(row => row._2.map(score => (row._1, score)))

      val topMatches: Array[Array[(WordVector, Double)]] = rowsArray.multiTop(n, distanceFunctions.length).map(_.toArray)

      //Don't make any assumptions about the partitioning scheme.  The partition could have less than n elements
      val numMatches: Int = if (n > topMatches.head.length) { topMatches.head.length} else { n }

      //topMatches has one element per distanceFunction, each element is a list of top matches for that distance function.
      //Transpose that to an array with one element for each of the top n matches, each element is an array of matches, one per distance function
      Array.tabulate[Array[(WordVector, Double)]](numMatches) { rowIndex =>
        distanceFunctions.zipWithIndex.map { case (_, columnIndex) =>
          topMatches(columnIndex)(rowIndex)
        }
      }.toIterator
    }.setName(s"${rdd.name}-top${n}-${distanceFunctions.length}topCandidates")

    //The topCandidates RDD will contain n rows for every partition in the rdd.  In amongst all those rows will be the n best matches
    //for each distance function.  Fortunately, n*partitionCount is very likely to be a small number, no more than a thousand or two at the very most,
    //so the easy thing to do is to collect it down to the driver program and process the data there
    val rows: Array[Array[(WordVector, Double)]] = topCandidates.collect()

    distanceFunctions.zipWithIndex.map { case (_, columnIndex) =>
      rows.map(row => row(columnIndex)).top(n).toArray
    }
  }

  def findNearestEuclidean(vector: DenseVector[Double], n: Int): Array[(WordVector, Double)] = findNearest(n, WordVectorRDD.euclideanDistance(vector), true)

  def findNearestCosine(vector: DenseVector[Double], n: Int): Array[(WordVector, Double)] = {
    findNearest(n, WordVectorRDD.cosineSimilarity(vector), false)
  }

  def save(path: URI, mode: SaveMode = SaveMode.Overwrite) {
    //Note we save as a Spark vector since Breeze vectors can't serialzie
    import VectorImplicits._

    val sqlContext = new SQLContext(rdd.context)
    import sqlContext.implicits._

    logger.info(s"Saving ${name} to $path")
    rdd.map(wv => (wv.index, wv.word, wv.vector.toSpark)).toDF().write.mode(mode).format("parquet").save(path.toString())
  }
}

object WordVectorRDD {
  import VectorImplicits._
  import breeze.linalg.functions._

  implicit def RDD2WordVectorRDD(value: RDD[WordVector]): WordVectorRDD = {
    new WordVectorRDD(value)
  }

  implicit def WordVectorRDD2RDD(value: WordVectorRDD): RDD[WordVector] = value.rdd

  def stringMatch(word: String, caseSensitive: Boolean = false): (WordVector) => Boolean = {
    if (caseSensitive) { (wv: WordVector) => wv.word == word }
    else { (wv: WordVector) => wv.word.equalsIgnoreCase(word) }
  }

  def euclideanDistance(v1: DenseVector[Double]): DenseVector[Double] => Double = {
    (v2: DenseVector[Double]) => breeze.linalg.functions.euclideanDistance(v1, v2)
  }

  def cosineSimilarity(v1: DenseVector[Double]): DenseVector[Double] => Double = {
    val norm = breeze.linalg.norm(v1, 2)

    (v2: DenseVector[Double]) => (v1 dot v2) / (norm * breeze.linalg.norm(v2, 2))
  }

  /** If we know all vectors in the model are normalized, we can avoid computing the norms and thus
  cosine similarity reduces to dot product */
  def normalizedCosineSimilarity(v1: DenseVector[Double]): DenseVector[Double] => Double = {
    require(v1.isNormalized)

    (v2: DenseVector[Double]) => v1 dot v2
  }

	def load(sc: SparkContext, path: URI): WordVectorRDD = {
    val name = new File(path.getPath()).getName
    val sqlContext = new SQLContext(sc)
    sqlContext.read.parquet(path.toString())
      .map(row => WordVector.apply(row.getLong(0), row.getString(1), row.getAs[SDenseVector](2).toBreeze))
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
