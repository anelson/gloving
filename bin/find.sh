#!/usr/bin/env bash
$SPARK_HOME/bin/spark-submit \
	--class gloving.Find \
	--name "gloving-find" \
	--master "local[*]" \
	--driver-memory 8G \
	./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
	"$@"

