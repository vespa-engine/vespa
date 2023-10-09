#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

echo "WARNING: This binary has been renamed from vdsgetsystemstate to "
echo "         vdsgetclusterstate. Currently, this script calls the other. "
echo "         as a convinience. This script will be removed later."

vdsgetclusterstate $@
