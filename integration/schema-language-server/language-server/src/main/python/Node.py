from urllib.parse import urljoin

class Node:
    tag: str
    attrs: list = []
    children: list = []
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

        singleQuote = False

        for i, child in enumerate(self.children):
            if type(child) == str:


                childSplitted = child.split("content=\"")
                if (len(childSplitted) == 1):
                    childSplitted = child.split("content='")
                    singleQuote = True

                includeSplit = childSplitted[0].split("include")
                if len(includeSplit) <= 1:
                    return
                includeFile = includeSplit[1].strip().split(".")[0]
                
                self.children[i] = f"*{includeFile.upper()}:* " + "".join(childSplitted[1:])

                break
        
        for i, child in reversed(list(enumerate(self.children))):
            if type(child) == str:

                splitChar = "'" if singleQuote else "\""
                
                self.children[i] = "".join(child.split(splitChar)[:-1])

                break
    
    def close(self):

        if (self.tag == "note"):
            return self.closeNoteNode()

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
            ret += child.__str__()
        
        return ret

    def getAttr(self, type: str) -> str:
        for attr in self.attrs:
            if (attr[0] == type):
                return attr[1]
        
        return ""
    
    def toMarkdown(self, readMoreLink = False) -> str:
        ret = ""

        if (self.tag == "h2"):
            return "## " + self.getContentStr() + "\n"
        
        if (self.tag == "a"):
            return f"[{self.getContentStr()}]({self.getAttr('href')})"
        
        if (self.tag == "pre"):
            ret += "```"
        
        if (self.tag == "code"):
            ret += "`"

        for child in self.children:
            if (type(child) == Node):
                ret += child.toMarkdown(readMoreLink)
            else:
                ret += child
        
        if (self.tag == "pre"):
            ret += "``` \n"
        
        if (self.tag == "code"):
            ret += "`"

        if (self.tag == "parent" and len(self.link) > 0 and readMoreLink):
            ret += f"\n[Read more]({self.link})"
        
        if self.tag == "note":
            return "\n> " + ret.replace("\n", "\n> ") + "\n"
        
        return ret

    def getName(self):
        if (self.tag != "parent"):
            raise Exception("The getFilename method should only be called on the parent Node")

        return self.children[0].getContentStr()