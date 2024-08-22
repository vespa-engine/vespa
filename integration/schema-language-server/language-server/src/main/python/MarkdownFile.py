import pathlib

REPLACE_FILENAME_MAP = {
    "EXPRESSION": [ "EXPRESSION_SL", "EXPRESSION_ML" ],
    "RANK_FEATURES": [ "RANKFEATURES_SL", "RANKFEATURES_ML" ],
    "FUNCTION (INLINE)? [NAME]": [ "FUNCTION" ],
    "SUMMARY_FEATURES": [ "SUMMARYFEATURES_SL", "SUMMARYFEATURES_ML", "SUMMARYFEATURES_ML_INHERITS" ],
    "MATCH_FEATURES": [ "MATCHFEATURES_SL", "MATCHFEATURES_ML", "MATCHFEATURES_SL_INHERITS" ],
    "IMPORT FIELD": [ "IMPORT" ]
}

class MarkdownFile:

    name: str = ""
    content = ""

    def __init__(self, name: str):
        self.name = name
    
    def addContent(self, content: str):
        self.content += content
    
    def getContent(self) -> str:
        return self.content

    def __repr__(self) -> str:
        return self.__str__()
    
    def __str__(self) -> str:
        return f"NAME: {self.name}\n{self.content}\n\n"
    
    def __write(self, fullPath: pathlib.Path):
        with open(fullPath, "w") as file:
            file.write(self.content)

    def write(self, path: pathlib.Path):

        filename = self.name

        if filename in REPLACE_FILENAME_MAP:
            for fn in REPLACE_FILENAME_MAP[filename]:
                self.__write(path.joinpath(fn))
        else:
            self.__write(path.joinpath(filename))

        