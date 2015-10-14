#!/usr/bin/env bash
$SPARK_HOME/bin/spark-submit \
		--class gloving.Load \
		--name "gloving-cluster" \
		--master "local[*]" \
		--driver-memory 6G \
		--executor-memory 6G \
		./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
		"$@"
