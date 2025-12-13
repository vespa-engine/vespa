#!/bin/bash
# Convert XGBoost UBJ file to JSON format
# Usage: ./ubj-to-json.sh <input.ubj> [output.json]

if [ $# -lt 1 ]; then
    echo "Usage: $0 <input.ubj> [output.json]"
    exit 1
fi

INPUT="$1"
OUTPUT="${2:-}"

if [ -n "$OUTPUT" ]; then
    mvn exec:java -Dexec.mainClass="ai.vespa.rankingexpression.importer.xgboost.UbjToJson" \
        -Dexec.args="$INPUT" -Dexec.classpathScope=test -q > "$OUTPUT"
    echo "Converted $INPUT to $OUTPUT"
else
    mvn exec:java -Dexec.mainClass="ai.vespa.rankingexpression.importer.xgboost.UbjToJson" \
        -Dexec.args="$INPUT" -Dexec.classpathScope=test -q
fi
