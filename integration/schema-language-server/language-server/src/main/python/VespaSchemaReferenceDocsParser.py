from Node import Node
from visitor import Visitor
from MarkdownFile import MarkdownFile

class VespaSchemaReferenceDocsParser(Visitor):

    results: list[MarkdownFile] = []
    readMoreLink = ""

    def __init__(self, readMoreLink: str):
        self.results = []
        self.readMoreLink = readMoreLink
    
    def readRelevantText(self, node: Node):
        mdFile = MarkdownFile(node.toText().strip().upper())


        currentNode = node
        while (
            currentNode is not None and
            currentNode.getTag() != "table" and
            (
                currentNode.getTag() != "h2" or
                currentNode == node
            )
            ):
            mdFile.addContent(currentNode.toMarkdown() + "\n")
            currentNode = currentNode.getNext()
        
        mdFile.addContent(f"\n\n[Read more]({self.readMoreLink}#{node.getAttr("id")})")

        mdFile.content = node.cleanupMarkdown(mdFile.content.strip())

        self.results.append(mdFile)

    def handleNode(self, node: Node):
        if (node.getTag() == "h2"):
            self.readRelevantText(node)
    
    def getResults(self):
        return self.results