package gloving.test.benchmarks

import scala.util.Random

import org.scalameter.api._

import breeze.linalg.{DenseVector, normalize}

import gloving.WordVector
import gloving.WordVectorRDD

object BreezeDotProductBenchmark extends DistanceBenchmark {
  val vectors = for {
    n <- ns
  } yield {
  	//Generate one random vector to be the left operand
  	val (x1, x2s) = generateVectors(n)

  	val v1 = normalize(DenseVector[Double](x1))
  	val v2s = x2s.map(x2 => normalize(DenseVector[Double](x2)))

  	(v1, v2s)
  }

  performance of "Breeze" in {
    measure method "dotProduct" in {
      using(vectors) in { case (v1, v2s) =>
        val dps = v2s.map(v2 => v1 dot v2)

        //Touch the result value to ensure this computation doesn't get optimized away
        require(dps.sum != 0)
      }
    }
  }
}
