from html.parser import HTMLParser
from dataclasses import dataclass
from urllib.parse import urljoin
from Node import Node

class TDElm:

    content: str
    node: Node

    def __init__(self):
        self.content = ""

    def addText(self, content):
        self.content += content
    
    def getContent(self) -> str:
        return self.content

    def setNode(self, node: Node):
        self.node = node
    
    def getNode(self) -> Node:
        return self.node

class TRElm:

    children: list

    def __init__(self):
        self.children = []
    
    def addChild(self, child: TDElm):
        self.children.append(child)
    
    def __str__(self):
        return f"TR({self.children[0].getContent()}, {self.children[2].getContent()})"

    def __repr__(self) -> str:
        return self.__str__()

    def getFunctionIdentifier(self) -> str:
        return self.children[0].getContent().replace(", ", ",").replace("input_1,input_2,...", "input,...")

    def getMarkdownContent(self) -> str:
        rawContent = self.children[2].getNode().toMarkdown(False)
        returnContent = ""
        for line in rawContent.split("\n"):
            returnContent += line.strip() + "\n"
        
        return returnContent.strip()

class RankExpressionHTMLParser(HTMLParser):
    
    insideTable = False

    tableRows: list = []
    currentTR = None
    currentTD = None

    linkPrefix = ""

    parseStack = []

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
            self.parseStack = [Node("parent", f"{self.linkPrefix}")]

        elif (self.currentTD is not None):
            self.parseStack.append(Node(tag, self.linkPrefix, attrs))

    
    def handle_data(self, data):
        if self.currentTD is not None:
            self.currentTD.addText(data)
            self.parseStack[-1].addChild(data)
    
    def handle_endtag(self, tag):
        
        if (tag == "table"):
            self.insideTable = False
        
        if (tag == "tr"):
            if len(self.currentTR.children) == 3:
                self.tableRows.append(self.currentTR)
            self.currentTR = None
        
        while len(self.parseStack) > 1:
            elm = self.parseStack.pop()
            self.parseStack[-1].addChild(elm)
            if (elm.tag == tag):
                break
        
        if (tag == "td"):
            self.currentTD.setNode(self.parseStack[0])
            self.parseStack[0].close()
            self.currentTR.addChild(self.currentTD)
            self.currentTD = None
    
    def getRows(self):
        return self.tableRows
