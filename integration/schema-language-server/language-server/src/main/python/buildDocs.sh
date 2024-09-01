#!/bin/bash

python3 -m pip install -r requirements.txt --user

python3 buildDocs.py $1

#exit 0