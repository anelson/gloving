package gloving

import java.net.URI
import java.io.{File, FileInputStream, BufferedInputStream, DataInputStream, InputStream}
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.nio.{ByteBuffer,ByteOrder}
import java.util.zip.GZIPInputStream

import scala.collection.mutable.ArrayBuffer

import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SaveMode
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.util.StatCounter

import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}

import breeze.linalg.DenseVector

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

import gloving.WordVectorRDD._

class Word2VecWordVectorLoader(val path: URI) extends WordVectorLoader {
  val SpaceCharacter = 0x20
  val NewLineCharacter = 0x0a

  val MaxString = 2000 //From word2vec source code

  val WordsPerChunk = 100000
  val ReadBufferSize = 1024 * 1024 //Aggressively buffer the input if it's compressed
  val BytesPerFloat = 4

  @transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

  def load(sc: SparkContext): WordVectorRDD = {
    val name = new File(path.getPath()).getName
    val inputStream = openVectorFile(path)

    val words = readWords(inputStream)

    //It's not practical to try to read all of the word vectors into memory, at least not on
    //a Mac laptop with a Java heap limit of a few gigs.  So process chunks of words at a time, loading them
    //into a dataframe and then appending each chunk one at a time.
    val tempDataFrameFile = File.createTempFile(s"$name-working-dataframe", ".tmp")
    tempDataFrameFile.delete()
    val tempDataFrameUri = tempDataFrameFile.toURI()
    logger.info(s"Loading word2vec vector $path into temporary data frame file $tempDataFrameUri")

    //First, create an empty data frame on disk
    sc.parallelize(Seq[WordVector]()).save(tempDataFrameUri, SaveMode.Overwrite)

    var wordCount: Long = 0

    words.grouped(WordsPerChunk).foreach { chunk =>
      val chunkSeq = chunk.toSeq

      sc.parallelize(chunkSeq).save(tempDataFrameUri, SaveMode.Append)
      wordCount += chunkSeq.length

      logger.info(s"Processed $wordCount words so far")
    }

    //When loading the temp file must repartition since it will be broken up into too many small partitions due to the way
    //it was constructed
    val rdd = WordVectorRDD.load(sc, tempDataFrameUri)
      .repartition(sc.defaultParallelism * 3)
    rdd.setName(s"$name-wordvectors")

    tempDataFrameFile.delete()

    rdd
  }

  def readWords(stream: DataInputStream): Iterator[WordVector] = {
    //The pre-trained word2vec files are in a very strange format.  First is an ASCII string representation of the number
    //of words in the file, followed by a space, and an ASCII string representation of the number of dimensions in each vector, followed by a newline.
    //After that, it's an array of records, consisting of the ASCII representation of the word, terminated by a space, followed
    //by binary representations of each vector dimension's value, stored as a single precision floating point.
    //It's just nutty.
    val numWords = readWord(stream).toInt
    val numDimensions = readWord(stream).toInt

    logger.info(s"Reading $numWords word vectors, $numDimensions dimensions per vector")
    val readBuffer = ByteBuffer.allocate(numDimensions * BytesPerFloat)
    readBuffer.order(ByteOrder.LITTLE_ENDIAN)
    for (wordIndex <- (0 until numWords).iterator) yield {
      val word = readWord(stream)

      val values = new Array[Double](numDimensions)

      //Read all of the bytes for this vector into the ByteBuffer first, and then and only then
      //use ByteBuffer to decode the float values.  This is needed because the floats are encoded little-endian
      //but DataInputStream only handles big-endian encoding
      readBuffer.rewind()
      stream.read(readBuffer.array(), readBuffer.arrayOffset(), numDimensions * BytesPerFloat)

      for (dim <- 0 until numDimensions) {
        val value = readBuffer.getFloat()
        values(dim) = value.toDouble
      }

      WordVector(wordIndex, word, DenseVector[Double](values))
    }
  }

  def readWord(stream: DataInputStream): String = {
    var readBuffer: Array[Byte] = new Array(MaxString)
    var wordCountStr: String = ""
    var b: Byte = 0x00
    var idx: Int = 0

    do {
      b = stream.readByte()
      readBuffer(idx) = b
      idx+=1
    } while (b != SpaceCharacter && b != NewLineCharacter)

    val word = new String(readBuffer, 0, idx-1, StandardCharsets.US_ASCII)
    logger.debug(s"Read word $word")

    word
  }

  private def openVectorFile(path: URI): DataInputStream = {
    //If the file is gzip-compressed, run it through a GZIPInputStfream first
    val rawStream = new FileInputStream(path.toString())

    if (path.getPath().endsWith(".gz")) {
      new DataInputStream(new BufferedInputStream(new GZIPInputStream(rawStream)))
    } else {
      new DataInputStream(new BufferedInputStream(rawStream, ReadBufferSize))
    }
  }
}
