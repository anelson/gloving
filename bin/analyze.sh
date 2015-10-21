#!/usr/bin/env bash
echo $SPARK_HOME/bin/spark-submit \
	--class gloving.Analyze \
	--name "gloving-analyze" \
	--master "local[*]" \
	--driver-memory ${DRIVER_MEMORY:-8G} \
	./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
	"$@"

