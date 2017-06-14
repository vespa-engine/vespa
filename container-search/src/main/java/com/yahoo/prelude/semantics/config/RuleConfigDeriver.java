// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleImporter;
import com.yahoo.prelude.semantics.parser.ParseException;

/**
 * Reads the rule base files in the given directory and creates a
 * semantic-rules.cfg file containing those rule bases in the given output dir.
 *
 * @author bratseth
 */
// Note: This is not used by the config model any more and can be removed
public class RuleConfigDeriver {

    public void derive(String ruleBaseDir, String outputDir) throws IOException, ParseException {
        // Validate output dir
        File outputDirFile=new File(outputDir);
        if (!outputDirFile.exists())
            throw new IOException("Output dir " + outputDirFile.getAbsolutePath() +
                                  " does not exist");

        List<RuleBase> ruleBases = derive(ruleBaseDir);
        // Convert file to config
        exportConfig(ruleBases,outputDir);
    }

    public List<RuleBase> derive(String ruleBaseDir) throws IOException, ParseException {
        // Validate the rule bases
        boolean ignoreAutomatas=true; // Don't fail if they are not available in config
        List<RuleBase> ruleBases = new RuleImporter(ignoreAutomatas).importDir(ruleBaseDir);
        ensureZeroOrOneDefault(ruleBases);
        return ruleBases;
    }

    public List<RuleBase> derive(List<NamedReader> readers) throws IOException, ParseException {
        // Validate the rule bases
        boolean ignoreAutomatas = true; // Don't fail if they are not available in config
        List<RuleBase> ruleBases = new ArrayList<>();
        RuleImporter importer = new RuleImporter(ignoreAutomatas);
        for (NamedReader reader : readers) {
            ruleBases.add(importer.importFromReader(reader, reader.getName(), null));
        }
        ensureZeroOrOneDefault(ruleBases);
        return ruleBases;
    }

    private void ensureZeroOrOneDefault(List<RuleBase> ruleBases) throws ParseException {
        String defaultName=null;
        for (RuleBase ruleBase : ruleBases) {
            if (defaultName != null && ruleBase.isDefault())
                throw new ParseException("Both '" + defaultName + "' and '" + ruleBase.getName() +
                                         "' is marked as default, there can only be one");
            if (ruleBase.isDefault())
                defaultName = ruleBase.getName();
        }
    }

    private void exportConfig(List<RuleBase> ruleBases, String outputDir)
            throws IOException {
        BufferedWriter writer=null;
        try {
            writer=IOUtils.createWriter(outputDir + "/semantic-rules.cfg","utf-8",false);
            writer.write("rulebase[" + ruleBases.size() + "]\n");
            for (int i=0; i<ruleBases.size(); i++) {
                RuleBase ruleBase= ruleBases.get(i);
                writer.write("rulebase[" + i + "].name \"" + ruleBase.getName() + "\"\n");
                writer.write("rulebase[" + i + "].rules \"");
                writeRuleBaseAsLine(ruleBase.getSource(),writer);
                writer.write("\"\n");
            }
        }
        finally {
            IOUtils.closeWriter(writer);
        }
    }

    private void writeRuleBaseAsLine(String file, Writer writer) throws IOException {
        BufferedReader reader=null;
        try {
            reader=IOUtils.createReader(file,"utf-8");
            String line;
            while (null!=(line=reader.readLine())) {
                writer.write(line);
                writer.write("\\n");
            }
        }
        finally {
            IOUtils.closeReader(reader);
        }
    }

    public static void main(String[] args) {
        if(args.length<2){
            System.out.println("USAGE: RuleConfigDeriver ruleBaseDir outputDir");
            System.exit(1);
        }

        try {
            new RuleConfigDeriver().derive(args[0],args[1]);
        }
        catch (Exception e) {
            System.out.println("ERROR: " + collectMessage(e));
            System.exit(1);
        }
    }

    private static String collectMessage(Throwable e) {
        if (e.getCause()==null)
            return messageOrName(e);
        else
            return messageOrName(e) + ": " + collectMessage(e.getCause());
    }

    private static String messageOrName(Throwable e) {
        if (e.getMessage()!=null)
            return e.getMessage();
        else
            return e.getClass().getName();
    }

}
