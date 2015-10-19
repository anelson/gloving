package gloving.test

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
}
