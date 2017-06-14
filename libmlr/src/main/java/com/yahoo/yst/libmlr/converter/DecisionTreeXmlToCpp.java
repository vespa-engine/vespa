// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashSet;

import com.yahoo.yst.libmlr.converter.entity.EarlyExit;
import com.yahoo.yst.libmlr.converter.entity.Epilog;
import com.yahoo.yst.libmlr.converter.entity.FuncNormalize;
import com.yahoo.yst.libmlr.converter.entity.FuncPolytransform;
import com.yahoo.yst.libmlr.converter.entity.Function;
import com.yahoo.yst.libmlr.converter.entity.InternalNode;
import com.yahoo.yst.libmlr.converter.entity.ResponseNode;
import com.yahoo.yst.libmlr.converter.entity.Tree;
import com.yahoo.yst.libmlr.converter.entity.TreeNode;
import com.yahoo.yst.libmlr.converter.entity.TreeNodeVisitor;
import com.yahoo.yst.libmlr.converter.entity.TreenetFunction;
import com.yahoo.yst.libmlr.converter.parser.DecisionTreeXmlException;
import com.yahoo.yst.libmlr.converter.parser.MlrXmlParser;

/**
 * This class generates C++ from an MLR Decision Tree File
 *
 * @author allenwei
 *
 */
public class DecisionTreeXmlToCpp {

    private static final String INDENT_UNIT = "    ";

    private TreenetFunction tnFunc;
    private String strCppFile;
    private PrintWriter fpCpp;

    private int nodeIdx;
    private int gIndentLevel; // global indent level

    public void setTnFunc(TreenetFunction tnFunc) {
        this.tnFunc = tnFunc;
    }

    public DecisionTreeXmlToCpp(String file) {
        strCppFile = file;
        try {
            fpCpp = new PrintWriter(
                        new BufferedWriter(
                                new FileWriter(strCppFile)));
        } catch (IOException ioex) {
            System.out.println("Cannot open " + strCppFile + " for write");
        }
    }

    /**
     * Generates C++ code.
     */
    public void genCode(String strHeaderFile) {
        genCodeHeader(strHeaderFile);
        gIndentLevel = 0;
        setNodeIndex();
        genCodeDefs();
        genCodeFunc();
    }

    private void genCodeHeader(String strHeaderFile) {
        String fmt = getFormatString(strHeaderFile);
        String fileName = "mlr" + tnFunc.getFunctionId() + ".c";
        int nTrees = tnFunc.getNumberOfTrees();
        int nLeaves = tnFunc.getTree(0).getNumInternalNodes() + 1;
        String header = MessageFormat.format(fmt, fileName, new Date(),
                                             Integer.toString(nTrees),
                                             Integer.toString(nLeaves));

        gIndentLevel = 0;
        printLn(0, header);
    }

    private void setNodeIndex() {

        // set node id for each tree
        int n = tnFunc.getNumberOfTrees();

        SetNodeIndexVisitor nodeVisitor = new SetNodeIndexVisitor();
        SetLeafIndexVisitor leafVisitor = new SetLeafIndexVisitor();
        for (int i = 0; i < n; i++) {
            nodeIdx = 0;
            Tree tree = tnFunc.getTree(i);
            traverseTree(tree.getRoot(), nodeVisitor);
            traverseTree(tree.getRoot(), leafVisitor);
        }
    }

    private void genCodeDefs() {
        printLn(0, "#define TOTAL_TREES " + tnFunc.getNumberOfTrees());
        printLn();

        // const def for internal node labels
        //genCodeTraverseTrees(0, null, new PrintNodeLabelDefVisitor(), null);

        // const def for leaf node labels
        //genCodeTraverseTrees(0, null, new PrintLeafLabelDefVisitor(), null);

        genCodeNamespaceDefs();

        // array init for internal nodes
        genCodeTraverseTrees(1, "static const TreeNode nodes[] = {",
                new PrintNodeInitVisitor(), "};");

        // array init for leaf nodes
        genCodeTraverseTrees(1, "static const double leaves[] = {",
                new PrintLeafInitVisitor(), "};");

        // array of tree size (number of internal nodes)
        genCodeTreeSizeArrayInit(1);

        genCodeEarlyExits(1);

    }

    private void genCodeNamespaceDefs() {
        printLn(0, "namespace " + tnFunc.getNameSpace() + " {");
        printLn();
        printLn(0, "enum Feature {");
        for (String f : tnFunc.getFeatureSet()) {
            printLn(1, f + ",");
        }
        printLn(1, "NUMBER_FEATURES");
        printLn(0, "}; /* enum */"); // end enum
        printLn();
        printLn(0, "} /* namespace */"); // end namespace
        printLn();
    }

    private void genCodeFunc() {

        // function definition
        printLn(0, "double");
        printLn(0, tnFunc.getFunctionName() + "(MlrScoreReq& msr) {");
        printLn();

        genFeatureArrayDecl(1);

        // call traverseAll()
        printLn(1, "msr.traverseAll(nodes, leaves, fValue, TOTAL_TREES, numNodes, meExits);");
        printLn();

        genCodeEpilog(1);

        printLn(1, "return msr.getScore();");
        printLn(0, "}"); // end function

        fpCpp.close();
    }

    private void genFeatureArrayDecl(int indentInc) {
        String ns = tnFunc.getNameSpace();

        printLn(indentInc, "double fValue[" + ns + "::NUMBER_FEATURES];");
        printLn();

        // FNTM: Distinguished values
        //printInd(1, "double FNTM = fValue[" + ns + "::FNTM] = msr.getFeature(rf::FNTM);");
        //printLn();

        HashSet<String> fSet = tnFunc.getFeatureSet();
        //fSet.remove("FNTM");

        // initialization of features
        for (String f : fSet) {
            printLn(indentInc, "fValue[" + ns + "::" + f + "] = msr.getFeature(rf::" + f + ");");
        }

        printLn();
    }

    /**
     * Prints code by iterating over all trees and visiting each tree node with
     * the TreeNodeVisitor.
     *
     * @param indentInc -
     *            indentation level of the first line
     * @param start -
     *            code printed before iterations
     * @param end -
     *            code printed after iterations
     */
    private void genCodeTraverseTrees(int indentInc, String start,
            TreeNodeVisitor visitor, String end) {

        if (start != null)
            printLn(indentInc, start);

        gIndentLevel += (indentInc + 1);
        int n = tnFunc.getNumberOfTrees();
        for (int i = 0; i < n; i++) {
            Tree t = tnFunc.getTree(i);
            printLn("// " + t.getId() + " " + t.getComment());

            traverseTree(t.getRoot(), visitor);
            printLn();
        }
        gIndentLevel -= (indentInc + 1);

        if (end != null)
            printLn(indentInc, end);

        printLn();
    }

    private void genCodeTreeSizeArrayInit(int indentInc) {
        String strDef = "static const int numNodes["
            + tnFunc.getNumberOfTrees() + "] = {";
        printLn(indentInc, strDef);

        int n = tnFunc.getNumberOfTrees();
        for (int i = 0; i < n; i++) {
            String msg = tnFunc.getTree(i).getNumInternalNodes() + ", // " + i;
            printLn(indentInc + 1, msg);
        }

        printLn(indentInc, "};");
        printLn();
    }

    private void genCodeEarlyExits(int indentInc) {
        printLn(indentInc, "static const MlrEarlyExit meExits[] = {");

        int n = tnFunc.getNumEarlyExits();
        for (int i = 0; i < n; i++) {
            EarlyExit eex = tnFunc.getEarlyExit(i);
            String strEarlyExit = "{" + eex.getTreeId() + ", "
                + "decisiontree::OP_" + eex.getOperator().getId().toUpperCase() + ", "
                + eex.getValue()
                + "},";
            printLn(indentInc + 1, strEarlyExit);
        }

        // always generate a sentinel element for terminal condition
        String sentinel =
            "{" + tnFunc.getNumberOfTrees()
            + ", decisiontree::OP_NONE, 0.0}";
        printLn(indentInc + 1, sentinel);
        printLn(indentInc, "};");
        printLn();
    }

    /**
     * Currently only generate code for normalize()
     */
    private void genCodeEpilog(int indentInc) {
        Epilog epilog = tnFunc.getEpilog();
        if (epilog == null)
            return;

        Function func = epilog.getFunction();

        if (func instanceof FuncNormalize) {
            FuncNormalize funcNorm = (FuncNormalize) func;

            if (funcNorm.getInvertMethod() != FuncNormalize.INV_NONE) {
                genCodeInversion(indentInc, funcNorm);
            }

            if (funcNorm.isGenNormalize()) {
                genCodeNormalize(indentInc, funcNorm);
            }
        } else if (func instanceof FuncPolytransform) {
            FuncPolytransform funcPolytransform = (FuncPolytransform) func;
            genCodePolytransform(indentInc, funcPolytransform);
        } else {
            throw new MlrCodeGenException("Unknown <epilogue> function: " + func.getClass().getName());
        }
    }

    private void genCodeInversion(int indentInc, FuncNormalize funcNorm) {
        if (funcNorm.getInvertMethod() == FuncNormalize.INV_INVERSION) {
            printLn(indentInc, "msr.invert(" + funcNorm.getInvertedFrom() + ");");
            printLn();
        } else if (funcNorm.getInvertMethod() == FuncNormalize.INV_NEGATION) {
            printLn(indentInc, "msr.negate();");
            printLn();
        }
    }

    private void genCodeNormalize(int indentInc, FuncNormalize funcNorm) {
        StringBuilder sb = new StringBuilder();

        printLn(indentInc, "msr.normalize(");

        sb.append(funcNorm.getMean0()).append(", ")
            .append(funcNorm.getSd0()).append(", ")
            .append(funcNorm.getA0()).append(", ")
            .append(funcNorm.getB0()).append(", ");
        printLn(indentInc + 1, sb.toString());

        sb.setLength(0);
        sb.append(funcNorm.getMean1()).append(", ")
            .append(funcNorm.getSd1()).append(", ")
            .append(funcNorm.getA1()).append(", ")
            .append(funcNorm.getB1()).append(", ");
        printLn(indentInc + 1, sb.toString());

        sb.setLength(0);
        sb.append(funcNorm.getMean2()).append(", ")
            .append(funcNorm.getSd2()).append(", ")
            .append(funcNorm.getA2()).append(", ")
            .append(funcNorm.getB2()).append(", ");
        printLn(indentInc + 1, sb.toString());

        sb.setLength(0);
        sb.append(funcNorm.getMean3()).append(", ")
            .append(funcNorm.getSd3()).append(", ")
            .append(funcNorm.getA3()).append(", ")
            .append(funcNorm.getB3());
        printLn(indentInc + 1, sb.toString());

        printLn(indentInc, ");");
        printLn();

    }

    private void genCodePolytransform(int indentInc, FuncPolytransform funcPoly) {
        StringBuilder sb = new StringBuilder();
        sb.append("msr.polytransform(");
        sb.append(funcPoly.getA0()).append(", ")
            .append(funcPoly.getA1()).append(", ")
            .append(funcPoly.getA2()).append(", ")
            .append(funcPoly.getA3()).append(");");
        printLn(indentInc, sb.toString());
        printLn();

    }

    // Utilities

    private String getFormatString(String strFmtFile) {
        try {
            BufferedReader fp = new BufferedReader(
                                    new FileReader(strFmtFile));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = fp.readLine()) != null) {
                sb.append(line).append("\n");
            }

            String fmt = sb.toString();
            fp.close();

            return fmt;

        } catch (FileNotFoundException e) {
            throw new MlrCodeGenException(strFmtFile, e);
        } catch (IOException ioe) {
            throw new MlrCodeGenException("reading file " + strFmtFile,
                    ioe);
        }
    }

    private void traverseTree(TreeNode node, TreeNodeVisitor v) {
        v.visit(node);
        if (node instanceof InternalNode) {
            InternalNode dcNode = (InternalNode) node;
            traverseTree(dcNode.getLeftNode(), v);
            traverseTree(dcNode.getRightNode(), v);
        }
    }

    private void printAppend(String str) {
        fpCpp.print(str);
    }

    private void printLn(int inc, String str) {
        int indent = gIndentLevel + inc;
        for (int i = 0; i < indent; i++) {
            fpCpp.print(INDENT_UNIT);
        }
        fpCpp.println(str);
    }

    private void printLn(String str) {
        printLn(0, str);
    }

    private void printLn() {
        fpCpp.println();
    }

    /**
     * subclasses of TreeNodeVisitor
     */

    private class SetNodeIndexVisitor implements TreeNodeVisitor {

        public void visit(TreeNode node) {
            if (node instanceof InternalNode) {
                node.setIndex(nodeIdx++);
            }
        }
    }

    private class SetLeafIndexVisitor implements TreeNodeVisitor {

        public void visit(TreeNode node) {
            if (node instanceof ResponseNode) {
                node.setIndex(nodeIdx++);
            }
        }
    }

    /*
    private class PrintNodeLabelDefVisitor implements TreeNodeVisitor {

        public void visit(TreeNode node) {
            if (node instanceof InternalNode) {
                printInd(0, "#define " + node.label + " " + node.nodeId);
            }
        }
    }

    private class PrintLeafLabelDefVisitor implements TreeNodeVisitor {

        public void visit(TreeNode node) {
            if (node instanceof LeafNode) {
                printInd(0, "#define " + node.label + " " + node.nodeId);
            }
        }
    }
    */

    private class PrintNodeInitVisitor implements TreeNodeVisitor {

        public void visit(TreeNode treeNode) {
            if (treeNode instanceof InternalNode) {
                InternalNode node = (InternalNode) treeNode;
                int leftNodeIndex = node.getLeftNode().getIndex();
                int rightNodeIndex = node.getRightNode().getIndex();

                StringBuilder sb = new StringBuilder();
                sb.append(node.getIndex() + " " + node.getId());
                sb.append(" ").append(node.getComment());

                String str = "{ " + tnFunc.getNameSpace() + "::" + node.getFeature() + ", "
                        + node.getValue() + ", " + leftNodeIndex + ", "
                        + rightNodeIndex + " }, // " + sb.toString();

                printLn(str);
            }
        }
    }

    private class PrintLeafInitVisitor implements TreeNodeVisitor {

        public void visit(TreeNode treeNode) {
            if (treeNode instanceof ResponseNode) {
                ResponseNode node = (ResponseNode) treeNode;
                StringBuilder sb = new StringBuilder();
                sb.append(node.getIndex() + " " + node.getId());
                sb.append(" ").append(node.getComment());

                String str = node.getResponse() + ", // " + sb.toString();
                printLn(str);
            }
        }
    }

    public static void main(String[] args) {
        String xmlFile = null;
        String headerFile = null;
        String cppFile = null;

        int i = 0;
        boolean hasErrors = false;
        while (i < args.length && !hasErrors) {
            String arg = args[i++];
            if (arg.equals("-i")) {
                if (i < args.length)
                    xmlFile = args[i++];
                else
                    hasErrors = true;

            } else if (arg.equals("-h")) {
                if (i < args.length)
                    headerFile = args[i++];
                else
                    hasErrors = true;

            } else if (arg.equals("-o")) {
                if (i < args.length)
                    cppFile = args[i++];
                else
                    hasErrors = true;
            }

        }

        if (xmlFile == null || headerFile == null)
            hasErrors = true;

        if (hasErrors) {
            System.out.println("USAGE: java DecisionTreeXmlToCpp -i XML_file -h header_file [-o Cpp_file]");
            return;
        }

        if (cppFile == null) {
            if (xmlFile.endsWith(".xml")) {
                int idx = xmlFile.lastIndexOf('.');
                cppFile = xmlFile.substring(0, idx+1) + "c";
            } else {
                cppFile = xmlFile + ".c";
            }
        }

        File fpCpp = new File(cppFile);
        if (fpCpp.exists()) {
            System.out.println(cppFile + " exits. Please rename and run again.");
            return;
        }

        try {
            MlrXmlParser parser = new MlrXmlParser();
            DecisionTreeXmlToCpp toCpp = new DecisionTreeXmlToCpp(cppFile);

            toCpp.setTnFunc((TreenetFunction) parser.parseXmlFile(xmlFile));
            toCpp.genCode(headerFile);

        } catch (DecisionTreeXmlException tnex) {
            tnex.printStackTrace();
        }

    }
}
