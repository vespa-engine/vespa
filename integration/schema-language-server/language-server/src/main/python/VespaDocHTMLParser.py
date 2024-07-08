from html.parser import HTMLParser
from dataclasses import dataclass
from urllib.parse import urljoin

EXCLUDED_IDS = [
    "syntax",
    "elements"
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
                attr[1] = urljoin(linkPrefix, attr[1])
            
            self.attrs.append(attr)
        
        self.link = linkPrefix
    
    def addChild(self, child):
        self.children.append(child)

        if type(child) == Node:
            child.close()
    
    def closeNoteNode(self):

        for i, child in enumerate(self.children):
            if type(child) == str:

                childSplitted = child.split("content=\"")

                includeFile = childSplitted[0].split("include")[1].strip().split(".")[0]
                
                self.children[i] = f"*{includeFile.upper()}:* " + "".join(childSplitted[1:])

                break
        
        for i, child in reversed(list(enumerate(self.children))):
            if type(child) == str:

                self.children[i] = "".join(child.split("\"")[:-1])

                break
    
    def close(self):

        if (self.tag == "note"):
            return self.closeNoteNode()

        #TODO: remove this, only used for debugging
        if (self.tag != "parent"):
            return

        noteNode = None

        newChildren = []
        
        for child in self.children:

            if noteNode is None:
                newChildren.append(child)
            else:
                noteNode.addChild(child)


            if type(child) == str:
                
                if "{%" in child and noteNode is None:
                    newChildren.pop()
                    noteNode = Node("note")
                    childSplitted = child.split("{%")

                    if len(childSplitted[0]) > 0:
                        newChildren.append(childSplitted[0])
                    
                    if len(childSplitted[1]) > 0:

                        toNoteNode = childSplitted[1]

                        if "%}" in childSplitted[1]:
                            toNoteNode = childSplitted[1].split("%}")[0]
                        
                        if len(toNoteNode) > 0:
                            noteNode.addChild(toNoteNode)
                    
                if "%}" in child and noteNode is not None:
                    childSplitted = child.split("%}")
                    noteNode.children.pop()

                    if len(childSplitted[0]) > 0:
                        toNoteNode = childSplitted[0] 
                        if "{%" in childSplitted[0]:
                            toNoteNode = childSplitted[0].split("{%")[1]
                        
                        if len(toNoteNode) > 0:
                            noteNode.addChild(toNoteNode)

                    newChildren.append(noteNode)
                    noteNode.close()

                    noteNode = None

                    if len(childSplitted[1]) > 0:
                        newChildren.append(childSplitted[1])
        
        if noteNode is not None:
            newChildren.append(noteNode)
            noteNode.close()
        
        self.children = newChildren
    
    def __str__(self) -> str:
        ret = f"<{self.tag}>"

        # for child in self.children:
        #     ret += "\n\t" + str(child).replace('\n', '\n\t')

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
        
        if self.tag == "note":
            return "\n> " + ret.replace("\n", "\n> ") + "\n"
        
        return ret

    def getName(self):
        if (self.tag != "parent"):
            raise Exception("The getFilename method should only be called on the parent Node")

        return self.children[0].getContentStr()


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

    def getTags(self) -> list[Tag]:

        return self.enconteredTags