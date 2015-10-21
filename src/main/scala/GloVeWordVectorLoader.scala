package gloving

import java.net.URI
import java.io.File

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter

import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}

import breeze.linalg.DenseVector

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

class GloVeWordVectorLoader(val path: URI) extends WordVectorLoader {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  def load(sc: SparkContext): WordVectorRDD = {
    val tuples = readTuples(sc, path)

    val name = new File(path.getPath()).getName
    new WordVectorRDD(tuples.map { case(index, word, vector) => WordVector(index, word, DenseVector[Double](vector)) })
      .setName(s"$name-wordvectors")
  }

  private def readTuples(sc: SparkContext, path: URI): RDD[(Long, String, Array[Double])] = {
    val name = new File(path.getPath()).getName

    val file = sc.textFile(path.toString)
    val tuples = file
      .zipWithIndex()
      .repartition(sc.defaultParallelism * 3) //Because gzip-ed textfiles aren't splittable natively, oops!
      .map{case(line,index) => (index, line.split(" "))}
      .map{case(index,arr) => (index, arr.head, arr.tail)}
      .map{case(index, word, vector) => (index, word, vector.map(_.toDouble))}

    tuples.setName(s"$name-tuples")
  }
}
