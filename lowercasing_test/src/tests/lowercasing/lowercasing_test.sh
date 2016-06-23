#!/bin/bash
set -e
. ../../binref/env.sh

$BINREF/compilejava CasingVariants.java
sh dotest.sh
