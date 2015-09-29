#!/usr/bin/env bash
S3_BUCKET=s3://gloving.data
LOG_URI=$S3_BUCKET/logs/`date +%Y%m%d%H%M%S`
JAR_URI=$S3_BUCKET/jars/gloving-assembly-0.1.0-SNAPSHOT.jar

aws emr create-cluster \
	--name "gloving-train" \
	--auto-terminate \
	--release-label emr-4.0.0 \
	--instance-type m3.large \
	--instance-count 3 \
	--ec2-attributes "KeyName=disobay master key pair" \
	--use-default-roles \
	--enable-debugging \
	--log-uri $LOG_URI \
	--applications Name=Spark,Args=[-x] #\
#	--steps "Name=\"RunSpark\",Type=Spark,Args=[--deploy-mode,cluster,--master,yarn-cluster,--conf,spark.executor.extraJavaOptions=-XX:MaxPermSize=512m,--conf,spark.driver.extraJavaOptions=-XX:MaxPermSize=512m,--class,gloving.Main,$JAR_URI,train,--clusters,5000,--iterations,10,--model,$S3_BUCKET/models/kmeans-5000-10.model,--vectors,$S3_BUCKET/vectors/glove.6B.50d.txt.gz]"
