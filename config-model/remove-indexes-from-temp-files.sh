#!/bin/sh

perl -pi -e 's{[[][0-9]*[]][.]}{[].}g;s{[[][0-9]*[]] }{[] };chomp;s/$/\n/' temp/*/*.cfg
