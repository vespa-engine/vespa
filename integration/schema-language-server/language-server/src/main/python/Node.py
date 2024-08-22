from typing import Optional
from enum import Enum

class NodeType(Enum):

    DOM = 1
    PLAIN_TEXT = 2
    ROOT = 3
    NOTE = 4

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

    @staticmethod
    def createNoteNode(type: str, rawText: str):
        ret = Node()
        ret.tagName = type
        ret.attrs = []
        ret.type = NodeType.NOTE
        ret.plainTextContent = rawText

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
    
    def getSiblingIndex(self) -> int:
        if (self.parent is None):
            return 0
        
        for i in range(len(self.parent.children)):
            if (self.parent.getChild(i) == self):
                return i
        
        raise Exception("Invalid AST, the a Node has a parent, but the parent has no reference to the node.")
    
    def getNext(self) -> 'Optional[Node]':
        parent = self.parent
        if (parent is None):
            return None

        siblingIndex = self.getSiblingIndex()
        if (len(parent.children) <= siblingIndex + 1):
            return None
        return parent.getChild(siblingIndex + 1)
        

    def getAttr(self, attribute: str) -> str | None:
        for attr in self.attrs:
            if (attr[0] == attribute):
                return attr[1]
        
        return None

    def setAttr(self, attribute: str, value: str):
        for i in range(len(self.attrs)):
            if (self.attrs[i][0] == attribute):
                self.attrs[i] = list(self.attrs[i])
                self.attrs[i][1] = value
                return
        
        self.attrs.append((attribute, value))
    
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
        if (self.type == NodeType.PLAIN_TEXT or self.type == NodeType.NOTE):
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
    
    def toText(self) -> str:
        if (self.type == NodeType.PLAIN_TEXT):
            return self.plainTextContent
        
        ret = ""
        for child in self.children:
            ret += child.toText()
        
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
        
        if (self.type == NodeType.NOTE):
            if (self.tagName.find("highlight") != -1):
                return ""
            
            ret += f"(Note {self.tagName})"
        
        tabSize = 0 if self.type == NodeType.ROOT else 2
        tab = " " * tabSize

        for child in self.children:
            ret += f"\n{tab}{child.toHTML().replace("\n", f"\n{tab}")}"
        
        if (self.type == NodeType.DOM):
            ret += f"\n</{self.tagName}>"

        return ret
    
    def markdownInFront(self, prefix: str, content: str) -> str:

        lines = content.split("\n")
        ret: str = ""
        for line in lines:
            ret += prefix + line.strip()

        return ret

    def markdownWrap(self, wrapStart: str, wrapEnd: str, content: str) -> str:
        return wrapStart + content + wrapEnd
    
    def __htmlToMarkdown(self, content: str) -> str:

        if (self.tagName == "h2"):
            return self.markdownInFront("## ", content)
        
        if (self.tagName == "pre" or self.tagName == "code"):

            if (content.count("\n") > 0):
                return self.markdownWrap("```\n", "\n```", content)
            return self.markdownWrap("`", "`", content)
        
        if (self.tagName == "li"):
            return self.markdownInFront("- ", content)
        
        if (self.tagName == "em"):
            return self.markdownWrap("*", "*", content)

        return content

    def __removeMulipleNewLines(self, content) -> str:
        lines = content.split("\n")

        ret = ""
        lastLineEmpty = False
        for line in lines:
            linesStripped = line.strip()
            if (linesStripped == ""):
                if (not lastLineEmpty):
                    ret += line + "\n"
                lastLineEmpty = True
            else:
                ret += line + "\n"
                lastLineEmpty = False

        
        return ret[:-1]

    def __toMarkdown(self) -> str:

        if (self.type == NodeType.PLAIN_TEXT):
            return self.plainTextContent

        content: str = ""

        for child in self.children:
            content += child.__toMarkdown()
        
        if (self.type == NodeType.ROOT):
            return self.__removeMulipleNewLines(content)
        
        if (self.type == NodeType.NOTE):
            return self.markdownInFront("> ", content)
        
        if (self.type == NodeType.DOM):
            return self.__htmlToMarkdown(content)

        return ""

    def toMarkdown(self, entry = True) -> str:
        ret = self.__toMarkdown()
        if (entry):
            return self.__removeMulipleNewLines(ret)
        
        return ret
