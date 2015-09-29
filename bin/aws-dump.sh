#!/usr/bin/env bash
S3_BUCKET=s3://gloving.data
LOG_URI=$S3_BUCKET/logs/`date +%Y%m%d%H%M%S`
JAR_URI=$S3_BUCKET/jars/gloving-assembly-0.1.0-SNAPSHOT.jar

aws emr create-cluster \
	--name "gloving-dump" \
	--no-auto-terminate \
	--release-label emr-4.0.0 \
	--instance-type m3.xlarge \
	--instance-count 3 \
	--ec2-attributes "KeyName=disobay master key pair" \
	--use-default-roles \
	--log-uri $LOG_URI \
	--applications Name=Spark \
	--configurations file://./emr-config.json \
  --steps \
  	"Name=\"Dump k=5000 iter=10\",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--deploy-mode,cluster,--master,yarn-cluster,--class,gloving.Main,$JAR_URI,dump,--model,$S3_BUCKET/models/kmeans-5000-10.model,--vectors,$S3_BUCKET/vectors/glove.6B.50d.txt.gz,--outputdir,$S3_BUCKET/output/kmeans-5000-10]" \
  	"Name=\"Dump k=5000 iter=100\",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--deploy-mode,cluster,--master,yarn-cluster,--class,gloving.Main,$JAR_URI,dump,--model,$S3_BUCKET/models/kmeans-5000-100.model,--vectors,$S3_BUCKET/vectors/glove.6B.50d.txt.gz,--outputdir,$S3_BUCKET/output/kmeans-5000-100]" \
  	"Name=\"Dump k=10000 iter=100\",Type=Spark,ActionOnFailure=CANCEL_AND_WAIT,Args=[--deploy-mode,cluster,--master,yarn-cluster,--class,gloving.Main,$JAR_URI,dump,--model,$S3_BUCKET/models/kmeans-10000-100.model,--vectors,$S3_BUCKET/vectors/glove.6B.50d.txt.gz,--outputdir,$S3_BUCKET/output/kmeans-10000-100]"
