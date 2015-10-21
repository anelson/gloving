package gloving

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

		def elementsizeOp(that: PimpedVector, op: (Double, Double) => Double): BDenseVector[Double] = {
			assert(vector.size == that.vector.size)

			val resultArray = Array.tabulate(vector.size) { i: Int => op(vector(i), that.vector(i)) }

			new BDenseVector(resultArray)
		}
	}
}
