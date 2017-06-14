// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.yahoo.yst.libmlr.converter.parser.DecisionTreeXmlException;

public class TreenetFunction extends MlrFunction {

    private String ns; // namespace
    private ArrayList<Tree> treeArylst;
    private HashSet<String> featureSet;
    private HashSet<String> labelSet;
    protected ArrayList<EarlyExit> earlyExitArylst;


    public TreenetFunction() {
        treeArylst = new ArrayList<Tree>(500);
        featureSet = new HashSet<String>();
        labelSet = new HashSet<String>();
        earlyExitArylst = new ArrayList<EarlyExit>(5);
    }

    public void setFunctionName(String id) {
        funcId = id;
        functionName = "mlr" + id;
        /*
        Pattern p = Pattern.compile("[^\\d]+(\\d+)\\w*");
        Matcher m = p.matcher(functionName);
        if (!m.matches())
            throw new IllegalArgumentException("not a valid functionName");

        funcId = m.group(1);
        */
        ns = "mlr" + funcId + "ns";
    }

    public String getNameSpace() {
        return ns;
    }

    public int getNumberOfTrees() {
        return treeArylst.size();
    }

    public Tree getTree(int i) {
        return treeArylst.get(i);
    }

    public void setTree(Tree t) {
        treeArylst.add(t);
    }

    public HashSet<String> getFeatureSet() {
        return featureSet;
    }

    public HashSet<String> getLabelSet() {
        return labelSet;
    }

    public void addFeature(String f) {
        featureSet.add(f);
    }

    public void addLabel(String lbl) {
        if (labelSet.contains(lbl))
            throw new DecisionTreeXmlException("Label " + lbl + " existed.");
        labelSet.add(lbl);
    }

    public void removeLabelSet() {
        labelSet = null;
    }

    public void getAllFeatures() {
        for (String f: featureSet) {
            System.out.println(f);
        }
    }

    public void addEarlyExit(EarlyExit earx) {
        earlyExitArylst.add(earx);
    }

    public int getNumEarlyExits() {
        return earlyExitArylst.size();
    }

    public EarlyExit getEarlyExit(int i) {
        return earlyExitArylst.get(i);
    }
}
