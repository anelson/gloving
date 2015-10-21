package gloving

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.DenseVector

import breeze.linalg.{ DenseVector => BDenseVector }

case class WordVector(index: Long, word: String, vector: BDenseVector[Double]) {
	import VectorImplicits._

	def normalize: WordVector = {
    copy(vector = breeze.linalg.normalize(vector))
	}
}

