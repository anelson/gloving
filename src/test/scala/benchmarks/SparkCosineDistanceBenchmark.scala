package gloving.test.benchmarks

import scala.util.Random

import org.scalameter.api._

import org.apache.spark.mllib.linalg.{ Vectors, Vector }

import gloving.WordVector
import gloving.WordVectorRDD

object SparkCosineDistanceBenchmark extends DistanceBenchmark {
  val vectors = for {
    n <- ns
  } yield {
    //Generate one random vector to be the left operand
    val (x1, x2s) = generateVectors(n)

    val v1 = Vectors.dense(x1)
    val v2s = x2s.map(x2 => Vectors.dense(x2))

    (v1, v2s)
  }

  def dot(v1: Vector, v2: Vector): Double = {
    require(v1.size == v2.size)

    var i = 0
    var size = v1.size
    var dp: Double = 0.0

    while (i < size) {
      dp += v1(i) * v2(i)
      i+=1
    }

    dp
  }

  performance of "Spark" in {
    measure method "cosineDistance" in {
      using(vectors) in { case (v1, v2s) =>
        val norm = Vectors.norm(v1, 2)

        val distanceFunc: (Vector) => Double = (v2) => dot(v1, v2) / (norm * Vectors.norm(v2, 2))

        val distances = v2s.map(v2 => distanceFunc(v2))

        //Touch the result value to ensure this computation doesn't get optimized away
        require(distances.sum != 0)
      }
    }
  }
}
