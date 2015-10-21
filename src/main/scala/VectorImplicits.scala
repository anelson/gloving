package gloving

import scala.language.implicitConversions

import org.scalautils._
import org.scalautils.TripleEquals._
import org.scalautils.Tolerance._

import breeze.linalg.{ DenseVector => BDenseVector }
import org.apache.spark.mllib.linalg.{ DenseVector => SDenseVector }

object VectorImplicits {
	implicit def sparkToBreezeVector(v: SDenseVector): BDenseVector[Double] = {
		//As of this writing, toArray just returns the immutable array backing the vector, so this should be a cheap operation
		BDenseVector(v.toArray)
	}

	implicit def breezeToSparkVector(v: BDenseVector[Double]): SDenseVector = {
		new SDenseVector(v.data)
	}

	implicit class PimpedSparkVector(val vector: SDenseVector) extends AnyVal {
		def toBreeze: BDenseVector[Double] = { vector }
	}

	implicit class PimpedVector(val vector: BDenseVector[Double]) extends AnyVal {
		def toSpark: SDenseVector = { vector }

		def -(that: PimpedVector): BDenseVector[Double] = {
			assert(vector.size == that.vector.size)

			elementsizeOp(that, (x, y) => x - y)
		}

		def +(that: PimpedVector): BDenseVector[Double] = {
			assert(vector.size == that.vector.size)

			elementsizeOp(that, (x, y) => x + y)
		}

		def dot(that: PimpedVector): Double = {
			vector dot that.vector
		}

		def computeNorm(): Double = {
			breeze.linalg.norm(vector)
		}

		def normalize(): BDenseVector[Double] = {
			breeze.linalg.normalize(vector)
		}

		def isNormalized: Boolean = {
			//Due to rounding errors a normalized vector will usually not be exactly 1.0 norm, but it should be close
			computeNorm() === 1.0 +- 1E-10
		}

		def elementsizeOp(that: PimpedVector, op: (Double, Double) => Double): BDenseVector[Double] = {
			assert(vector.size == that.vector.size)

			val resultArray = Array.tabulate(vector.size) { i: Int => op(vector(i), that.vector(i)) }

			new BDenseVector(resultArray)
		}
	}
}
