#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

CURRENT=$(date -u '+%F %T %Z')

if [ -z ${VERSION} ]; then
    echo "ERROR: No version number defined";
    exit 1;
fi

cat <<EOF

This package provides a ClientProvider and a ServerProvider implementation using HTTP on JDisc.

EOF
