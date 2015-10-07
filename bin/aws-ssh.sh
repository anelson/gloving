#!/usr/bin/env bash

echo Opening SSH connection to $1 with internal-only hostname $2

ssh -i ~/Dropbox/disobay\ aws\ master\ keypair.pem hadoop@$1 -D 8157 -L:8088:$2:8088 -L:20888:$2:20888 -L:4040:$2:4040 -A
