#!/usr/bin/env bash

#if [ -d models/kmeans-$1-$2.model ]; then
#	rm -rf models/kmeans-$1-$2.model
#fi

mkdir -p models/

$SPARK_HOME/bin/spark-submit \
		--class gloving.Cluster \
		--name "gloving-cluster" \
		--master "local[*]" \
		--driver-memory ${DRIVER_MEMORY:-8G} \
		--conf spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties \
		--conf spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties \
		./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
		--model models/ \
		--vectors vectors/glove.6B.50d/ \
		--clusters 500 \
		--iterations 1 \
		--runs 1 \
		--checkpoints 1