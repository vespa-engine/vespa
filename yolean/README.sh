#!/bin/sh
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
if [ -z ${VERSION} ]; then
    echo "Environment VERSION not set." >&2
    exit 1;
fi
cat <<EOF

Yolean is a collection of Java utility classes that may be useful
across various products. INclusion here has a higher threshold than
vespajlib.

EOF
