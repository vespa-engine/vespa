#!/bin/bash
set -e

rm -rf server
$VALGRIND ./searchlib_translogstress_app

