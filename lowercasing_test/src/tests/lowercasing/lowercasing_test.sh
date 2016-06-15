#!/bin/bash
. ../../binref/env.sh

$BINREF/compilejava CasingVariants.java
sh dotest.sh
