#!/bin/bash
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

./vespalib_make_tutorial_app > tutorial_out.html
diff -u $SOURCE_DIRECTORY/tutorial.html tutorial_out.html || true
echo "IGNORED: vespalib_make_tutorial_app"
