package gloving.test.benchmarks

import scala.util.Random

import org.scalameter.api._

import breeze.linalg.DenseVector

import gloving.WordVector
import gloving.WordVectorRDD

object BreezeCosineDistanceBenchmark extends DistanceBenchmark {
  val vectors = for {
    n <- ns
  } yield {
  	//Generate one random vector to be the left operand
  	val (x1, x2s) = generateVectors(n)

  	val v1 = DenseVector[Double](x1)
  	val v2s = x2s.map(x2 => DenseVector[Double](x2))

  	(v1, v2s)
  }

  performance of "Breeze" in {
    measure method "cosineDistance" in {
      using(vectors) in { case (v1, v2s) =>
        val distanceFunc: (DenseVector[Double]) => Double = { x2 => breeze.linalg.functions.cosineDistance(v1, x2) }

        val distances = v2s.map(v2 => distanceFunc(v2))

        //Touch the result value to ensure this computation doesn't get optimized away
        require(distances.sum != 0)
      }
    }
  }
}
