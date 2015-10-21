package gloving

import scala.collection._
import scala.collection.generic._
import scala.collection.mutable.SortedSet
import scala.collection.mutable.ArrayOps

trait Topper[A] {
	var minValue: Option[A] = None

	def add(e: A)

	def values: Seq[A]
}

object Topper {
	def apply[A](n: Int)(implicit ord: Ordering[A]): Topper[A] = {
		//Choose the topper implementation that gives the best performance
		require(n > 0)

		if (n == 1) {
			new SingleTopper[A]()
		} else {
			new MultiTopper[A](n)
		}
	}
}

/** Implementation of Topper that gets n top values */
class MultiTopper[A](n: Int)(implicit ord: Ordering[A]) extends Topper[A] {
	val topValues = SortedSet[A]()

	def add(e: A) {
		if (topValues.size < n) {
  		topValues += e

  		minValue = Some(topValues.head)
  	} else if (ord.gt(e, minValue.get)) {
  		//If e is greater than the lowest value we've seen  yet, bump the lowest value and replace it with e
    	topValues -= minValue.get
    	topValues += e

    	minValue = Some(topValues.head)
    }
	}

	def values = topValues.toSeq.reverse //The SortedSet stores values from lowest to highest, but this is top n so we want highest to lowest
}

/** Specialization of Topper that gets only one top value.  This saves a tiny bit of
overhead by avoiding recomputing the min value on each add.  It's not worth it unless you're doing it
countless millions of time in a tight loop, which we do */
class SingleTopper[A]()(implicit ord: Ordering[A]) extends Topper[A] {
	def add(e: A) {
		if (minValue.isEmpty || ord.gt(e, minValue.get)) {
  		//If e is greater than the lowest value we've seen  yet, bump the lowest value and replace it with e
  		minValue = Some(e)
    }
	}

	def values: Seq[A] = minValue.toSeq
}

object Top {
	implicit class ToppableTraversable[A](val self: GenTraversableOnce[A]) extends AnyVal {
		def top(n: Int)(implicit ord: Ordering[A]): Seq[A] = {
			val topper = Topper[A](n)

	    self.foreach(topper.add(_))

	    topper.values
		}
	}

	implicit class ToppableTraversableArray[A](val self: GenTraversableOnce[Array[A]]) extends AnyVal {
		/** How's this for mind-bending: if you have something traversable which contains Arrays, assuming each Array
		has the same size, this method computes the top N values for each dimension of the arrays.  Boom. */
		def multiTop(n: Int, dimensions: Int)(implicit ord: Ordering[A]): Array[Seq[A]] = {
			require(n > 0)
			require(dimensions > 0)

			var toppers = Array.fill(dimensions) { Topper[A](n) }

	    self.foreach { arr =>
	    	//arr is an array of elements of type A
	    	require(arr.length == dimensions)
	    	for (idx <- 0 until dimensions) {
	    		toppers(idx).add(arr(idx))
	    	}
	    }

	    toppers.map(_.values)
		}
	}

	implicit class ToppableArray[A](val self: Array[A]) extends AnyVal {
		def top(n: Int)(implicit ord: Ordering[A]): Seq[A] = {
			//TODO: Get a PhD so I can understand Scala's collection library and implement a generic method that works
			//on Array as well as Iterator types without resorting to this hack
			val ops: ArrayOps[A] = self

			ops.top(n)
		}
	}
}