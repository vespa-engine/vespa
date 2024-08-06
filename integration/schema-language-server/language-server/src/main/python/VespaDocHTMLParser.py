from html.parser import HTMLParser
from dataclasses import dataclass

from Node import Node

EXCLUDED_IDS = [
    "syntax",
    "elements"
]

@dataclass
class Tag:
    id: str
    content: str = ""
    AST: Node = None

class VespaDocHTMLParser(HTMLParser):
    enconteredTags = []
    inputText: str

    currentReadingTag = None
    parseStack = []

    linkPrefix = ""

    def __init__(self, linkPrefix):
        super().__init__()
        self.linkPrefix = linkPrefix

    def parse(self, input: str):
        super().parse(input)
        self.inputText = input

    def stopReading(self):
        if (self.currentReadingTag is None):
            return

        self.currentReadingTag.endPos = self.getpos()

        while len(self.parseStack) > 1:
            elm = self.parseStack.pop()
            self.parseStack[-1].addChild(elm)

        self.currentReadingTag.AST = self.parseStack[0]
        self.currentReadingTag.AST.close()
        self.enconteredTags.append(self.currentReadingTag)
        self.currentReadingTag = None
    
    def startReading(self, name):
        self.parseStack = [Node("parent", f"{self.linkPrefix}#{name}")]
        self.currentReadingTag = Tag(name)

    def handle_starttag(self, tag, attrs):
        if (self.currentReadingTag is not None and tag == "table"):
            self.stopReading()

        for attr in attrs:
            if attr[0] == "id":

                self.stopReading()

                if tag == "h2" and attr[1] not in EXCLUDED_IDS:
                    self.startReading(attr[1])
        
        if (self.currentReadingTag is not None):
            self.currentReadingTag.content += f"<{tag}>"

            self.parseStack.append(Node(tag, self.linkPrefix, attrs))
    
    def handle_data(self, data):
        
        if (self.currentReadingTag is not None):
            self.currentReadingTag.content += data
            self.parseStack[-1].addChild(data)
    
    def handle_endtag(self, tag):
        
        if self.currentReadingTag is not None:
            self.currentReadingTag.content += f"</{tag}>"

            while len(self.parseStack) > 1:
                elm = self.parseStack.pop()
                self.parseStack[-1].addChild(elm)
                if (elm.tag == tag):
                    break

    def getTags(self) -> list:

        return self.enconteredTags
