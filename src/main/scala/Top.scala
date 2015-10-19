package gloving

import scala.collection.mutable.SortedSet

object Top {
	implicit class ToppableTraversable[T](val self: TraversableOnce[T]) extends AnyVal {
		def top(n: Int)(implicit ord: Ordering[T]): Seq[T] = {
			var topValues = SortedSet[T]()

	    var min: Option[T] = None

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
}