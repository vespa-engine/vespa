
import json


def main():
    
    with open("../vscode/package.json") as file:

        data = json.load(file)

        version = data["version"]

        with open("VERSION", "w") as versionFile:
            versionFile.write(version)


if __name__ == "__main__":
    main()