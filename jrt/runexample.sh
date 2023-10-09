#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
if [ $# -eq 0 ]; then
    echo "usage: $0 <class> [class args]"
    echo "  available class files:"
    ls examples/classes
else
    java -cp build/classes:examples/classes "$@"
fi
