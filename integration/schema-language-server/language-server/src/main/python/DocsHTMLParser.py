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

        preParseResults = self.findNoteNodes(input)

        for results in preParseResults:
            if isinstance(results, str):
                super().feed(results)
            else:
                self.getTopNode().addChild(results)

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

    def findNoteNodes(self, rawData: str) -> list[str | Node]:
        splits = rawData.split("{%")

        ret = [splits[0]]

        for i in range(1, len(splits)):
            index = splits[i].find("%}")
            noteData = splits[i][0:index]
            node = self.parseNoteData(noteData)
            ret.append(node)
            remainingText = splits[i][index + 2:]
            ret.append(remainingText)
        
        return ret
    
    def parseNoteData(self, input: str) -> Node:

        firstSplit = input.split("content=")

        print(firstSplit)

        name = firstSplit[0].strip()

        node = Node.createNoteNode(name, "{%" + input + "%}")

        if (len(firstSplit) > 1):
            content = firstSplit[1][1:-1]

            newParser = DocsHTMLParser(self.fileName)
            newParser.parseStack = [node]
            newParser.parse(content)

        return node