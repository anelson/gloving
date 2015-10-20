package gloving

import scala.collection._
import scala.collection.generic._
import scala.collection.mutable.SortedSet
import scala.collection.mutable.ArrayOps

object Top {
	implicit class ToppableTraversable[A](val self: GenTraversableOnce[A]) extends AnyVal {
		def top(n: Int)(implicit ord: Ordering[A]): Seq[A] = {
			var topValues = SortedSet[A]()

	    self.foreach { e =>
	    	if (topValues.size < n) {

	    		topValues += e

	    	} else if (ord.gt(e, topValues.head)) {
	    		//If e is greater than the lowest value we've seen  yet, bump the lowest value and replace it with e

	      	topValues -= topValues.head
	      	topValues += e
	      }
	    }

	    topValues.toSeq.reverse //The SortedSet stores values from lowest to highest, but this is top n so we want highest to lowest
		}
	}

	implicit class ToppableTraversableArray[A](val self: GenTraversableOnce[Array[A]]) extends AnyVal {
		/** How's this for mind-bending: if you have something traversable which contains Arrays, assuming each Array
		has the same size, this method computes the top N values for each dimension of the arrays.  Boom. */
		def multiTop(n: Int, dimensions: Int)(implicit ord: Ordering[A]): Array[Seq[A]] = {
			require(n > 0)
			require(dimensions > 0)

			var topValues = Array.fill(dimensions) { SortedSet[A]() }

	    self.foreach { arr =>
	    	//arr is an array of elements of type A
	    	require(arr.length == dimensions)

	    	arr.zipWithIndex.foreach { case (e, index) =>
	    		//e is the latest value of dimension 'index'
		    	if (topValues(index).size < n) {

		    		topValues(index) += e
		    	} else if (ord.gt(e, topValues(index).head)) {
		    		//If e is greater than the lowest value we've seen  yet, bump the lowest value and replace it with e

		      	topValues(index) -= topValues(index).head
		      	topValues(index) += e
		      }
	    	}
	    }

	    topValues.map(_.toList.reverse) //The SortedSet stores values from lowest to highest, but this is top n so we want highest to lowest
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