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

import gloving.Word2VecWordVectorLoader

class Word2VecWordVectorLoaderSpec extends FunSuite with Matchers {
  test("decodes the first word in the GoogleNews pretrained vectors") {
    val testFileUrl = getClass.getResource("/word2vec.head")
    val loader = new Word2VecWordVectorLoader(testFileUrl.toURI())
    val stream = new DataInputStream(new BufferedInputStream(new FileInputStream(testFileUrl.getPath())))

    val words = loader.readWords(stream)
    val wordVector = words.next()

    wordVector.word should be ("</s>") //Don't ask me why but this is the first word in the corpus from Google News

    //I got these values by modifying the word2vec code to dump the literal values from the first vector
    //Note the unusual hex representation of the float, preserves the value exactly with no imprecision due to base-10
    //representation
    wordVector.vector(0) should be ("0x1.28p-10".toFloat)
    wordVector.vector(1) should be ("-0x1.d6p-11".toFloat)
    wordVector.vector(298) should be ("-0x1.02p-13".toFloat)
    wordVector.vector(299) should be ("-0x1.6ap-14".toFloat)
  }

/*
  test("randomDataset has correct #rows") {
    val d = randomDataset(10)
//    d.collect().foreach(println)

    assert(d.collect().size === 10)
    assert(randomDataset(0).collect().size === 0)
  }

  test("randomInt properties: from is inclusive, to is exclusive") {
    val propInclusiveFrom = exists { (from: Int) =>
      randomInt(from, from + 10) == from
      randomInt(from, from + 1) == from
      randomInt(from, from + 0) == from
      randomInt(from, from + -1) == from
      randomInt(from, from + -10) == from
    }

    val propExclusiveTo = forAll { (from: Int, to: Int) =>
      if (from != to)
        randomInt(from, to) != to
      else
        randomInt(from, to) == to
    }

    check(propInclusiveFrom)
    check(propExclusiveTo)
  }

  val datasetGen =
    for (
      id <- arbitrary[String];
      ts <- choose(0L, System.currentTimeMillis());
      v <- arbitrary[Double];
      tag <- choose(1, 4)
    ) yield RandomRow(id, new Timestamp(ts), v, tag, "")

  test("tag range is in range") {
    check(
      forAll(datasetGen) { d =>
        d.tag >= 1 && d.tag < 5
      })
  }
*/
}
