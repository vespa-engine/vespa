#!/bin/bash
set -e

. ../../binref/env.sh
$BINREF/compilejava HelloWorldLocal.java
sh dotest.sh
