#!/usr/bin/env bash
S3_BUCKET=s3://gloving.data
LOG_URI=$S3_BUCKET/logs/`date +%Y%m%d%H%M%S`
JAR_URI=$S3_BUCKET/jars/gloving-assembly-0.1.0-SNAPSHOT.jar
JAR_PATH=/home/hadoop/gloving/jars/gloving-assembly-0.1.0-SNAPSHOT.jar

aws emr create-cluster \
	--name "gloving-cluster" \
	--auto-terminate \
	--release-label emr-4.1.0 \
	--instance-type c3.2xlarge \
	--instance-count 3 \
	--ec2-attributes "KeyName=disobay master key pair" \
	--use-default-roles \
	--log-uri $LOG_URI \
	--applications Name=Spark \
	--configurations file://./emr-config.json \
	--bootstrap-actions Path=$S3_BUCKET/emr-bootstrap.sh,Name=DownloadJAR,Args=$S3_BUCKET \
  --steps \
  	"Name=\"Cluster 6B 300d k=1000 \",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--conf,spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--conf,spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--master,yarn-client,--class,gloving.Cluster,$JAR_PATH,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.6B.300d.txt.gz,--outputdir,$S3_BUCKET/output/,--clusters,1000,--iterations,100,--runs,10,--checkpoints,10]" \
  	"Name=\"Cluster 6B 300d k=5000 \",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--conf,spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--conf,spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--master,yarn-client,--class,gloving.Cluster,$JAR_PATH,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.6B.300d.txt.gz,--outputdir,$S3_BUCKET/output/,--clusters,5000,--iterations,100,--runs,10,--checkpoints,10]" \
  	"Name=\"Cluster 6B 300d k=10000\",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--conf,spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--conf,spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--master,yarn-client,--class,gloving.Cluster,$JAR_PATH,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.6B.300d.txt.gz,--outputdir,$S3_BUCKET/output/,--clusters,10000,--iterations,100,--runs,10,--checkpoints,10]" \
  	"Name=\"Cluster 42B 300d k=1000 \",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--conf,spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--conf,spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--master,yarn-client,--class,gloving.Cluster,$JAR_PATH,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.42B.300d.txt.gz,--outputdir,$S3_BUCKET/output/,--clusters,1000,--iterations,100,--runs,10,--checkpoints,10]" \
  	"Name=\"Cluster 42B 300d k=5000 \",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--conf,spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--conf,spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--master,yarn-client,--class,gloving.Cluster,$JAR_PATH,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.42B.300d.txt.gz,--outputdir,$S3_BUCKET/output/,--clusters,5000,--iterations,100,--runs,10,--checkpoints,10]" \
  	"Name=\"Cluster 42B 300d k=10000\",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--conf,spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--conf,spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.gloving.properties,--master,yarn-client,--class,gloving.Cluster,$JAR_PATH,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.42B.300d.txt.gz,--outputdir,$S3_BUCKET/output/,--clusters,10000,--iterations,100,--runs,10,--checkpoints,10]"



