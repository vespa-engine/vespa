from html.parser import HTMLParser
from dataclasses import dataclass

WANTED_IDS = [
    "schema",
    "document",
    "struct",
    "field",
    "fieldset",
    "rank-profile"
]

class Node:
    tag: str
    attrs: list[str] = []
    children = []
    link: str = ""

    def __init__(self, tag: str, linkPrefix: str = "", attrs = []):
        self.tag = tag
        self.children = []
        
        self.attrs = []

        for attrTuple in attrs:
            attr = list(attrTuple)
            if attr[0] == "href":
                attr[1] = linkPrefix + attr[1]
            
            self.attrs.append(attr)
        
        self.link = linkPrefix
    
    def addChild(self, child):
        self.children.append(child)
    
    def __str__(self) -> str:
        ret = f"{self.tag}:"

        for child in self.children:
            ret += "\n\t" + str(child).replace('\n', '\n\t')

        return ret

    def __repr__(self) -> str:
        return f"Node({self.tag}, {len(self.children)})"

    def toHTML(self) -> str:
        attrsStr = ""
        for a in self.attrs:
            attrsStr += f" {a[0]}=\"{a[1]}\""

        ret = f"\n<{self.tag}{attrsStr}>\n"

        for child in self.children:
            data = ""
            if (type(child) == Node):
                data = child.toHTML()
            else:
                data = child
            
            ret += "\t" + data.replace('\n', '\n\t')

        ret += f"\n</{self.tag}>\n"

        return ret

    def getContentStr(self) -> str:
        ret = ""

        for child in self.children:
            ret += child
        
        return ret

    def getAttr(self, type: str) -> str:
        for attr in self.attrs:
            if (attr[0] == type):
                return attr[1]
        
        return ""
    
    def toMarkdown(self) -> str:
        ret = ""

        if (self.tag == "h2"):
            return "## " + self.getContentStr() + "\n"
        
        if (self.tag == "a"):
            return f"[{self.getContentStr()}]({self.getAttr("href")})"
        
        if (self.tag == "pre"):
            ret += "```"
        
        if (self.tag == "code"):
            ret += "`"

        for child in self.children:
            if (type(child) == Node):
                ret += child.toMarkdown()
            else:
                ret += child
        
        if (self.tag == "pre"):
            ret += "``` \n"
        
        if (self.tag == "code"):
            ret += "`"

        if (self.tag == "parent" and len(self.link) > 0):
            ret += f"\n[Read more]({self.link})"
        
        return ret


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

                if attr[1] in WANTED_IDS:
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

    def getTags(self) -> list[Tag]:

        return self.enconteredTags