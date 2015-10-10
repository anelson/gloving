#!/usr/bin/env bash
$SPARK_HOME/bin/spark-submit \
		--class gloving.Load \
		--name "gloving-cluster" \
		--master "local[*]" \
		--driver-memory 4G \
		--executor-memory 4G \
		--conf spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties \
		--conf spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties \
		./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
		--input data/glove.6B.50d.txt.gz \
		--output vectors/glove.6B.50d \
		--format glove
