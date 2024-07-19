import sys
import os
import requests
import pathlib
from VespaDocHTMLParser import VespaDocHTMLParser

URL_PREFIX: str = "https://raw.githubusercontent.com/vespa-engine/documentation/master/en"

SCHEMA_URL = "/reference/schema-reference.html"

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
    data = requests.get(f"{URL_PREFIX}{file_url}")
    if data.status_code != 200:
        raise Exception("Could not fetch the news documentation! Has the url paths changed?")

    return data.text

def main():
    print("Downloading docs...")

    targetPath: pathlib.Path = pathlib.Path()

    if (len(sys.argv) >= 2):
        targetPath = pathlib.Path(sys.argv[1])
    else:
        raise Exception("No target directory specified")
    
    if (len(sys.argv) >= 3):
        if (sys.argv[2] == "skip"):
            return
    
    if not os.path.exists(targetPath):
        os.makedirs(targetPath)

    data: str = fetchFile(SCHEMA_URL)

    parser = VespaDocHTMLParser(LINK_BASE_URL + SCHEMA_URL)
    parser.feed(data)

    tags = parser.getTags()

    for tag in tags:

        filename = convertToToken(tag.AST.getName())
        data = tag.AST.toMarkdown()

        if filename in REPLACE_FILENAME_MAP:
            for fn in REPLACE_FILENAME_MAP[filename]:
                 writeToFile(f"{targetPath}/{fn}.md", data)
        else:
            writeToFile(f"{targetPath}/{filename}.md", data)

def writeToFile(filepath: str, data: str):
    with open(filepath, "w") as file:
        file.write(data)

def convertToToken(name):
    return name.upper().replace("-", "_")

if __name__ == "__main__":
    main()