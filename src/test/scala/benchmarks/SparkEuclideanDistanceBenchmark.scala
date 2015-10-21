package gloving.test.benchmarks

import scala.util.Random

import org.scalameter.api._

import org.apache.spark.mllib.linalg.{ Vectors, Vector }

import gloving.WordVector
import gloving.WordVectorRDD

object SparkEuclideanDistanceBenchmark extends DistanceBenchmark {
  val vectors = for {
    n <- ns
  } yield {
    //Generate one random vector to be the left operand
    val (x1, x2s) = generateVectors(n)

    val v1 = Vectors.dense(x1)
    val v2s = x2s.map(x2 => Vectors.dense(x2))

    (v1, v2s)
  }

  performance of "Spark" in {
    measure method "euclideanDistance" in {
      using(vectors) in { case (v1, v2s) =>
        val distanceFunc: (Vector) => Double = { x2 => Math.sqrt(Vectors.sqdist(v1, x2)) }

        val distances = v2s.map(v2 => distanceFunc(v2))

        //Touch the result value to ensure this computation doesn't get optimized away
        require(distances.sum != 0)
      }
    }
  }
}
