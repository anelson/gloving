#!/usr/bin/env bash
$SPARK_HOME/bin/spark-submit \
	--class gloving.Analyze \
	--name "gloving-analyze" \
	--master "local[*]" \
	--conf spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties \
	--conf spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties \
	./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
	--vectors /Users/adam/sources/glove/data/glove.6B.300d.txt.gz

