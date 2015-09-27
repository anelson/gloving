#!/bin/sh
awk '{printf "%d %s\n", NR, $0}' < $1
