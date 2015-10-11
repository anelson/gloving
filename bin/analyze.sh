#!/usr/bin/env bash
$SPARK_HOME/bin/spark-submit \
	--class gloving.Analyze \
	--name "gloving-analyze" \
	--master "local[*]" \
	--driver-memory 4G \
	--executor-memory 4G \
	./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
	"$@"

