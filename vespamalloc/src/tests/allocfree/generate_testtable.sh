#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
base=$1

echo "No threads test. Test difference of static linkage, shared and debug versus glibc"
cat $base | ./timeusage.sh > t1
cat $base | grep time | grep allocfree_ | cut -d'5' -f2  | awk '{print $1*2 + $2 ";"}' > t2
cat $base | grep "Total" | awk '{print $2}' > t3
paste testnames.all testtype.all t2 t1 t3 > t4

for t in "cross thread" "same thread" "same + cross"
do
    echo $t

    for f in "glibc" "vespamallostatic" "vespamalloc" "tcmalloc" "jemalloc" "ptmalloc3" "nedmalloc" "hoard" "tlsf"
    do
        grep "$t" t4 | grep "$f" | cut -d';' -f7 | xargs echo $f | sed "s/ /;/g"
    done
done

cat t4

