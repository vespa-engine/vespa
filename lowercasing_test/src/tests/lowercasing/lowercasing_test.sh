#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

. ../../binref/env.sh

$BINREF/compilejava -d . $SOURCE_DIRECTORY/CasingVariants.java
bash -e $SOURCE_DIRECTORY/dotest.sh
