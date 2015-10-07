#!/usr/bin/env bash
S3_BUCKET=$1
JAR_URI=$S3_BUCKET/jars/gloving-assembly-0.1.0-SNAPSHOT.jar
CONFIG_URI=$S3_BUCKET/config

#aws s3 cp $CONFIG_URI/log4j.properties /home/hadoop/

#sudo cp log4j.properties /etc/hadoop/conf.empty

#sudo cp log4j.properties /etc/spark/conf.dist

mkdir -p /home/hadoop/gloving/jars

aws s3 cp $JAR_URI /home/hadoop/gloving/jars
