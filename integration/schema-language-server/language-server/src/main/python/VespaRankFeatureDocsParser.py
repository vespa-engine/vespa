from Node import Node, NodeType, Position
from visitor import Visitor
from MarkdownFile import MarkdownFile
import re

class VespaRankFeatureDocsParser(Visitor):

    results: list[MarkdownFile] = []

    exceptions: list[Exception] = []

    def __init__(self):
        self.results = []
        self.exceptions = []
    
    def findTDNodes(self, node: Node) -> list[Node]:

        ret: list[Node] = []

        for child in node.children:
            if (child.type == NodeType.DOM and child.getTag() == "td"):
                ret.append(child)
        
        return ret
    
    def enforceSimpleLintRules(self, mdFile: MarkdownFile, position: Position):

        name = mdFile.name

        namingRegex = "[a-z][a-zA-Z0-9]*"
        parameterList = f"(\\(({namingRegex}(\\,({namingRegex}))*)(\\,\\.\\.\\.)?\\))"

        success = re.search(f"^{namingRegex}{parameterList}?(\\.{namingRegex})" + "{0,2}$", name)

        RULES = f"""
        - Feature name, parameters and properties should be in camelcase.
        - No space after the commas in the parameterlist
        """

        if not success:
            self.exceptions.append(Exception(f"Invalid function signature formatting for function {name} at line {position.line}, the name should match this form: nativeRank(length,field,...).example. Summary fo some of the rules:\n{RULES}"))
    
    def readRelevantText(self, node: Node):
        if (node.getAttr("class") == "trx"):
            return

        if (len(node.children) < 3):
            return

        tdChildren = self.findTDNodes(node)

        if (len(tdChildren) != 3):
            return
        
        nameNode = tdChildren[0]

        mdFile = MarkdownFile(nameNode.toText().strip())

        contentNode = tdChildren[2]

        mdFile.addContent(contentNode.toMarkdown())

        self.enforceSimpleLintRules(mdFile, nameNode.getStartPosition())

        self.results.append(mdFile)


    def handleNode(self, node: Node):
        if (node.getTag() == "tr"):
            self.readRelevantText(node)
    
    def getResults(self):

        if (len(self.exceptions) > 0):
            raise ExceptionGroup("Naming of rank features do not comply with naming rules.", self.exceptions)

        return self.results