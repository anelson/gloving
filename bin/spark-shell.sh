#!/usr/bin/env bash
$SPARK_HOME/bin/spark-shell \
		--name "gloving-shell" \
		--master "local[*]" \
		--conf spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties \
		--driver-memory 4G \
		--executor-memory 4G \
		--jars ./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar