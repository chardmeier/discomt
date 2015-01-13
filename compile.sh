#! /bin/bash

cd java
find . -name \*.java |
	xargs javac -cp lib/trove-2.0.jar
