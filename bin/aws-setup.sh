#!/usr/bin/env bash

# Create S3 bucket
aws s3 mb s3://gloving.data

upload_vectors() {
	aws s3 cp ~/sources/glove/data/$1 s3://gloving.data/vectors/
}

upload_vectors glove.6B.50d.txt.gz
upload_vectors glove.6B.300d.txt.gz
upload_vectors glove.42B.300d.txt.gz

aws emr create-default-roles
