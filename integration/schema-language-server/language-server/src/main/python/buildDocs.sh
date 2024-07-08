#!/bin/bash

which python3
if [ $? -eq 0 ]
then
    python3 buildDocs.py $1
fi