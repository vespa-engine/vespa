from typing import Optional
from enum import Enum

class NodeType(Enum):

    DOM = 1
    PLAIN_TEXT = 2
    ROOT = 3

class Position:

    line: int = 0
    col: int = 0

    def __init__(self, line: int, col: int):
        self.line = line
        self.col = col
    
    def __str__(self) -> str:
        return f"{self.line + 1}:{self.col + 1}"
    
    def __add__(self, other):
        ret = Position(self.line, self.col)

        if (other.line == 0):
            ret.col += other.col
        else:
            ret.line += other.line
            ret.col = other.col
        
        return ret

class Node:

    parent: 'Optional[Node]' = None
    children: 'list[Node]' = []

    tagName: str = ""
    attrs: list[tuple[str, str | None]] = []

    type: NodeType = NodeType.ROOT
    plainTextContent: str = ""

    def __init__(self):
        self.children = []
    
    @staticmethod
    def createHTMLNode(tagName: str, attrs: list[tuple[str, str | None]]):
        ret = Node()
        ret.tagName = tagName
        ret.attrs = attrs
        ret.type = NodeType.DOM

        return ret
    
    @staticmethod
    def createTextNode(content: str):
        ret = Node()
        ret.tagName = ""
        ret.attrs = []
        ret.type = NodeType.PLAIN_TEXT
        ret.plainTextContent = content

        return ret

    def __repr__(self) -> str:
        return f"Node({self.type}, {len(self.children)})"
    
    def addChild(self, node: 'Node'):
        self.children.append(node)
        node.setParent(self)
    
    def setParent(self, node: 'Node'):
        self.parent = node
    
    def getTag(self) -> str:
        return self.tagName
    
    def getChild(self, i: int) -> 'Node':
        return self.children[i]
    
    def getStartPosition(self) -> Position:
        if (self.type == NodeType.ROOT):
            return Position(0, 0)
        
        if (self.parent is None):
            raise Exception("Cannot find the position before a parent is set")

        startPosition = self.parent.getStartPosition()

        for child in self.parent.children:
            if (child == self):
                break
            
            startPosition += child.getCharLength()

        return startPosition


    def getCharLength(self) -> Position:
        source = self.getRawSource()
        line = source.count("\n")
        lastSegmentStart = source.rfind("\n")
        col = len(source) - lastSegmentStart
        return Position(line, col)
    
    def getRawSource(self) -> str:
        if (self.type == NodeType.PLAIN_TEXT):
            return self.plainTextContent
        
        ret = ""
        
        if (self.type == NodeType.DOM):
            ret += f"<{self.tagName}"

            for attr in self.attrs:
                ret += f" {attr[0]}"
                if (attr[1] is not Node):
                    ret += f"=\"{attr[1]}\""
            
            ret += ">"

        for child in self.children:
            ret += f"{child.getRawSource()}"
        
        if (self.type == NodeType.DOM):
            ret += f"</{self.tagName}>"

        return ret
    
    def toHTML(self) -> str:

        if (self.type == NodeType.PLAIN_TEXT):
            return self.plainTextContent.strip()
        
        ret = ""
        
        if (self.type == NodeType.DOM):
            ret += f"<{self.tagName}"

            for attr in self.attrs:
                ret += f" {attr[0]}"
                if (attr[1] is not Node):
                    ret += f"=\"{attr[1]}\""
            
            ret += ">"
        
        tabSize = 0 if self.type == NodeType.ROOT else 2
        tab = " " * tabSize

        for child in self.children:
            ret += f"\n{tab}{child.toHTML().replace("\n", f"\n{tab}")}"
        
        if (self.type == NodeType.DOM):
            ret += f"\n</{self.tagName}>"

        return ret
