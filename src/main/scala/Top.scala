package gloving

import scala.collection._
import scala.collection.generic._
import scala.collection.mutable.SortedSet
import scala.collection.mutable.ArrayOps

object Top {
	implicit class ToppableTraversable[A](val self: GenTraversableOnce[A]) extends AnyVal {
		def top(n: Int)(implicit ord: Ordering[A]): Seq[A] = {
			var topValues = SortedSet[A]()

	    var min: Option[A] = None

	    self.foreach { e =>
	    	if (topValues.size < n) {

	    		topValues += e

	    		min = Some(topValues.head)

	    	} else if (ord.gt(e, min.get)) {
	    		//If e is greater than the lowest value we've seen  yet, bump the lowest value and replace it with e

	      	topValues -= min.get
	      	topValues += e


	      	min = Some(topValues.head)
	      }
	    }

	    topValues.toSeq.reverse //The SortedSet stores values from lowest to highest, but this is top n so we want highest to lowest
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