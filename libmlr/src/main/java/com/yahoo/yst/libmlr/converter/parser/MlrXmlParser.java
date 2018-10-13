// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.parser;

import com.yahoo.yst.libmlr.converter.XmlUtils;
import com.yahoo.yst.libmlr.converter.entity.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Logger;

/**
 * Parses Treenet output V5 into Abstract Treenet XML File format.
 *
 * @author allenwei
 */
public class MlrXmlParser {

    private static Logger logger = Logger.getLogger("com.yahoo.yst.libmlrutil.TnXmlParser");
    private static final String errNormAttr = "<Normalize>: All or none of attributes mean0-3, sd0-3, a0-3, b0-3 are required";
    private static final String errPolyAttr = "<Normalize>: All or none of attributes a0-3 are required";

    private HashSet<String> treeIdSet = new HashSet<String>(500);
    private HashSet<String> nodeIdSet = new HashSet<String>(10000);
    private HashSet<String> respIdSet = new HashSet<String>(10000);

    public MlrFunction parseXmlFile(String fileName) throws DecisionTreeXmlException {
        File file = new File(fileName);
        if ( ! file.exists()) {
            String errMsg = fileName + " does not exist.";
            logErrors(errMsg);
            throw new DecisionTreeXmlException(errMsg);
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try { // XXE prevention
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        }
        catch (ParserConfigurationException e) {
            throw new IllegalStateException("Could not disallow-doctype-decl", e);
        }
        DocumentBuilder docBuilder;

        try {
            docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(file);
            Element eltMlrFnc = doc.getDocumentElement();
            if (!eltMlrFnc.getTagName().equals("MlrFunction")) {
                String errMsg = "The top element must be <MlrFunction>";
                logErrors(errMsg);
                throw new DecisionTreeXmlException(errMsg);
            }

            return parseMlrFunction(eltMlrFnc);

            //System.out.println("features: " + tnFunc.getFeatureSet().size());
            //System.out.println("labels: " + tnFunc.getLabelSet().size());

        } catch (ParserConfigurationException pe) {
            String errMsg = "Cannot construct XML DocumentBuilder";
            logErrors(pe, errMsg);
            throw new DecisionTreeXmlException(errMsg, pe);

        } catch (DecisionTreeXmlException te) {
            throw te;

        } catch (Exception ex) {
            String errMsg ="Errors found parsing XML";
            logErrors(errMsg);
            ex.printStackTrace();
            throw new DecisionTreeXmlException(errMsg, ex);
        }
    }

    private MlrFunction parseMlrFunction(Element eltMlrFnc) {
        MlrFunction mlrFunc = null;

        Element eltDecisionTree = getFirstChildElementByName(eltMlrFnc, "DecisionTree", true);
        TreenetFunction tnFunc = new TreenetFunction();
        String id = getAttribute(eltMlrFnc, "name", true);
        try {
            Integer.parseInt(id);
        } catch (NumberFormatException nex) {
            throw new DecisionTreeXmlException("name in <MlrFunction> should be an integer " + id , nex);
        }
        tnFunc.setFunctionName(id);
        parseDecisionTree(eltDecisionTree, tnFunc);

        mlrFunc = tnFunc;

        if (mlrFunc != null) {
            Element eltEpilog = getFirstChildElementByName(eltMlrFnc, "Epilogue", false);
            if (eltEpilog != null) {
                Epilog epilog = parseEpilog(eltEpilog);
                mlrFunc.setEpilog(epilog);
            }
        }

        return mlrFunc;
    }

    private void parseDecisionTree(Element eltDecisionTree, TreenetFunction tnFunc) {
        parseForest(getFirstChildElementByName(eltDecisionTree, "Forest", true), tnFunc);

        Element eltEarlyExits = getFirstChildElementByName(eltDecisionTree, "EarlyExits", false);
        if (eltEarlyExits != null)
            parseEarlyExits(eltEarlyExits, tnFunc);
    }

    private void parseForest(Element eltForest, TreenetFunction tnFunc) {
        //String strTotal = eltForest.getAttribute("total");
        // tnFunc.setNumTrees(Integer.parseInt(eltForest.getAttribute("total")));

        ArrayList<Element> nl = XmlUtils.getChildrenByName(eltForest, "Tree");
        int n = nl.size();
        if (n == 0)
            throw new DecisionTreeXmlException("<Forest> should have at least one <Tree> element");

        for (int i = 0; i < n; i++) {
            parseTree(nl.get(i), tnFunc);
        }
    }

    private void parseTree(Element eltTree, TreenetFunction tnFunc) {
        String comment = getAttribute(eltTree, "comment", false);
        String id = getAttribute(eltTree, "id", true);
        if (treeIdSet.contains(id))
            throw new DecisionTreeXmlException("Duplicate tree id " + id);
        else
            treeIdSet.add(id);

        Tree tr = new Tree(id, comment);
        tnFunc.setTree(tr);

        // DEBUG
        //System.out.println("tree " + id);
        ArrayList<Element> list = XmlUtils.getChildrenByName(eltTree, "Node");
        if (list == null || list.size() != 1)
            throw new DecisionTreeXmlException("<Tree> should have exactly one root <Node> element");

        Element eltNode = list.get(0);
        InternalNode root = parseInternalNode(eltNode, tnFunc, tr);
        tr.setRoot(root);
    }

    private TreeNode parseTreeNode(Element eltNode, TreenetFunction tnFunc, Tree tr) {
        String tag = eltNode.getNodeName();
        if (tag.equals("Node"))
            return parseInternalNode(eltNode, tnFunc, tr);
        else if (tag.equals("Response"))
            return parseResponse(eltNode, tnFunc);
        else
            throw new DecisionTreeXmlException("ERROR: unknown tag <" + tag + ">. Should never reach here.");
    }

    private InternalNode parseInternalNode(Element eltNode, TreenetFunction tnFunc, Tree tr) {
        tr.incrInteralNodes();

        String id = getAttribute(eltNode, "id", true);
        if (nodeIdSet.contains(id))
            throw new DecisionTreeXmlException("Duplicate Internal Node id " + id);
        else
            nodeIdSet.add(id);

        String comment = getAttribute(eltNode, "comment", false);

        String feature = getAttribute(eltNode, "feature", true);
        tnFunc.addFeature(feature);

        String value = getAttribute(eltNode, "value", true);
        try {
            Double.parseDouble(value);
        } catch (NumberFormatException nfex) {
            String errMsg = "Node " + id + ": value not a number: " + value;
            throw new DecisionTreeXmlException(errMsg, nfex);
        }

        ArrayList<Node> childNodes = new ArrayList<Node>(5);

        NodeList nl = eltNode.getChildNodes();
        int n = nl.getLength();
        Node nd;
        for (int i = 0; i < n; i++) {
            nd = nl.item(i);
            if (nd.getNodeType() == Node.ELEMENT_NODE) {
                String tag = nd.getNodeName();
                if (tag.equals("Node") || tag.equals("Response")) {
                    childNodes.add(nd);
                }
            }
        }

        int numChildNodes = childNodes.size();
        if (numChildNodes != 2) {
            String strNode = "Node: id=" + id + " " + feature + " " + value;
            String errMsgNodes = "ERROR: A <Node> element should have exactly 2 child nodes. A child node can be <Node> or <Response>. " + strNode;
            throw new DecisionTreeXmlException(errMsgNodes);
        }

        TreeNode left = parseTreeNode((Element)childNodes.get(0), tnFunc, tr);
        TreeNode right = parseTreeNode((Element)childNodes.get(1), tnFunc, tr);

        return new InternalNode(id, comment, feature, value, left, right);
    }

    private ResponseNode parseResponse(Element eltResponse, TreenetFunction tnFunc) {
        String id = getAttribute(eltResponse, "id", true);
        if (respIdSet.contains(id))
            throw new DecisionTreeXmlException("Duplicate Response Node id " + id);
        else
            respIdSet.add(id);

        String comment = getAttribute(eltResponse, "comment", false);

        String strValue = eltResponse.getAttribute("value");
        double value;
        try {
            value = Double.parseDouble(strValue);
        } catch (NumberFormatException ne) {
            throw new DecisionTreeXmlException("Response Node " + id + " does not contain a double value. value=" + strValue);
        }

        return new ResponseNode(id, comment, value);
    }

    private void parseEarlyExits(Element eltEarlyExits, TreenetFunction tnFunc) {
        ArrayList<Element> nl = XmlUtils.getChildrenByName(eltEarlyExits, "Exit");
        if (nl != null) {
            int n = nl.size();
            for (int i = 0; i < n; i++) {
                parseExit(nl.get(i), tnFunc);
            }
        }
    }

    private void parseExit(Element eltExit, TreenetFunction tnFunc) {
        String attr = getAttribute(eltExit, "tree", true);
        int tree;
        try {
            tree = Integer.parseInt(attr);
        } catch (NumberFormatException ex) {
            String errMsg = "Invalid value for attribute tree: " + attr;
            throw new DecisionTreeXmlException(errMsg);
        }

        String strValue = getAttribute(eltExit, "value", true);
        try {
            Double.parseDouble(attr);
        } catch (NumberFormatException ex) {
            String errMsg = "Invalid value for attribute value: " + attr;
            throw new DecisionTreeXmlException(errMsg);
        }

        attr = getAttribute(eltExit, "op", true);
        Operator op;
        try {
            op = Operator.parse(attr);
        } catch (IllegalArgumentException ex) {
            String errMsg = "Invalid value for attribute op: " + attr;
            throw new DecisionTreeXmlException(errMsg);
        }

        tnFunc.addEarlyExit(new EarlyExit(tree, op, strValue));

    }

    private Epilog parseEpilog(Element eltEpilog) {
        Element eltOp = XmlUtils.getFirstChildElement(eltEpilog);
        if (eltOp.getNodeName().equals("Normalize")) {
            try {
            return parseNormalize(eltOp);
            } catch (DecisionTreeXmlException e) {
                return null;
            }
        } else if (eltOp.getNodeName().equals("Polytransform")) {
            return parsePolytransform(eltOp);
        }
        else {
            return null;
        }
    }

    private Epilog parseNormalize(Element eltNorm) {
        Epilog epilog = new Epilog();
        FuncNormalize func = new FuncNormalize();
        epilog.setFunction(func);

        String strIsInv = getBoolAttribute(eltNorm, "isInverted", false);
        if (strIsInv != null && strIsInv.equals("true")) {
            String strInvFrom = getDoubleAttribute(eltNorm, "invertedFrom", true);
            func.setInvertMethod(FuncNormalize.INV_INVERSION);
            func.setInvertedFrom(strInvFrom);
        }

        String strIsNeg = getAttribute(eltNorm, "isNegated", false);
        if (strIsNeg != null && strIsNeg.equals("true")) {
            if (func.getInvertMethod() == FuncNormalize.INV_NONE)
                func.setInvertMethod(FuncNormalize.INV_NEGATION);
            else
                throw new DecisionTreeXmlException("cannot have both isInverted and isNegated defined in element <Normalize>");
        }

        func.setMean0(getDoubleAttribute(eltNorm, "mean0", false));
        func.setMean1(getDoubleAttribute(eltNorm, "mean1", false));
        func.setMean2(getDoubleAttribute(eltNorm, "mean2", false));
        func.setMean3(getDoubleAttribute(eltNorm, "mean3", false));
        func.setSd0(getDoubleAttribute(eltNorm, "sd0", false));
        func.setSd1(getDoubleAttribute(eltNorm, "sd1", false));
        func.setSd2(getDoubleAttribute(eltNorm, "sd2", false));
        func.setSd3(getDoubleAttribute(eltNorm, "sd3", false));
        func.setA0(getDoubleAttribute(eltNorm, "a0", false));
        func.setA1(getDoubleAttribute(eltNorm, "a1", false));
        func.setA2(getDoubleAttribute(eltNorm, "a2", false));
        func.setA3(getDoubleAttribute(eltNorm, "a3", false));
        func.setB0(getDoubleAttribute(eltNorm, "b0", false));
        func.setB1(getDoubleAttribute(eltNorm, "b1", false));
        func.setB2(getDoubleAttribute(eltNorm, "b2", false));
        func.setB3(getDoubleAttribute(eltNorm, "b3", false));

        if (!func.validateParams())
            throw new DecisionTreeXmlException(errNormAttr);

        return epilog;
    }

    private Epilog parsePolytransform(Element eltOp) {
        Epilog epilog = new Epilog();
        FuncPolytransform func = new FuncPolytransform();

        func.setA0(getDoubleAttribute(eltOp, "a0", false));
        func.setA1(getDoubleAttribute(eltOp, "a1", false));
        func.setA2(getDoubleAttribute(eltOp, "a2", false));
        func.setA3(getDoubleAttribute(eltOp, "a3", false));

        if (!func.validateParams())
            throw new DecisionTreeXmlException(errPolyAttr);

        epilog.setFunction(func);
        return epilog;
    }

    /**
     * Checks if the attribute name exists.
     *
     * @param eltNorm - the element containing the attribute
     * @param attr - attribute name
     * @return true if the attribute exists; or false, otherwise.
     */
    private boolean checkAttrExist(Element eltNorm, String attr) {
        Attr attrNode = eltNorm.getAttributeNode(attr);
        if (attrNode != null)
            return true;
        else
            return false;
    }

    /**
     * Returns the value of attribute.
     *
     * @param elt
     * @param attr
     * @param reqd
     * @return If the attribute exists, the value of the attribute is returned, otherwise null is returned.
     */
    private String getAttribute(Element elt, String attr, boolean reqd) {
        Attr attrNode = elt.getAttributeNode(attr);
        String val = null;
        if (attrNode != null)
            val = elt.getAttribute(attr);

        if (reqd && (val == null || val.equals("")))
            throw new DecisionTreeXmlException(elt.getTagName() + ": missing required attribute " + attr);
        return val;
    }

    private String getBoolAttribute(Element elt, String attr, boolean reqd) {
        String strVal = getAttribute(elt, attr, reqd);

        if (strVal == null ||
                ((strVal.equals("true") || strVal.equals("false")))) {
            return strVal;
        } else {
            String errMsg = "Attribute " + attr + " in Element " + elt.getTagName() + " is not a valid boolean value: " + strVal;
            throw new DecisionTreeXmlException(errMsg);
        }
    }

    private String getIntAttribute(Element elt, String attr, boolean reqd) {
        String strVal = getAttribute(elt, attr, reqd);
        try {
            if (strVal != null)
                Integer.parseInt(strVal);
            return strVal;
        } catch (NumberFormatException ne) {
            String errMsg = "Attribute " + attr + " in Element " + elt.getTagName() + " is not a valid integer: " + strVal;
            throw new DecisionTreeXmlException(errMsg);
        }
    }

    private String getDoubleAttribute(Element elt, String attr, boolean reqd) {
        String strVal = getAttribute(elt, attr, reqd);
        try {
            if (strVal != null)
                Double.parseDouble(strVal);
            return strVal;
        } catch (NumberFormatException ne) {
            String errMsg = "Attribute " + attr + " in Element " + elt.getTagName() + " is not a valid double: " + strVal;
            throw new DecisionTreeXmlException(errMsg);
        }
    }

    private Element getFirstChildElementByName(Element parent, String childName, boolean reqd) {
        Element elt = XmlUtils.getFirstChildElementByName(parent, childName);
        if (elt == null && reqd)
            throw new DecisionTreeXmlException(elt.getTagName() +  ": missing required element " + childName);
        return elt;
    }

    private static void logErrors(String msg) {
        logger.severe(msg);
        System.out.println(msg);
    }

    private static void logErrors(Exception ex, String msg) {
        String errMsg = ex.getClass().getName() + " " + ex.getMessage() + ": " + msg;
        logger.severe(errMsg);
        System.out.println(errMsg);
    }

    public static void main(String[] args) {
        String fileName = "C:\\yst\\libMLR_framework\\mlr3135.xml";
        new MlrXmlParser().parseXmlFile(fileName);
    }

}
