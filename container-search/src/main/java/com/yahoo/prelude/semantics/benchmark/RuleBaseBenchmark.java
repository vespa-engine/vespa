// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.benchmark;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.search.Query;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleImporter;
import com.yahoo.prelude.semantics.parser.ParseException;

public class RuleBaseBenchmark {

    public void benchmark(String ruleBaseFile, String queryFile, int iterations)
            throws IOException, ParseException {

        String fsaFile = null;
        if(ruleBaseFile.endsWith(".sr")){
            fsaFile = ruleBaseFile.substring(0,ruleBaseFile.length()-3) + ".fsa";
            File fsa = new File(fsaFile);
            if(!fsa.exists()){
                fsaFile = null;
            }
        }
        RuleBase ruleBase = new RuleImporter(new SimpleLinguistics()).importFile(ruleBaseFile, fsaFile);
        ArrayList<String> queries = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(queryFile));
        String line;
        while((line=reader.readLine())!=null){
            queries.add(line);
        }
        Date start = new Date();
        for (int i=0; i<iterations; i++){
            for (Iterator<String> iter = queries.iterator(); iter.hasNext(); ){
                String queryString = iter.next();
                Query query = new Query("?query="+queryString);
                ruleBase.analyze(query,0);
            }
        }
        Date end = new Date();
        long elapsed = end.getTime() - start.getTime();
        System.out.print("BENCHMARK: rulebase=" + ruleBaseFile +
                "\n           fsa=" + fsaFile +
                "\n           queries=" + queryFile +
                "\n           iterations=" + iterations +
                "\n           elapsed=" + elapsed + "ms\n");
    }


    public static void main(String[] args) {
        if(args.length<3){
            System.out.println("USAGE: RuleBaseBenchmark ruleBaseFile queryFile iterations");
            System.exit(1);
        }

        try {
            new RuleBaseBenchmark().benchmark(args[0],args[1],Integer.parseInt(args[2]));
        }
        catch (Exception e) {
            System.out.println("ERROR: " + collectMessage(e));
            //e.printStackTrace();
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
