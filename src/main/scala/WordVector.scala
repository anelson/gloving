package gloving

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.linalg.{Vectors, Vector}

case class WordVector(index: Long, word: String, vector: Vector) {
	def l2norm: Double = {
		Vectors.norm(vector, 2.0)
	}

	def normalize: WordVector = {
    val norm = l2norm
    val normalizedArray = vector.toArray.map { value => value / norm }
    copy(vector = Vectors.dense(normalizedArray))
	}
}
