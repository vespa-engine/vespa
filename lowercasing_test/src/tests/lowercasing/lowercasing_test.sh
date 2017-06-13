#!/bin/bash
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

. ../../binref/env.sh

$BINREF/compilejava -d . $SOURCE_DIRECTORY/CasingVariants.java
bash -e $SOURCE_DIRECTORY/dotest.sh
