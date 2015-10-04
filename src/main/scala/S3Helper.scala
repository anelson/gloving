package gloving

import java.io.{InputStream, ByteArrayInputStream}
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions._
import com.amazonaws.services.s3._
import com.amazonaws.services.s3.model._

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

object S3Helper {
	@transient lazy val logger = Logger(LoggerFactory.getLogger(getClass.getName))

	def writeToS3(url: URI, contents: File) {
		//Because of a Joda Time bug fixed in 2.8.2 (see https://github.com/aws/aws-sdk-java/issues/444)
		//this won't work under Java 8.  Amazon have a workaround but it seems not to be deployed yet.
		System.out.println(new org.joda.time.DateTime().getClass().getProtectionDomain().getCodeSource());

		logger.info(s"Writing file $contents with length ${contents.length} to ${url}")

		val credentails = new ProfileCredentialsProvider().getCredentials()

		val s3Uri = new AmazonS3URI(url)
		val client = new AmazonS3Client(credentails)

		s3Uri.getRegion() match {
			case null =>
			case region => client.setRegion(RegionUtils.getRegion(region))
		}

		logger.info(s"URL ${url} specifies region [${s3Uri.getRegion()}] bucket [${s3Uri.getBucket()}] key [${s3Uri.getKey()}]")

		client.putObject(new PutObjectRequest(s3Uri.getBucket(), s3Uri.getKey(), contents))

		logger.info(s"Wrote string with length ${contents.length} to ${url}")
	}
}