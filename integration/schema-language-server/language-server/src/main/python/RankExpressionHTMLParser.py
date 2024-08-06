from html.parser import HTMLParser
from dataclasses import dataclass
from urllib.parse import urljoin


class TDElm:

    content: str

    def __init__(self):
        self.content = ""

    def addText(self, content):
        self.content += content
    
    def getContent(self) -> str:
        return self.content

class TRElm:

    children: list[TDElm]

    def __init__(self):
        self.children = []
    
    def addChild(self, child: TDElm):
        self.children.append(child)
    
    def __str__(self):
        return f"TR({self.children[0].getContent()}, {self.children[2].getContent()})"

    def __repr__(self) -> str:
        return self.__str__()

    def getFunctionIdentifier(self) -> str:
        return self.children[0].getContent()

    def getMarkdownContent(self) -> str:
        rawContent = self.children[2].getContent()
        returnContent = ""
        for line in rawContent.split("\n"):
            returnContent += line.strip() + "\n"
        
        return returnContent.strip()

class RankExpressionHTMLParser(HTMLParser):
    
    insideTable = False

    tableRows: list[TRElm] = []
    currentTR = None
    currentTD = None

    linkPrefix = ""

    def __init__(self, linkPrefix):
        super().__init__()
        self.linkPrefix = linkPrefix

    def parse(self, input: str):
        super().parse(input)
        self.inputText = input

    def handle_starttag(self, tag, attrs):
        if (tag == "table"):
            self.insideTable = True
        
        if (not self.insideTable):
            return
        
        if (tag == "tr"):
            self.currentTR = TRElm()
        
        if (tag == "td"):
            self.currentTD = TDElm()


    
    def handle_data(self, data):
        if self.currentTD is not None:
            self.currentTD.addText(data)
    
    def handle_endtag(self, tag):
        
        if (tag == "table"):
            self.insideTable = False
        
        if (tag == "tr"):
            if len(self.currentTR.children) == 3:
                self.tableRows.append(self.currentTR)
            self.currentTR = None
        
        if (tag == "td"):
            self.currentTR.addChild(self.currentTD)
            self.currentTD = None
    
    def getRows(self):
        return self.tableRows
