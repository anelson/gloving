package gloving

import org.apache.spark.mllib.linalg.{Vectors, Vector}

object VectorImplicits {
	implicit class PimpedVector(val vector: Vector) extends AnyVal {
		def -(that: PimpedVector): Vector = {
			assert(vector.size == that.vector.size)

			elementsizeOp(that, (x, y) => x - y)
		}

		def +(that: PimpedVector): Vector = {
			assert(vector.size == that.vector.size)

			elementsizeOp(that, (x, y) => x + y)
		}

		def dot(that: PimpedVector): Double = {
			assert(vector.size == that.vector.size)

	    //Can use functional hotness like zip, but this avoids copying the contents into a new array
	    var dp: Double = 0.0

	    for (i <- 0 until vector.size) { dp += vector(i) * that.vector(i) }

	    dp
		}

		def elementsizeOp(that: PimpedVector, op: (Double, Double) => Double): Vector = {
			assert(vector.size == that.vector.size)

			val resultArray = Array.tabulate(vector.size) { i: Int => op(vector(i), that.vector(i)) }

			Vectors.dense(resultArray)
		}
	}
}