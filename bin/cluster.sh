#!/usr/bin/env bash

if [ -d models/kmeans-$1-$2.model ]; then
	rm -rf models/kmeans-$1-$2.model
fi

mkdir -p models/

$SPARK_HOME/bin/spark-submit \
		--class gloving.Cluster \
		--name "gloving-cluster" \
		--master "local[*]" \
		./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
		--model models/ \
		--vectors data/glove.6B.50d.txt.gz