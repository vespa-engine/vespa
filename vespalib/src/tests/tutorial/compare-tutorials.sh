#!/bin/bash
set -e
./vespalib_make_tutorial_app > tutorial_out.html && diff -u tutorial.html tutorial_out.html
