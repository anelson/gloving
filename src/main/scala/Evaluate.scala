package gloving

import java.net.{URI, URL}
import java.io.{File, PrintWriter}

import scala.collection.parallel.ParSeq
import scala.collection.immutable.Map

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

import breeze.linalg.{ DenseVector }

import play.api.libs.json._
import play.api.libs.json.Json._

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import gloving.WordVectorRDD._
import VectorImplicits._

object Evaluate {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  case class CliOptions(questionFilesPath: File = null,
    vectorUrls: Seq[URI] = Seq(),
    normalized: Boolean = false,
    outputUrl: URI = new URI("./vector-evaluation.json"))

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("evaluate") {
      head("gloving", "SNAPSHOT")
      arg[File]("question-files") required() action { (x, c) =>
        c.copy(questionFilesPath = x) } text("Name of directory containing analogy problem question files")
      arg[URI]("vector file [vector file...]") unbounded() required() action { (x, c) =>
        c.copy(vectorUrls = c.vectorUrls :+ x) } text("URLs or paths to vector files created by the load command")
      opt[Unit]('n', "normalized") optional() action { (x, c) =>
        c.copy(normalized = true) } text("Assume the vector files are normalized.  Evaluation is MUCH faster on normalized vectors.")
      opt[URI]('o', "output") optional() action { (x, c) =>
        c.copy(outputUrl = x) } text("Path and file name to which the evaluation JSON file is written")
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving-evaluate")
    val sc = new SparkContext(SparkHelpers.config(conf))

    evaluate(sc, config)
  }

  def evaluate(sc: SparkContext, config: CliOptions) {
    logger.info(s"Loading analogy problems from problem folder ${config.questionFilesPath}")

    val analogyProblems = config.questionFilesPath.listFiles.filter(_.isFile).map { file =>
      (file.getName -> AnalogyProblem.fromTextFile(file.toURI))
    }.toMap

    val resultsMap = config.vectorUrls.map { vectorUrl =>
      val name = new File(vectorUrl.getPath()).getName

      val words = WordVectorRDD.load(sc, vectorUrl).cache()

      logger.info(s"Evaluating vectors $name")

      val results = evaluate(config, words, analogyProblems)

      words.unpersist()

      (name, results)
    }

    //analysis is a collection of (name, analysis) tuples.  Convert it into
    //a JSON map with 'name' as the key
    import gloving.VectorEvaluation._

    val jsonAnalyses = resultsMap.map{ case(k,v) => (k, Json.toJson(v)) }
    val jsonObj = JsObject(jsonAnalyses.toSeq)
    val json = Json.prettyPrint(jsonObj)

    val file = new File(config.outputUrl.toString())
    new PrintWriter(file) { write(json); close }

    logger.info(s"Wrote analysis to $file")
  }

  def evaluate(config: CliOptions, words: WordVectorRDD, problemSet: Map[String, Seq[AnalogyProblem]]): VectorEvaluation = {
    //Most words in the problem set appear in many problems.  As an optimization, look up the word vectors of all of the words
    //up front in one operation
    val wordVectors = words.findWords(getUniqueWords(problemSet))

    if (config.normalized) {
      //The vectors are expected to be normalized.  It's not practical to check them all, but at least hceck the first one
      wordVectors.headOption.map { case (key, wv) =>
        require(wv.vector.isNormalized,
          s"Expecting the vector file ${words.name} to be normalized, but the vector for word '${wv.word}' has a norm of ${wv.vector.computeNorm()}!")
      }
    }

    val results: Iterable[AnalogyResults] = problemSet.map { case (name, problems) =>
      computeAnalogyResults(solveAnalogyProblems(config, words, wordVectors, problems),
        name)
    }

    VectorEvaluation(results.toSeq)
  }

  def computeAnalogyResults(results: Seq[AnalogyResult], name: String): AnalogyResults = {
    //Compute the aggregate statistics for all of the problems
    val euclideanResults = results.map(r => (r.problem.test.target, r.euclideanAnswer))
    val euclideanPerformance = computeAlgorithmPerformance(euclideanResults)

    val cosineResults = results.map(r => (r.problem.test.target, r.cosineAnswer))
    val cosinePerformance = computeAlgorithmPerformance(cosineResults)

    val incorrectResults = results
      .filter(result => !result.problem.test.target.equalsIgnoreCase(result.euclideanAnswer.word) || !result.problem.test.target.equalsIgnoreCase(result.cosineAnswer.word))

    AnalogyResults(name,
      results.length,
      euclideanPerformance,
      cosinePerformance,
      incorrectResults.seq)
  }

  /** Given a list of expected answers, actual answers, and distances, computes the accuracy, and statistical distribuion
  of the distances overall, correct, and incorrect */
  def computeAlgorithmPerformance(results: Seq[(String, WordDistance)]): AlgorithmAnalogyPerformance = {
    val correctResults = results.filter(r => r._1.equalsIgnoreCase(r._2.word))
    val incorrectResults = results.filter(r => !r._1.equalsIgnoreCase(r._2.word))

    val resultCount = results.length
    val accuracy = correctResults.length.toDouble / resultCount.toDouble

    val allDistances = results.map(_._2.distance)
    val correctDistances = correctResults.map(_._2.distance)
    val incorrectDistances = incorrectResults.map(_._2.distance)

    val stats = Statistics.fromDescriptiveStatistics(new DescriptiveStatistics(allDistances.toArray))

    //If accuracy was 0 %, there are no correct stats, and if it's 100%, there are no incorrect stats
    val correctStats = accuracy match {
      case x if x > 0.0 => Statistics.fromDescriptiveStatistics(new DescriptiveStatistics(correctDistances.toArray))
      case _ => Statistics.empty
    }

    val incorrectStats = accuracy match {
      case x if x < 1.0 => Statistics.fromDescriptiveStatistics(new DescriptiveStatistics(incorrectDistances.toArray))
      case _ => Statistics.empty
    }

    AlgorithmAnalogyPerformance(accuracy, stats, correctStats, incorrectStats)
  }

  def solveAnalogyProblems(config: CliOptions, words: WordVectorRDD, wordVectors: Map[String, WordVector], problems: Seq[AnalogyProblem]): Seq[AnalogyResult] = {
    //Make an array of all of the AnalogyProblem items, and the vector whose nearest neighbor is expected to be the answer to that problem
    val problemsWithVector: Array[(AnalogyProblem, DenseVector[Double])] = problems.map(x => (x, computeQueryVector(wordVectors, x)))
      .flatMap { case (problem, vector) =>
        vector match {
          case None => {
            logger.error(s"Unable to find word vectors for one or more words in analogy problem $problem")
            None
          }

          case Some(v) => Some(problem, v)
        }
      }.toArray

    //Make an array of distance functions, one for each problem
    val euclideanDistanceFunctions: Array[DenseVector[Double] => Double] = problemsWithVector.map { case (_, vector) => WordVectorRDD.euclideanDistance(vector) }
    val cosineDistanceFunctions: Array[DenseVector[Double] => Double] = problemsWithVector.map { case (_, vector) =>
      if (config.normalized) {
        //We assume all of the vectors in the RDD are normalized.  Query vectors are not necessarily normalized, so
        //normalize them first
        WordVectorRDD.normalizedCosineSimilarity(vector.normalize)
      } else {
        WordVectorRDD.cosineSimilarity(vector)
      }
    }

    //Run the queries.
    val euclideanResults = words.findNearestMulti(1,
        euclideanDistanceFunctions,
        true)

    val cosineResults = words.findNearestMulti(1,
        cosineDistanceFunctions,
        false)

    //Combine the results and build the AnalogyResult objects
    val completedResults = problems.zip(euclideanResults.zip(cosineResults)).map { case (problem, (euclideanResult, cosineResult)) =>
      AnalogyResult(problem,
        WordDistance(euclideanResult.head._1.word, euclideanResult.head._2),
        WordDistance(cosineResult.head._1.word, cosineResult.head._2))
    }

    val incompletedResults = problems.filter(p => !problemsWithVector.exists(pv => pv._1 == p)).map { problem =>
      AnalogyResult(problem,
        WordDistance("", 0.0),
        WordDistance("", 0.0))
    }

    completedResults ++ incompletedResults
  }

  /* when we do an analogy problem like 'king' - 'man' + 'woman', the vector computed by that vector arithmetic
  is the query vector.  Computing it requires looking up the vectors for the three words which define the analogy problem
  any one of those words might not appear in the word vectors model, in which case there can be no query vector, which is why
  this returns Option */
  def computeQueryVector(wordVectors: Map[String, WordVector], problem: AnalogyProblem): Option[DenseVector[Double]] = {
    import gloving.VectorImplicits._

    for (exampleSrc <- wordVectors.get(problem.example.source);
      exampleTarget <- wordVectors.get(problem.example.target);
      testSrc <- wordVectors.get(problem.test.source)) yield {
      //Find the vector difference between the source and target example words
      val diff = exampleTarget.vector - exampleSrc.vector

      //Add the source component of the test analogy, and look for the word most closely matching
      val query = diff.vector + testSrc.vector

      query
    }
  }

  def getUniqueWords(problemSet: scala.collection.immutable.Map[String, Seq[AnalogyProblem]]): Set[String] = {
    val words = problemSet.flatMap { case (key, problems) =>
      problems.flatMap { problem => Seq(problem.example.source, problem.example.target, problem.test.source, problem.test.target) }
    }

    words.toSet
  }
}
