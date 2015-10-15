package gloving.test

import java.net.URI
import java.io.{File, FileInputStream, BufferedInputStream, DataInputStream, InputStream}

import org.apache.spark.sql.test._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen._
import org.scalacheck.Prop.{exists, forAll}
import org.scalatest.FunSuite
import org.scalatest.prop.Checkers._
import org.scalatest._

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import gloving.Statistics

class VectorStatisticsSpec extends FunSuite with Matchers {
  test("computes stats on a simple list") {
    val data = (-10.0 to 10.0 by 1.0).toArray
    val desc = new DescriptiveStatistics(data)
    val stats = Statistics.fromDescriptiveStatistics(desc, 10)

    stats.min should be(-10.0)
    stats.max should be(10.0)
    stats.mean should be(0.0)
    stats.percentiles("50") should be (0.0)
    stats.histogram should be(Array(2, 2, 2, 2, 2, 2, 2, 2, 2, 3))
  }
}
