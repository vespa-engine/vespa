import os
import sys
import json

VERSION_PREFIX = "lsp-v"


def bumpVersion(version: str, bumpType: str) -> str:

    versionS = version.split(".")
    LOOKUP_LIST = [ "major", "minor", "patch" ]

    if (len(versionS) != len(LOOKUP_LIST)):
        raise Exception("The version has an invalid format")

    index = LOOKUP_LIST.index(bumpType)

    versionS[index] = str(int(versionS[index]) + 1)

    for i in range(index + 1, len(LOOKUP_LIST)):
        versionS[i] = "0"

    return ".".join(versionS)

def updateVsCodeVersion(version: str):

    with open("../clients/vscode/package.json", "+r") as file:
        data = json.load(file)

        data['version'] = version

        file.seek(0)

        json.dump(data, file, indent=2)

        file.truncate()
        file.write("\n")

def main(args):

    if (len(args) != 2):
        raise Exception("Expected one argument of either 'major', 'minor' or 'patch'")

    releasesRaw = os.popen(f"gh release list --json tagName -q '[.[]|select(.tagName | startswith(\"{VERSION_PREFIX}\"))]'").read()

    releaseData = json.loads(releasesRaw)

    if (len(releaseData) == 0):
        raise Exception("No previos releases found, not able to determine the last version for the LSP")

    latestRelease: str = releaseData[0]["tagName"]
    
    latestVersion = latestRelease[len(VERSION_PREFIX):]

    newVersion = bumpVersion(latestVersion, args[1])

    print(f"VERSION={newVersion}")

    updateVsCodeVersion(newVersion)


if __name__ == "__main__":
    main(sys.argv)