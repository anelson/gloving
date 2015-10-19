package gloving

import scala.io.Source;

import java.net.URI
import java.io.{File, Serializable}

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.linalg.{DenseVector, Vector}

import play.api.libs.json._
import play.api.libs.functional.syntax._

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

case class Analogy(source: String, target: String)

case class AnalogyProblem(example: Analogy,
  test: Analogy) {
}

case class WordDistance(word: String, distance: Double)

object AnalogyProblem {
  def fromTextFile(path: URI): Seq[AnalogyProblem] = {
    val name = new File(path.getPath()).getName

    val src = Source.fromFile(path)
    try {
      val problems = src.getLines().map { line =>
        val components = line.split(" ")

        AnalogyProblem( Analogy(components(0), components(1)), Analogy(components(2), components(3)))
      }

      problems.toList
    } finally {
      src.close()
    }
  }
}

case class AnalogyResult(problem: AnalogyProblem,
  euclideanAnswer: WordDistance,
  cosineAnswer: WordDistance)

case class AnalogyResults(testFile: String,
  testCount: Int,
  euclideanAccuracy: Double,
  cosineAccuracy: Double,
  incorrectResults: Seq[AnalogyResult])

case class VectorEvaluation(analogyResults: Seq[AnalogyResults])

object VectorEvaluation {
  implicit val analogyFmt = Json.format[Analogy]
  implicit val problemFmt = Json.format[AnalogyProblem]
  implicit val distanceFmt = Json.format[WordDistance]
  implicit val resultFmt = Json.format[AnalogyResult]
  implicit val resultsFmt = Json.format[AnalogyResults]
  implicit val evaluationFmt = Json.format[VectorEvaluation]
}
