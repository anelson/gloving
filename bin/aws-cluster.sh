#!/usr/bin/env bash
S3_BUCKET=s3://gloving.data
LOG_URI=$S3_BUCKET/logs/`date +%Y%m%d%H%M%S`
JAR_URI=$S3_BUCKET/jars/gloving-assembly-0.1.0-SNAPSHOT.jar

aws emr create-cluster \
	--name "gloving-cluster" \
	--no-auto-terminate \
	--release-label emr-4.1.0 \
	--instance-type m3.xlarge \
	--instance-count 3 \
	--ec2-attributes "KeyName=disobay master key pair" \
	--use-default-roles \
	--log-uri $LOG_URI \
	--applications Name=Spark Name=Zeppelin-Sandbox \
	--configurations file://./emr-config.json \
  --steps \
  	"Name=\"Cluster 6B\",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--deploy-mode,cluster,--master,yarn-cluster,--class,gloving.Cluster,$JAR_URI,--model,$S3_BUCKET/models/,--vectors,$S3_BUCKET/vectors/glove.6B.50d.txt.gz,--outputdir,$S3_BUCKET/output/]"