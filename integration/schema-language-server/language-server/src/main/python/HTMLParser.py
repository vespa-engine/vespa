from html.parser import HTMLParser
from Node import Node

class DocsHTMLParser(HTMLParser):

    parseStack: list[Node] = []

    inputText: str = ""
    fileName: str = ""

    errors: list[Exception] = []

    def __init__(self, fileName: str):
        super().__init__()
        self.fileName = fileName

        self.parseStack = [Node()]
    
    def parse(self, input: str) -> Node:
        super().feed(input)
        self.inputText = input

        while (len(self.parseStack) > 1):
            topNode = self.parseStack.pop()
            self.errors.append(Exception(f"Unclosed HTML tag {topNode.getTag()} at {topNode.getStartPosition()}"))
        
        if len(self.errors) > 0:
            raise ExceptionGroup(f"Errors occuring when parsing documentation html, source file: {self.fileName}", self.errors)
        
        return self.getTopNode()
    
    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        newNode = Node.createHTMLNode(tag, attrs)
        self.getTopNode().addChild(newNode)

        self.parseStack.append(newNode)
    
    def getTopNode(self):
        return self.parseStack[len(self.parseStack) - 1]
    
    def handle_endtag(self, tag: str) -> None:
        
        while (len(self.parseStack) > 1):
            topNode = self.parseStack.pop()
            if (topNode.getTag() == tag):
                break
            else:
                self.errors.append(Exception(f"Unclosed HTML tag {topNode.getTag()} at {topNode.getStartPosition()}"))
    
    def handle_data(self, data: str) -> None:
        textNode = Node.createTextNode(data)
        self.getTopNode().addChild(textNode)