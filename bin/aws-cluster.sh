#!/usr/bin/env bash
S3_BUCKET=s3://gloving.data
LOG_URI=$S3_BUCKET/logs/`date +%Y%m%d%H%M%S`
JAR_URI=$S3_BUCKET/jars/gloving-assembly-0.1.0-SNAPSHOT.jar
JAR_PATH=/home/hadoop/gloving/jars/gloving-assembly-0.1.0-SNAPSHOT.jar

#STANDARD_GC_OPTIONS="-verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:+PrintGCApplicationStoppedTime"
STANDARD_DRIVER_GC_OPTIONS="-XX:+UseConcMarkSweepGC -XX:CMSInitiatingOccupancyFraction=70 -XX:MaxHeapFreeRatio=70 -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512M"
STANDARD_EXECUTOR_GC_OPTIONS="-verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+UseConcMarkSweepGC -XX:CMSInitiatingOccupancyFraction=70 -XX:MaxHeapFreeRatio=70 -XX:+CMSClassUnloadingEnabled"

EXECUTOR_OPTIONS="-Dlog4j.configuration=log4j.gloving.properties $STANDARD_EXECUTOR_GC_OPTIONS"
DRIVER_OPTIONS="-Dlog4j.configuration=log4j.gloving.properties $STANDARD_DRIVER_GC_OPTIONS"
STEP_COMMON_ARGS="--conf,spark.executor.extraJavaOptions=$EXECUTOR_OPTIONS,--conf,spark.driver.extraJavaOptions=$DRIVER_OPTIONS,--master,yarn-client,--class,gloving.Cluster,$JAR_PATH"

echo aws emr create-cluster \
	--name "gloving-cluster" \
	--no-auto-terminate \
	--release-label emr-4.1.0 \
	--instance-type m3.2xlarge \
	--instance-count 3 \
	--ec2-attributes "KeyName=disobay master key pair" \
	--use-default-roles \
	--log-uri $LOG_URI \
	--applications Name=Spark \
	--configurations file://./emr-config.json \
	--bootstrap-actions Path=$S3_BUCKET/emr-bootstrap.sh,Name=DownloadJAR,Args=$S3_BUCKET \
  --steps \
  	"Name=\"Cluster 42B 300d k=1000 \",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[$STEP_COMMON_ARGS,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.42B.300d.txt.gz,--outputdir,$S3_BUCKET/output/,--clusters,1000,--iterations,100,--runs,1,--checkpoints,10]" \
  	"Name=\"Cluster 42B 300d k=5000 \",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[$STEP_COMMON_ARGS,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.42B.300d.txt.gz,--outputdir,$S3_BUCKET/output/,--clusters,5000,--iterations,100,--runs,1,--checkpoints,10]" \
  	"Name=\"Cluster 42B 300d k=10000\",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[$STEP_COMMON_ARGS,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.42B.300d.txt.gz,--outputdir,$S3_BUCKET/output/,--clusters,10000,--iterations,100,--runs,1,--checkpoints,10]"



