from Node import Node
from urllib.parse import urljoin
from MarkdownFile import MarkdownFile

class Visitor:

    def __init__(self):
        pass

    def traverse(self, node: Node):
        self.handleNode(node)

        for child in node.children:
            self.traverse(child)
    
    def handleNode(self, node: Node):
        pass

    def getResults(self) -> list[MarkdownFile]:
        return []

class SwitchInternalURL(Visitor):

    URL_PREFIX: str = ""

    def __init__(self, URL_PREFIX: str):
        self.URL_PREFIX = URL_PREFIX

    def handleNode(self, node: Node):
        if node.getTag() == "a":
            link = node.getAttr("href")
            if (link is not None):
                newUrl = urljoin(self.URL_PREFIX, link)
                node.setAttr("href", newUrl)