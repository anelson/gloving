#!/usr/bin/env bash

train() {
	if [ -d data/kmeans-$1-$2.model ]; then
		rm -rf data/kmeans-$1-$2.model
	fi

	$SPARK_HOME/bin/spark-submit \
		--class gloving.Main \
		--name "example" \
		--master "local[4]" \
		./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
		train \
		--clusters $1 \
		--iterations $2 \
		--model data/kmeans-$1-$2.model \
		--vectors data/glove.6B.50d.txt.processed
}

train 1000 5
train 1000 25
train 1000 50

train 10000 5
train 10000 25
train 10000 50
