#!/usr/bin/env bash
$SPARK_HOME/bin/spark-shell \
		--name "gloving-shell" \
		--master "local[*]" \
		--driver-memory 6G \
		--executor-memory 6G \
		--jars ./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar