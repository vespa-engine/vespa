#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
for file in *
do
  if [ -d "$file" ]; then
    (cd "$file" && make clean && echo "$file cleaned")
  fi
done
