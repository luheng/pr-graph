-----------------------------------------------------------------------------
pr-graph version 0.1 (Graph-based Posterior Regularization)
-----------------------------------------------------------------------------

This Javaproject implements the Graph-based Posterior Regularization model 
described in the following paper:

Graph-Based Posterior Regularization for Semi-Supervised Structured Prediction
Luheng He, Jennifer Gillenwater, and Ben Taskar.
Conference on Computational Natural Language Learning (CoNLL), 2013.

----------------
Contents
----------------

1. Compiling
2. Graph Building
   a. POS Tagging Graph
   b. Handwriting Letters Graph
3. Running
   a. Input data format
   b. Running PR-graph
 
----------------
1. Compiling
----------------

The build.xml is included in the repository.
Use Ant (http://ant.apache.org/) to compile the project.

From the base directory of this project, run "ant build" to compile the code.
You can also run "ant clean" to remove compiled files and start over.

---------------------------------
2. Graph Buiding
--------------------------------

---------------------------------
2a. POS Tagging Graph
--------------------------------
PosTagging Graph building uses a suffix dictionary included in 
//pr-graph/data/suffix.dict
This list of suffixes is extracted from the Wiktionary data.

Universal part-of-speech tags mapping can be found here:
https://code.google.com/p/universal-pos-tags/

(D. Das, S. Petrov, and R. McDonald.
2012. A Universal Part-of-Speech Tagset. In Proc.
LREC.)

To run the graph builder, we can do:

export WDIR="your working directory"
export DDIR=”your data directory”
export CLASSPATH="$WDIR/bin/:$WDIR/libs/optimization-2010.11.jar:$WDIR/libs/trove-2.0.2.jar:$WDIR/libs/args4j-2.0.10.jar"

java -cp $CLASSPATH -Xmx8000m programs.TestPosGraphBuilder  \
-data-path "$DDIR/lang.train,$DDIR/lang.test" \ # a list of comma-delimited input file paths
-sufix-path “$DDIR/suffix.dict”
-umap-path "$DDIR/lang.map" \
-graph-path "$DDIR/graph/lang.grph" \ 
-ngram-path "$DDIR/graph/$lang.idx" \ 
-num-neighbors 60 \
-lang-name "lang"

The Graph builder outputs the node index file to -ngram-path, and the graph 
edge file to -graph-path. More options can be found at config.Config, 
config.PosConfig and config.PosGraphConfig.

-----------------------
2b. Handwriting Letters Graph
-----------------------
The code for building OCR Graph lives in another project (due to its dependency 
on the FastEMD code). The code is under //pr-graph/supplementary.
We can also use the graph file in //pr-graph/data/graph to run the experiments.

The FastEMD code and its Java wrapper is written by Ofir Pele:
(O. Pele and M. Werman.
2009. Fast and Robust Earth Mover’s Distances. In Proc. ICCV)

To run the OCR graph builder, we can do:

1). Extract the package ocr-graph-builder.tar.gz
2). In the file ocr-graph/src/ emd_hat.java:
      In Line 119, update the path of the emd tool library:
        System.load("/$YOUR_WORKING_PATH/ocr-graph/libs/libemd_hat_native.so");
3). From the base directory ocr-graph, run:
      ant build
    to compile.
4). Run:

export WDIR="your working directory"
export DDIR="your data directory"
export CLASSPATH="$WDIR/bin/:$WDIR/libs/trove-2.0.2.jar:$WDIR/libs/args4j-2.0.10.jar:$WDIR/libs/libemd_hat_native.so"

java -cp $CLASSPATH -Xmx8000m OcrGraphBuilder -data-path "$DDIR/letter.data"

-------------------------
3. Running
-------------------------

-------------------------
3a. Input data format
-------------------------
We use the CoNLL-X (http://ilk.uvt.nl/conll/index.html#dataformat) format for 
POSTagging, and the OCR (http://www.seas.upenn.edu/~taskar/ocr/) data for the 
handwriting task.

----------------------------
3b. Running PR-graph
----------------------------

For Pos-Tagging, run:
java -cp $CLASSPATH -Xmx8000m programs.TestHighOrderPos -num-labels 100 \
-data-path "lang.train,lang.test" \
-umap-path "lang.map" \
-ngram-path "lang-graph.idx" \
-graph-path "lang-graph.grph" \
-lang-name "lang" 
-sample-fold 0 \
-num-sample-folds 10 \
-eta 0.2 \
-backoff 1e-8 \
-gaussian-prior 100 \
-graph-strength 0.1 \
-num-mstep-iters 300 \
-num-estep-iters 10 \
-em-stop 0.01 \
-estep-stop 0.01 \
-mstep-stop 0.00001 \
-num-em-iters 20 \
-num-threads 8 \
-encoding "LATIN1"

*About encoding:
There was a encoding bug in the code for CoNLL-2013 paper, so in order to 
reproduce the result exactly, set -encoding to "LATIN1"; otherwise, set the 
encoding to "UTF8". The difference is tiny.

*Numerical issue in multi-threading
There will be tiny difference in optimizing for the CRF base model (probably 
due to some numerical problem) when we change the number of threads. 
Use -num-thread=8 to reproduce result. I will try to fix this problem in future 
version.

*-sample-fold and -num-sample-folds
In the CoNLL 2013 paper, we ran the experiment 10 times by randomly sampling 10 
different set of training samples, so we used -num-sample-folds = 10.
-sample-fold=0 means it is using the 0-th training set. Averaging through 
sample-fold from 0 to 9 will get the final results.

----

For handwriting recognition, run:

java -cp $CLASSPATH -Xmx8000m programs.TestHighOrderOCR -num-labels 110 \
-data-path "letter.data" \
-graph-path "ocr-graph.grph" \
-sample-fold 0 \
-num-sample-folds 10 \
-eta 0.2 \
-backoff 1e-8 \
-gaussian-prior 100 \
-graph-strength 1.0 \
-num-mstep-iters 300 \
-num-estep-iters 10 \
-em-stop 0.01 \
-estep-stop 0.01 \
-mstep-stop 0.00001 \
-num-em-iters 20 \
-num-threads 8 \
