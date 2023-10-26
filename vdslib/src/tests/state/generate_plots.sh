#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

yum ls gnuplot &> /dev/null

if [ $? -ne 0 ]; then
    echo "gnuplot is not installed. Please do: sudo yum install gnuplot"
fi

echo "cp data files to tests/state/plots/data"
cp datadistribution_*.dat state/plots/data/ &> /dev/null

echo "ploting graphs"
for a in `ls state/plots/scripts`; do echo "$a"; gnuplot state/plots/scripts/$a; done

echo "mv png files to graphs directory"
mv *.png state/plots/graphs/ &> /dev/null
