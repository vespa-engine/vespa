import sys
import os
import requests
import pathlib
from VespaDocHTMLParser import VespaDocHTMLParser

URL_PREFIX: str = "https://raw.githubusercontent.com/vespa-engine/documentation/master/en"

SCHEMA_URL = "/reference/schema-reference.html"

LINK_BASE_URL = "https://docs.vespa.ai/en"

def fetchFile(file_url: str) -> str:
    data = requests.get(f"{URL_PREFIX}{file_url}")
    if data.status_code != 200:
        raise Exception("Could not fetch the news documentation! Has the url paths changed?")

    return data.text

def main():
    print("Downlaoding docs...")

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

        with open(f"{targetPath}/{convertToToken(tag.id)}.md", "w") as file:
            file.write(tag.AST.toMarkdown())


def convertToToken(name):
    return name.upper().replace("-", "_")

if __name__ == "__main__":
    main()