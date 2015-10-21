package gloving.test.benchmarks

import scala.util.Random

import org.scalameter.api._

import breeze.linalg.DenseVector

import gloving.WordVector
import gloving.WordVectorRDD

trait DistanceBenchmark extends Bench.LocalTime {
  val ns = Gen.range("n")(50, 300, 50)
	val random = new Random(42)
  val NumberOfVectors = 100000

  def generateVectors(n: Int): (Array[Double], Array[Array[Double]]) = {
    val x1 = Array.fill(n)(random.nextDouble() * 20 - 10)

    //Generate many more to be the right operand; we'll compute the distance from x1 to each of the x2s
    val x2s = Array.fill(NumberOfVectors) { Array.fill(n)(random.nextDouble() * 20 - 10) }

    (x1, x2s)
  }
}
