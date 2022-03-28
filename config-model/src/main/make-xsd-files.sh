#!/bin/sh

set -e

srcdir=resources/schema
outputdir=../../target/generated-sources/trang/resources/schema

trangjar=../../target/trang.jar

outputs="services hosts container-include deployment validation-overrides"

gen_xsd() {
    echo "Generating XML schema: $1.rnc -> $1.rng -> $1.xsd"
    java -jar ${trangjar} -I rnc -O rng ${srcdir}/$1.rnc ${outputdir}/$1.rng
    java -jar ${trangjar} -I rng -O xsd ${outputdir}/$1.rng ${outputdir}/$1.xsd
    echo "generated ok."
}

regenall() {
    mkdir -p $outputdir
    for x in $outputs; do gen_xsd $x; done
}

need_regen() {
    for out in $outputs; do
        outfile=${outputdir}/${out}.xsd
        if [ -f ${outfile} ]; then
            for infile in ${srcdir}/*.*; do
                if [ ${infile} -nt ${outfile} ]; then
                    echo "Updated input: ${infile} - regenerating all"
                    return 0
                fi
            done
        else
            echo "Missing output: ${outfile} - regenerating all"
            return 0
        fi
    done
    echo "No updates for schema files"
    return 1
}

if need_regen; then
    regenall
fi
