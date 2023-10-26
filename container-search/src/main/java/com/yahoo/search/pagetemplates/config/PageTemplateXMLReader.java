// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.config;

import com.yahoo.component.ComponentId;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.PageTemplateRegistry;
import com.yahoo.search.pagetemplates.model.*;
import com.yahoo.search.query.Sorting;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Reads all page template XML files from a given directory (or list of readers).
 * Instances of this are for single-thread usage only.
 *
 * @author bratseth
 */
public class PageTemplateXMLReader {

    private static Logger logger=Logger.getLogger(PageTemplateXMLReader.class.getName());

    /** The registry being constructed */
    private PageTemplateRegistry registry;

    /** XML elements by page id - available after phase 1. Needed for includes. */
    private Map<ComponentId, Element> pageElementsByPageId=new LinkedHashMap<>();

    /**
     * Reads all page template xml files in a given directory.
     *
     * @throws RuntimeException if <code>directory</code> is not a readable directory, or if there is some error in the XML
     */
    public PageTemplateRegistry read(String directory) {
        List<NamedReader> pageReaders = new ArrayList<>();
        try {
            File dir = new File(directory);
            if ( ! dir.isDirectory() ) throw new IllegalArgumentException("Could not read page templates: '" +
                                                                          directory + "' is not a valid directory.");

            for (File file : sortFiles(dir)) {
                if ( ! file.getName().endsWith(".xml")) continue;
                pageReaders.add(new NamedReader(file.getName(), new FileReader(file)));
            }

            return read(pageReaders, true);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read page templates from '" + directory + "'",e);
        }
        finally {
            for (NamedReader reader : pageReaders) {
                try { reader.close(); } catch (IOException e) { }
            }
        }
    }

    /**
     * Reads a single page template file.
     *
     * @throws RuntimeException if <code>fileName</code> is not a readable file, or if there is some error in the XML
     */
    public PageTemplate readFile(String fileName) {
        NamedReader pageReader = null;
        try {
            File file = new File(fileName);
            pageReader = new NamedReader(fileName,new FileReader(file));
            String firstName = file.getName().substring(0, file.getName().length() - 4);
            return read(Collections.singletonList(pageReader), true).getComponent(firstName);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read the page template '" + fileName + "'", e);
        }
        finally {
            if (pageReader != null)
                try { pageReader.close(); } catch (IOException e) { }
        }
    }

    private List<File> sortFiles(File dir) {
        ArrayList<File> files = new ArrayList<>();
        files.addAll(Arrays.asList(dir.listFiles()));
        Collections.sort(files);
        return files;
    }

    /**
     * Reads all page template xml files in a given list of readers. This is called from the Vespa configuration model.
     *
     * @param validateReaderNames should be set to true if the readers were created by files, not otherwise
     * @throws RuntimeException if <code>directory</code> is not a readable directory, or if there is some error in the XML
     */
    public PageTemplateRegistry read(List<NamedReader> pageReaders,boolean validateReaderNames) {
        // Initialize state
        registry=new PageTemplateRegistry();

        // Phase 1
        pageElementsByPageId=createPages(pageReaders,validateReaderNames);
        // Phase 2
        readPages();
        return registry;
    }

    private Map<ComponentId,Element> createPages(List<NamedReader> pageReaders,boolean validateReaderNames) {
        Map<ComponentId,Element> pageElementsByPageId=new LinkedHashMap<>();
        for (NamedReader reader : pageReaders) {
            Element pageElement= XML.getDocument(reader).getDocumentElement();
            if ( ! pageElement.getNodeName().equals("page")) {
                logger.info("Ignoring '" + reader.getName() +
                         "': Expected XML root element 'page' but was '" + pageElement.getNodeName() + "'");
                continue;
            }
            String idString=pageElement.getAttribute("id");

            if (idString==null || idString.isEmpty())
                throw new IllegalArgumentException("Page template '" + reader.getName() + "' has no 'id' attribute in the root element");
            ComponentId id=new ComponentId(idString);
            if (validateReaderNames)
                validateFileName(reader.getName(),id,"page template");
            registry.register(new PageTemplate(id));
            pageElementsByPageId.put(id,pageElement);
        }
        return pageElementsByPageId;
    }

    /** Throws an exception if the name is not corresponding to the id */
    private void validateFileName(String actualName, ComponentId id, String artifactName) {
        String expectedCanonicalFileName = id.toFileName();
        String fileName = new File(actualName).getName();
        fileName = stripXmlEnding(fileName);
        String canonicalFileName = ComponentId.fromFileName(fileName).toFileName();
        if ( ! canonicalFileName.equals(expectedCanonicalFileName))
            throw new IllegalArgumentException("The file name of " + artifactName + " '" + id +
                                               "' must be '" + expectedCanonicalFileName + ".xml' but was '" + actualName + "'");
    }

    private String stripXmlEnding(String fileName) {
        if (!fileName.endsWith(".xml"))
            throw new IllegalArgumentException("'" + fileName + "' should have a .xml ending");
        else
            return fileName.substring(0, fileName.length() - 4);
    }

    private void readPages() {
        for (Map.Entry<ComponentId,Element> pageElement : pageElementsByPageId.entrySet()) {
            try {
                PageTemplate page = registry.getComponent(pageElement.getValue().getAttribute("id"));
                readPageContent(pageElement.getValue(), page);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Could not read page template '" + pageElement.getKey() + "'",e);
            }
        }
    }

    private void readPageContent(Element pageElement, PageTemplate page) {
        if (page.isFrozen()) return; // Already read
        Section rootSection = new Section(page.getId().toString());
        readSection(pageElement, rootSection);
        page.setSection(rootSection);
        page.freeze();
    }

    /** Fills a section with attributes and sub-elements from a "section" or "page" element */
    private Section readSection(Element sectionElement, Section section) {
        section.setLayout(Layout.fromString(sectionElement.getAttribute("layout")));
        section.setRegion(sectionElement.getAttribute("region"));
        section.setOrder(Sorting.fromString(sectionElement.getAttribute("order")));
        section.setMax(readOptionalNumber(sectionElement,"max"));
        section.setMin(readOptionalNumber(sectionElement,"min"));
        section.elements().addAll(readSourceAttribute(sectionElement));
        section.elements().addAll(readPageElements(sectionElement));
        return section;
    }

    /** Returns all page elements found under the given node */
    private List<PageElement> readPageElements(Element parent) {
        List<PageElement> pageElements=new ArrayList<>();
        for (Element child : XML.getChildren(parent)) {
            if (child.getNodeName().equals("include"))
                pageElements.addAll(readInclude(child));
            else
                addIfNonNull(readPageElement(child),pageElements);
        }
        return pageElements;
    }

    private void addIfNonNull(PageElement pageElement,List<PageElement> pageElements) {
        if (pageElement!=null)
            pageElements.add(pageElement);
    }

    /** Reads the direct descendant elements of an include */
    private List<PageElement> readInclude(Element element) {
        PageTemplate included = registry.getComponent(element.getAttribute("idref"));
        if (included == null)
            throw new IllegalArgumentException("Could not find page template '" + element.getAttribute("idref"));
        readPageContent(pageElementsByPageId.get(included.getId()), included);
        return included.getSection().elements(Section.class);
    }

    /** Returns the page element corresponding to the given node, never null */
    private PageElement readPageElement(Element child) {
        if (child.getNodeName().equals("choice"))
            return readChoice(child);
        else if (child.getNodeName().equals("source"))
            return readSource(child);
        else if (child.getNodeName().equals("placeholder"))
            return readPlaceholder(child);
        else if (child.getNodeName().equals("section"))
            return readSection(child,new Section(child.getAttribute("id")));
        else if (child.getNodeName().equals("renderer"))
            return readRenderer(child);
        else if (child.getNodeName().equals("parameter"))
            return null; // read elsewhere
        throw new IllegalArgumentException("Unknown node type '" + child.getNodeName() + "'");
    }

    private List<Source> readSourceAttribute(Element sectionElement) {
        List<Source> sources = new ArrayList<>();
        String sourceAttributeString = sectionElement.getAttribute("source");
        if (sourceAttributeString != null) {
            for (String sourceName : sourceAttributeString.split(" ")) {
                if (sourceName.isEmpty()) continue;
                if ("*".equals(sourceName))
                    sources.add(Source.any);
                else
                    sources.add(new Source(sourceName));
            }
        }
        return sources;
    }

    private Source readSource(Element sourceElement) {
        Source source=new Source(sourceElement.getAttribute("name"));
        source.setUrl(nullIfEmpty(sourceElement.getAttribute("url")));
        source.renderers().addAll(readPageElements(sourceElement));
        /*
        source.renderers().addAll(readRenderers(XML.children(sourceElement,"renderer")));
        readChoices(sourceElement,source);
        */
        source.parameters().putAll(readParameters(sourceElement));
        return source;
    }

    private String nullIfEmpty(String s) {
        if (s==null) return s;
        s=s.trim();
        if (s.isEmpty()) return null;
        return s;
    }

    private Placeholder readPlaceholder(Element placeholderElement) {
        return new Placeholder(placeholderElement.getAttribute("id"));
    }

    private Renderer readRenderer(Element rendererElement) {
        Renderer renderer =new Renderer(rendererElement.getAttribute("name"));
        renderer.setRendererFor(nullIfEmpty(rendererElement.getAttribute("for")));
        renderer.parameters().putAll(readParameters(rendererElement));
        return renderer;
    }

    private int readOptionalNumber(Element element,String attributeName) {
        String attributeValue=element.getAttribute(attributeName);
        try {
            if (attributeValue.isEmpty()) return -1;
            return Integer.parseInt(attributeValue);
        }
        catch (NumberFormatException e) { // Suppress original exception as it conveys no useful information
            throw new IllegalArgumentException("'" + attributeName + "' in " + element + " must be a number, not '" + attributeValue + "'");
        }
    }

    private AbstractChoice readChoice(Element choiceElement) {
        String method=nullIfEmpty(choiceElement.getAttribute("method"));
        if (XML.getChildren(choiceElement,"map").size()>0)
            return readMapChoice(choiceElement,method);
        else
            return readNonMapChoice(choiceElement,method);
    }

    private MapChoice readMapChoice(Element choiceElement,String method) {
        Element mapElement=XML.getChildren(choiceElement,"map").get(0);
        MapChoice map=new MapChoice();
        map.setMethod(method);

        map.placeholderIds().addAll(readSpaceSeparatedAttribute("to",mapElement));
        for (Element value : XML.getChildren(mapElement)) {
            if ("item".equals(value.getNodeName()))
                map.values().add(readPageElements(value));
            else
                map.values().add(Collections.singletonList(readPageElement(value)));
        }
        return map;
    }

    private Choice readNonMapChoice(Element choiceElement,String method) {
        Choice choice=new Choice();
        choice.setMethod(method);

        for (Element alternative : XML.getChildren(choiceElement)) {
            if (alternative.getNodeName().equals("alternative")) // Explicit alternative container
                choice.alternatives().add(readPageElements(alternative));
            else if (alternative.getNodeName().equals("include")) // Implicit include
                choice.alternatives().add(readInclude(alternative));
            else // Other implicit
                choice.alternatives().add(Collections.singletonList(readPageElement(alternative)));
        }
        return choice;
    }

    /*
    private void readChoices(Element sourceElement,Source source) {
        for (Element choiceElement : XML.children(sourceElement,"choice")) {
            for (Element alternative : XML.children(choiceElement)) {
                if ("alternative".equals(alternative.getNodeName())) // Explicit alternative container
                    source.renderer().alternatives().addAll(readRenderers(XML.children(alternative)));
                else // Implicit alternative - yes implicit and explicit may be combined
                    source.renderer().alternatives().addAll(readRenderers(Collections.singletonList(alternative)));
            }
        }
    }
    */

    private Map<String,String> readParameters(Element containingElement) {
        List<Element> parameterElements=XML.getChildren(containingElement,"parameter");
        if (parameterElements.size()==0) return Collections.emptyMap(); // Shortcut

        Map<String,String> parameters=new LinkedHashMap<>();
        for (Element parameter : parameterElements) {
            String key=parameter.getAttribute("name");
            String value=XML.getValue(parameter);
            parameters.put(key,value);
        }
        return parameters;
    }

    private List<String> readSpaceSeparatedAttribute(String attributeName, Element containingElement) {
        List<String> values=new ArrayList<>();
        String attributeString=nullIfEmpty(containingElement.getAttribute(attributeName));
        if (attributeString!=null) {
            for (String value : attributeString.split(" "))
                values.add(value);
        }
        return values;
    }

}
