#! /bin/bash

base=/usit/abel/u1/chm/discomt/java
java -Deval.verbosity=1 -Dcoref.tgtlang=fr -cp $base:$base/lib/trove-2.0.jar discomt.eval.PronounEvaluation "$@"
