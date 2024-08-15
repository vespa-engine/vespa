import sys
import os
import requests
import pathlib
from Node import Node
from DocsHTMLParser import DocsHTMLParser
import visitor

BRANCH = "master"
URL_PREFIX = f"https://raw.githubusercontent.com/vespa-engine/documentation/{BRANCH}/en"

SCHEMA_URL = "/reference/schema-reference.html"
RANK_EXPRESSION_URL = "/reference/rank-features.html"

LINK_BASE_URL = "https://docs.vespa.ai/en"

REPLACE_FILENAME_MAP = {
    "EXPRESSION": [ "EXPRESSION_SL", "EXPRESSION_ML" ],
    "RANK_FEATURES": [ "RANKFEATURES_SL", "RANKFEATURES_ML" ],
    "FUNCTION (INLINE)? [NAME]": [ "FUNCTION" ],
    "SUMMARY_FEATURES": [ "SUMMARYFEATURES_SL", "SUMMARYFEATURES_ML", "SUMMARYFEATURES_ML_INHERITS" ],
    "MATCH_FEATURES": [ "MATCHFEATURES_SL", "MATCHFEATURES_ML", "MATCHFEATURES_SL_INHERITS" ],
    "IMPORT FIELD": [ "IMPORT" ]
}

# TODO: fix dictionary and attribute and index

def fetchFile(file_url: str) -> str:
    URL = f"{URL_PREFIX}{file_url}"

    print(f"Downloading docs from: {URL}")
    data = requests.get(URL)
    if data.status_code != 200:
        raise Exception("Could not fetch the news documentation! Has the url paths changed?")

    return data.text

def parseRawHTML(rawData: str, fileName: str) -> Node:
    parser = DocsHTMLParser(fileName)

    node = parser.parse(rawData)

    urlVisitor = visitor.SwitchInternalURL(LINK_BASE_URL + fileName)
    urlVisitor.traverse(node)

    return node

def main():

    targetPath: pathlib.Path = pathlib.Path()
    subPaths = ["schema", "rankExpression"]

    if (len(sys.argv) >= 2):
        targetPath = pathlib.Path(sys.argv[1])
    else:
        raise Exception("No target directory specified")
    
    if (len(sys.argv) >= 3):
        if (sys.argv[2] == "skip"):
            return
    
    if not os.path.exists(targetPath):
        os.makedirs(targetPath)

    for subPath in subPaths:
        absoluteSubPath = targetPath.joinpath(subPath)
        if not os.path.exists(absoluteSubPath):
            os.makedirs(absoluteSubPath)

    rawData = fetchFile(SCHEMA_URL)

    data = ""
    with open("/Users/theodorkl/Documents/github.com/vespa-engine/documentation/en/reference/schema-reference.html") as file:
        data = file.read()

    results = parseRawHTML(data, SCHEMA_URL)

    print(results.toHTML())

if __name__ == "__main__":
    main()