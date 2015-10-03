package gloving

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.linalg.Vector

case class WordVector(index: Long, word: String, vector: Vector)
