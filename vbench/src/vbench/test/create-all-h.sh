# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#!/bin/bash
cd $(dirname $0)
out="all.h"
exec > $out
year=$(date +%Y)
cat <<EOF
// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// This file was generated by $(basename $0)


EOF

(
  cd ../..
  find vbench -name "*.h" | grep -v "vbench/test/$out" | while read name; do
    echo "#include <$name>"
  done
)

cat <<EOF

EOF
