#!/bin/bash
set -e
. ../../binref/env.sh

$BINREF/compilejava TestCompile.java
$BINREF/runjava TestCompile

