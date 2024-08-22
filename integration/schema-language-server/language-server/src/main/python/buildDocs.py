import sys
import os
import requests
import pathlib
from Node import Node
from DocsHTMLParser import DocsHTMLParser
import visitor
from VespaSchemaReferenceDocsParser import VespaSchemaReferenceDocsParser
from VespaRankFeatureDocsParser import VespaRankFeatureDocsParser

BRANCH = "master"
URL_PREFIX = f"https://raw.githubusercontent.com/vespa-engine/documentation/{BRANCH}/en"

SCHEMA_URL = "/reference/schema-reference.html"
RANK_FEATURE_URL = "/reference/rank-features.html"

LINK_BASE_URL = "https://docs.vespa.ai/en"

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

def parsePage(data: str, url: str, parser: visitor.Visitor, outputPath: pathlib.Path):

    results = parseRawHTML(data, url)
    
    parser.traverse(results)
    mdFiles = parser.getResults()

    for file in mdFiles:
        file.write(outputPath)

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

    schemaReferenceParser = VespaSchemaReferenceDocsParser(f"{LINK_BASE_URL}{SCHEMA_URL}")
    parsePage(data, SCHEMA_URL, schemaReferenceParser, targetPath.joinpath(subPaths[0]))

    data = ""
    with open("/Users/theodorkl/Documents/github.com/vespa-engine/documentation/en/reference/rank-features.html") as file:
        data = file.read()

    rankFeatureParser = VespaRankFeatureDocsParser()
    parsePage(data, RANK_FEATURE_URL, rankFeatureParser, targetPath.joinpath(subPaths[1]))

if __name__ == "__main__":
    main()