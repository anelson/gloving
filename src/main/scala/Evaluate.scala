package gloving

import java.net.{URI, URL}
import java.io.{File, PrintWriter}

import scala.collection.parallel.ParSeq
import scala.collection.immutable.Map

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}
import org.apache.spark.mllib.linalg.{Vectors, Vector}

import play.api.libs.json._
import play.api.libs.json.Json._

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

import net.sf.ehcache.{CacheManager,Ehcache,Cache,Element}

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import gloving.WordVectorRDD._

object Evaluate {
  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  case class CliOptions(questionFilesPath: File = null,
    vectorUrls: Seq[URI] = Seq(),
    outputUrl: URI = new URI("./vector-evaluation.json"))

  def main(args: Array[String]) {
    val optParser = new scopt.OptionParser[CliOptions]("evaluate") {
      head("gloving", "SNAPSHOT")
      arg[File]("question-files") required() action { (x, c) =>
        c.copy(questionFilesPath = x) } text("Name of directory containing analogy problem question files")
      arg[URI]("vector file [vector file...]") unbounded() required() action { (x, c) =>
        c.copy(vectorUrls = c.vectorUrls :+ x) } text("URLs or paths to vector files created by the load command")
      opt[URI]('o', "output") optional() action { (x, c) =>
        c.copy(outputUrl = x) } text("Path and file name to which the evaluation JSON file is written")
    }

    val config = optParser.parse(args, CliOptions()).get

    val conf = new SparkConf().setAppName("gloving-evaluate")
    val sc = new SparkContext(conf)

    evaluate(sc, config)
  }

  def evaluate(sc: SparkContext, config: CliOptions) {
    logger.info(s"Loading analogy problems from problem folder ${config.questionFilesPath}")

    val analogyProblems = config.questionFilesPath.listFiles.filter(_.isFile).map { file =>
      (file.getName -> AnalogyProblem.fromTextFile(file.toURI))
    }.toMap

    // val cm = CacheManager.newInstance(getClass().getResource("/ehcache.xml"))
    // val cache = cm.getCache("words")

    val resultsMap = config.vectorUrls.map { vectorUrl =>
      val name = new File(vectorUrl.getPath()).getName

      val words = WordVectorRDD.load(sc, vectorUrl).cache()

      logger.info(s"Evaluating vectors $name")

      val results = evaluate(words, analogyProblems)

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

  def evaluate(words: WordVectorRDD, problemSet: Map[String, Seq[AnalogyProblem]]): VectorEvaluation = {
    //Most words in the problem set appear in many problems.  As an optimization, look up the word vectors of all of the words
    //up front in one operation
    val wordVectors = words.findWords(getUniqueWords(problemSet))

    val results: Iterable[AnalogyResults] = problemSet.map { case (name, problems) =>
      computeAnalogyResults(solveAnalogyProblems(words, wordVectors, problems),
        name)
    }

    VectorEvaluation(results.toSeq)
  }

  def computeAnalogyResults(results: Seq[AnalogyResult], name: String): AnalogyResults = {
    //Compute the aggregate statistics for all of the problems
    val euclideanResults = results.map(r => (r.problem.test.target, r.euclideanAnswer))
    val (euclideanAccuracy, euclideanStats, euclideanCorrectStats, euclideanIncorrectStats) = computeResultStats(euclideanResults)

    val cosineResults = results.map(r => (r.problem.test.target, r.cosineAnswer))
    val (cosineAccuracy, cosineStats, cosineCorrectStats, cosineIncorrectStats) = computeResultStats(cosineResults)

    val incorrectResults = results
      .filter(result => result.problem.test.target != result.euclideanAnswer.word || result.problem.test.target != result.cosineAnswer.word)

    AnalogyResults(name,
      results.length,
      euclideanAccuracy, euclideanStats, euclideanCorrectStats, euclideanIncorrectStats,
      cosineAccuracy, cosineStats, cosineCorrectStats, cosineIncorrectStats,
      incorrectResults.seq)
  }

  /** Given a list of expected answers, actual answers, and distances, computes the accuracy, and statistical distribuion
  of the distances overall, correct, and incorrect */
  def computeResultStats(results: Seq[(String, WordDistance)]): (Double, Statistics, Statistics, Statistics) = {
    val correctResults = results.filter(r => r._1 == r._2.word)
    val incorrectResults = results.filter(r => r._1 != r._2.word)

    val resultCount = results.length
    val accuracy = correctResults.length.toDouble / resultCount.toDouble

    val allDistances = results.map(_._2.distance)
    val correctDistances = correctResults.map(_._2.distance)
    val incorrectDistances = incorrectResults.map(_._2.distance)

    val stats = Statistics.fromDescriptiveStatistics(new DescriptiveStatistics(allDistances.toArray))
    val correctStats = Statistics.fromDescriptiveStatistics(new DescriptiveStatistics(correctDistances.toArray))
    val incorrectStats = Statistics.fromDescriptiveStatistics(new DescriptiveStatistics(incorrectDistances.toArray))

    (accuracy, stats, correctStats, incorrectStats)
  }

  def solveAnalogyProblems(words: WordVectorRDD, wordVectors: Map[String, WordVector], problems: Seq[AnalogyProblem]): Seq[AnalogyResult] = {
    problems.par.flatMap { problem => solveAnalogyProblem(words, wordVectors, problem) }.seq
  }

  def solveAnalogyProblem(words: WordVectorRDD, wordVectors: Map[String, WordVector], problem: AnalogyProblem) : Option[AnalogyResult] = {
    import gloving.VectorImplicits._

    val analogyResult = for (exampleSrc <- wordVectors.get(problem.example.source);
      exampleTarget <- wordVectors.get(problem.example.target);
      testSrc <- wordVectors.get(problem.test.source)) yield {
      //Find the vector difference between the source and target example words
      val diff = exampleTarget.vector - exampleSrc.vector

      //Add the source component of the test analogy, and look for the word most closely matching
      val query = diff.vector + testSrc.vector

      val euclideanResult = words.findNearestEuclidean(query, 1).head
      val cosineResult = words.findNearestCosine(query, 1).head

      AnalogyResult(problem,
        WordDistance(euclideanResult._1.word, euclideanResult._2),
        WordDistance(cosineResult._1.word, cosineResult._2))
    }

    //Because we used for comprehensions on the Option values returned by findword,
    //analogyResult could be None if one or more words were not found in the WordVector RDD.
    //Print a warning in the log; the flatMap call on problems.par will exclude any None values from the results
    analogyResult match {
      case None => {
        logger.error(s"Unable to find word vectors for one or more words in analogy problem $problem")
        None
      }

      case x => {
        logger.info(s"Got analogy result: $x")
        x
      }
    }
  }

  def getUniqueWords(problemSet: scala.collection.immutable.Map[String, Seq[AnalogyProblem]]): Set[String] = {
    val words = problemSet.flatMap { case (key, problems) =>
      problems.flatMap { problem => Seq(problem.example.source, problem.example.target, problem.test.source, problem.test.target) }
    }

    words.toSet
  }
}
