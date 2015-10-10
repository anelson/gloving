package gloving


import org.apache.spark.SparkContext

trait WordVectorLoader {
  def load(sc: SparkContext): WordVectorRDD
}