#!/bin/sh
$SPARK_HOME/bin/spark-submit \
	--class gloving.Main \
	--name "example" \
	--master "local[4]" \
	./target/scala-2.10/gloving-assembly-0.1.0-SNAPSHOT.jar \
	dump \
	--model ~/sources/spark-1.5.0-bin-hadoop2.6/kmeans.model \
	--vectors data/glove.6B.50d.txt.processed
