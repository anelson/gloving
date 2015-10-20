package gloving.test

import scala.util.Random

import org.apache.spark.sql.test._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen._
import org.scalacheck.Prop.{exists, forAll}
import org.scalatest.FunSuite
import org.scalatest.prop.Checkers._
import org.scalatest._

import gloving.Top._

class TopSpec extends FunSuite with Matchers {
  test("returns the top integers from an array") {
    val data = (1 to 10)

    data.top(1) should be (Seq(10))
    data.top(2) should be (Seq(10, 9))
    data.top(5) should be ((10 to 6 by -1).toSeq)
    data.top(10) should be ((10 to 1 by -1).toSeq)
    data.top(20) should be ((10 to 1 by -1).toSeq)
  }

  test("returns the top integers from an array that isn't sorted") {
    val data = Random.shuffle((1 to 10))

    data.top(1) should be (Seq(10))
    data.top(2) should be (Seq(10, 9))
    data.top(5) should be ((10 to 6 by -1).toSeq)
    data.top(10) should be ((10 to 1 by -1).toSeq)
    data.top(20) should be ((10 to 1 by -1).toSeq)
  }

  test("returns the top integers from an iterable of arrays") {
    //Create a collection where each element is an array with five dimensions.  Each dimension
    //contains every integer from 1 to 10, but in a random order.  We will try to find the
    //top values in each dimension
    val data = List(
        Random.shuffle((1 to 10).toList),
        Random.shuffle((1 to 10).toList),
        Random.shuffle((1 to 10).toList),
        Random.shuffle((1 to 10).toList),
        Random.shuffle((1 to 10).toList)
        ).transpose.map(_.toArray)

    //For all of these tests, it's very important that we test against an Iterator, not the List type
    //With List, multiple passes can be made through the data, but the whole point of multiTop is
    //only one pass is required.  Thus, each of these calls operates on an iterator over data, not data itself
    data.toIterator.multiTop(1, 5) should be ( Array.fill(5)(Seq(10)) )
    data.toIterator.multiTop(2, 5) should be ( Array.fill(5)(Seq(10, 9)) )
    data.toIterator.multiTop(5, 5) should be ( Array.fill(5)((10 to 6 by -1).toSeq) )
    data.toIterator.multiTop(10, 5) should be ( Array.fill(5)((10 to 1 by -1).toSeq) )
    data.toIterator.multiTop(20, 5) should be ( Array.fill(5)((10 to 1 by -1).toSeq) )
  }
}
