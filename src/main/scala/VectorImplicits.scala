package gloving

import com.github.fommil.netlib.{BLAS => NetlibBLAS, F2jBLAS}

import org.apache.spark.mllib.linalg.{Vectors, Vector, DenseVector}

object VectorImplicits {
	@transient private var _f2jBLAS: NetlibBLAS = _

	/** These two methods I stolre from org.apache.spark.mllib.linalgBLAS, which for some reason is private so I can't use it directly */
	private def f2jBLAS: NetlibBLAS = {
    if (_f2jBLAS == null) {
      _f2jBLAS = new F2jBLAS
    }
    _f2jBLAS
  }

  def dot(x: Vector, y: Vector): Double = {
    require(x.size == y.size,
      "BLAS.dot(x: Vector, y:Vector) was given Vectors with non-matching sizes:" +
      " x.size = " + x.size + ", y.size = " + y.size)
    (x, y) match {
      case (dx: DenseVector, dy: DenseVector) =>
        dot(dx, dy)
      case _ =>
        throw new IllegalArgumentException(s"dot doesn't support (${x.getClass}, ${y.getClass}).")
    }
  }

  private def dot(x: DenseVector, y: DenseVector): Double = {
    val n = x.size
    f2jBLAS.ddot(n, x.values, 1, y.values, 1)
  }

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
			VectorImplicits.dot(vector, that.vector)
		}

		def elementsizeOp(that: PimpedVector, op: (Double, Double) => Double): Vector = {
			assert(vector.size == that.vector.size)

			val resultArray = Array.tabulate(vector.size) { i: Int => op(vector(i), that.vector(i)) }

			Vectors.dense(resultArray)
		}
	}
}