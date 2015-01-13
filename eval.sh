#! /bin/bash

base=${DISCOMT_HOME:-.}/java
java -Deval.verbosity=1 -cp $base:$base/lib/trove-2.0.jar discomt.eval.PronounEvaluation "$@"
