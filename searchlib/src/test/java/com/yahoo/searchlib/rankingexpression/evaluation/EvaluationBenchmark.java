// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization.GBDTForestOptimizer;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Two small benchmarks of ranking expression evaluation
 *
 * @author bratseth
 */
public class EvaluationBenchmark {

    public void run() {
        try {
            //runNativeComparison(100*1000*1000);

            // benchmark with a large gbdt: Expected tree and forest speedup: 2x, 4x
            runGBDT(1000*1000, gbdt);

            // benchmark with a large gbdt using set membership tests (on integers) extensively
            // we simplify the attribute name to make it work with the map context implementation.
            // Expected tree and forest speedup: 3x, 4x
            // runGBDT(100*1000, readFile("src/test/files/ranking07.expression").replace("attribute(catid)","catid"));
        }
        catch (ParseException e) {
            throw new RuntimeException("Benchmarking failed",e);
        }
    }

    private String readFile(String file) {
        try {
            return IOUtils.readFile(new File(file));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public void runNativeComparison(int iterations) {
        oul("Running native expression...");
        MapContext arguments=new MapContext();
        arguments.put("one",1d);

        out("  warming up...");
        double nativeTotal=0;
        for (int i=0; i<iterations/5; i++) {
            arguments.put("i",(double)i);
            nativeTotal+=nativeExpression(arguments);
        }
        oul("done");

        out("  running " + iterations + " iterations...");
        long startTime=System.currentTimeMillis();
        for (int i=0; i<iterations; i++) {
            arguments.put("i",(double)i);
            nativeTotal+=nativeExpression(arguments);
        }
        long nativeTotalTime=System.currentTimeMillis()-startTime;
        oul("done");
        oul("  Total time running native:     " + nativeTotalTime + " ms (" + iterations/nativeTotalTime + " expressions/ms)");

        oul("Running ranking expression...");
        RankingExpression expression;
        try {
            expression=new RankingExpression(comparisonExpression);
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
        out("  warming up...");
        double rankingTotal=0;
        for (int i=0; i<iterations/5; i++) {
            arguments.put("i",(double)i);
            rankingTotal+=expression.evaluate(arguments).asDouble();
        }
        oul("done");

        out("  running " + iterations + " iterations...");
        startTime=System.currentTimeMillis();
        for (int i=0; i<iterations; i++) {
            arguments.put("i",(double)i);
            rankingTotal+=expression.evaluate(arguments).asDouble();
        }
        long rankingTotalTime=System.currentTimeMillis()-startTime;
        if (rankingTotal!=nativeTotal)
            throw new IllegalStateException("Expressions are not the same, native: " + nativeTotal + " rankingExpression: " + rankingTotal);
        oul("done");
        oul("  Total time running expression: " + rankingTotalTime + " ms (" + iterations/rankingTotalTime + " expressions/ms)");
        oul("Expression % of max possible speed: " + ((int)((100*nativeTotalTime)/rankingTotalTime)) + " %");
    }

    private static final String comparisonExpression="10*if(i>35,if(i>one,if(i>=670,4,8),if(i>8000,5,3)),if(i==478,90,91))";

    private final double nativeExpression(Context context) {
        double r;
        if (context.get("i").asDouble()>35) {
            if (context.get("i").asDouble()>context.get("one").asDouble()) {
                if (context.get("i").asDouble()>=670)
                    r=4;
                else
                    r=8;
            }
            else {
                if (context.get("i").asDouble()>8000)
                    r=5;
                else
                    r=3;
            }
        }
        else {
            if (context.get("i").asDouble()==478)
                r=90;
            else
                r=91;
        }
        return r*10;
    }

    private void runGBDT(int iterations, String gbdtString) throws ParseException {

        // Unoptimized...............
        double total = benchmark(new RankingExpression(gbdtString), new MapContext(), iterations, "Unoptimized");
        System.out.println("-----------------------------------------------------------------------------------------------------");

        // Tree optimized...................
        RankingExpression treeOptimized = new RankingExpression(gbdtString);
        ArrayContext treeContext = new ArrayContext(treeOptimized, true);
        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        optimizer.getOptimizer(GBDTForestOptimizer.class).setEnabled(false);
        System.out.print("Tree optimizing ... ");
        OptimizationReport treeOptimizationReport = optimizer.optimize(treeOptimized, treeContext);
        System.out.println("done");
        System.out.println(treeOptimizationReport);
        double treeTotal = benchmark(treeOptimized, treeContext, iterations, "Tree optimized");
        assertEqualish(total, treeTotal);
        System.out.println("-----------------------------------------------------------------------------------------------------");

        // Forest optimized...................
        RankingExpression forestOptimized=new RankingExpression(gbdtString);
        DoubleOnlyArrayContext forestContext = new DoubleOnlyArrayContext(forestOptimized, true);
        System.out.print("Forest optimizing ... ");
        OptimizationReport forestOptimizationReport=new ExpressionOptimizer().optimize(forestOptimized, forestContext);
        System.out.println("done");
        System.out.println(forestOptimizationReport);
        double forestTotal=benchmark(forestOptimized,forestContext,iterations,"Forest optimized");
        assertEqualish(total,forestTotal);
        System.out.println("-----------------------------------------------------------------------------------------------------");
    }

    private double benchmark(RankingExpression gbdt, Context context, int iterations, String description) {
        oul("Running '" + description + "':");
        out("   Warming up ...");
        double total=0;
        total+=benchmarkIterations(gbdt,context,iterations/5);
        oul("done");

        out("   Running " + iterations + " of '" + description + "' ...");
        long tStartTime=System.currentTimeMillis();
        total+=benchmarkIterations(gbdt,context,iterations);
        long totalTime=System.currentTimeMillis()-tStartTime;
        oul("done");
        oul("   Total time running '" + description + "': " + totalTime + " ms (" + totalTime*1000/iterations + " microseconds/expression)");
        return total;
    }

    private double benchmarkIterations(RankingExpression gbdt, Context contextPrototype, int iterations) {
        // This tries to simulate realistic use: The array context can be reused for a series of evaluations in a thread
        // but each evaluation binds a new set of values.
        double total=0;
        Context context = copyForEvaluation(contextPrototype);
        for (int i=0; i<iterations; i++) {
            context.put("LW_NEWS_SEARCHES_RATIO",(double)i);
            context.put("NEWS_USERS",(double)i/1000*1000);
            context.put("catid",100300102);
            total+=gbdt.evaluate(context).asDouble();
        }
        return total;
    }

    private Context copyForEvaluation(Context contextPrototype) {
        if (contextPrototype instanceof AbstractArrayContext) // optimized - contains name to index map
            return ((AbstractArrayContext)contextPrototype).clone();
        else if (contextPrototype instanceof MapContext) // Unoptimized - nothing to keep
            return new MapContext();
        else
            throw new RuntimeException("Unknown context type " + contextPrototype.getClass());
    }

    private void out(String s) {
        System.out.print(s);
    }

    private void oul(String s) {
        System.out.println(s);
    }

    public static void main(String[] args) {
        new EvaluationBenchmark().run();
    }

    private void assertEqualish(double a,double b) {
        if (Math.abs(a-b) >= Math.abs((a+b)/100000000) )
            throw new RuntimeException("Expected value " + a + " but optimized evaluation produced " + b);
    }

    private final String gbdt =
                     "if (LW_NEWS_SEARCHES_RATIO < 1.72971, 0.0697159, if (LW_USERS < 0.10496, if (SEARCHES < 0.0329127, 0.151257, 0.117501), if (SUGG_OVERLAP < 18.5, 0.0897622, 0.0756903))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 1.73156, if (NEWS_USERS < 0.0737993, -0.00481646, 0.00110018), if (LW_USERS < 0.0844616, 0.0488919, if (SUGG_OVERLAP < 32.5, 0.0136917, 9.85328E-4))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 1.74451, -0.00298257, if (LW_USERS < 0.116207, if (SEARCHES < 0.0329127, 0.0676105, 0.0340198), if (NUM_WORDS < 1.5, -8.55514E-5, 0.0112406))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 1.72995, if (NEWS_USERS < 0.0737993, -0.00407515, 0.00139088), if (LW_USERS < 0.0509035, 0.0439466, if (LW_USERS < 0.325818, 0.0187156, 0.00236949))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 1.72503, -0.00239817, if (LW_USERS < 0.0977572, if (ISABSTRACT_AVG < 0.04, 0.041602, 0.0157381), if (LW_USERS < 0.602112, 0.0118004, 7.92829E-4))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 1.53348, -0.00227065, if (LW_USERS < 0.0613667, 0.0345214, if (NUM_WORDS < 1.5, -9.25274E-4, if (BIDDED_SEARCHES < 0.538873, 0.0207086, 0.00549622)))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 1.50465, -0.00206609, if (LW_USERS < 0.183424, if (NUM_WORDS < 1.5, 0.00203703, if (BIDDED_SEARCHES < 0.0686975, 0.0412142, 0.0219894)), 0.00246537)) + \n" +
                     "if (NEWS_USERS < 0.0737993, -0.00298889, if (LW_USERS < 0.212577, if (NUM_WORDS < 1.5, 0.00385669, 0.0260773), if (NUM_WORDS < 1.5, -0.00141889, 0.00565858))) + \n" +
                     "if (NEWS_USERS < 0.0737993, -0.0026984, if (BIDDED_SEARCHES < 0.202548, if (NUM_WORDS < 1.5, 0.00356601, 0.026572), if (SUGG_OVERLAP < 34.5, 0.00642933, -8.83847E-4))) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 8.47575, if (NUM_WORDS < 2.5, if (NEWS_USERS < 0.0737993, -0.0031992, if (ISTITLE_AVG < 0.315, 0.0106735, 1.98748E-4)), 0.00717291), 0.0216488) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.79697, if (NEWS_CTR < 0.659695, -0.0018297, 0.0062345), if (BIDDED_SEARCHES < 0.148816, if (NUM_WORDS < 1.5, 0.00397494, 0.0282706), 0.00287526)) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.81978, if (NUM_WORDS < 2.5, -0.00183825, 0.00447334), if (SUGG_OVERLAP < 8.5, if (SEARCHES < 0.0692601, 0.0319928, 0.0121653), 0.0010403)) + \n" +
                     "if (NEWS_CTR < 0.660025, if (PREV_DAY_NEWS_CTR_RATIO < 0.502543, if (SEARCHES < 0.245402, 0.0193446, 9.09694E-4), -0.00160176), if (NEWS_MAIN_SEARCHES_RATIO < 1.64873, 0.00264489, 0.0177375)) + \n" +
                     "if (NUM_WORDS < 2.5, if (NEWS_USERS < 0.0737993, -0.00238821, if (LW_USERS < 0.0143922, 0.0188957, 8.0445E-4)), if (LW_NEWS_SEARCHES_RATIO < 1.32846, 0.00349568, 0.015966)) + \n" +
                     "if (NUM_WORDS < 2.5, if (NEWS_USERS < 0.0737993, -0.002169, if (ISTITLE_AVG < 0.625, 0.00906748, -2.5122E-4)), if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.69164, 0.0039487, 0.0174816)) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 8.66642, if (NUM_WORDS < 2.5, -8.59968E-4, if (NEWS_CTR < 0.632914, 0.00287223, 0.0148924)), if (SEARCHES < 0.0237478, 0.033539, 0.0071663)) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 1.26315, -0.00130179, if (NEWS_CTR < 0.628621, if (PREV_DAY_NEWS_CTR_RATIO < 0.525166, if (SUGG_OVERLAP < 9.5, 0.0171556, 2.36297E-4), 2.29746E-4), 0.0123793)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 1.88252, if (NEWS_USERS < 0.0737993, -0.00207461, 6.60118E-4), if (NEWS_USERS < 0.0737993, 9.39125E-4, if (SEARCHES < 0.0248661, 0.0272446, 0.00973038))) + \n" +
                     "if (NUM_WORDS < 1.5, -0.0018842, if (NEWS_USERS < 0.0737993, -5.44658E-4, if (PREV_DAY_USERS < 0.43141, if (PREV_DAY_NEWS_CTR < 0.447268, 4.25375E-4, 0.0152695), 0.00230817))) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 2.6946, -7.37738E-4, if (NEWS_CTR < 0.618656, if (PREV_DAY_NEWS_CTR_RATIO < 0.522617, if (ISTITLE_AVG < 0.21, 0.0202984, 0.00221158), 8.26792E-4), 0.0131518)) + \n" +
                     "if (NUM_WORDS < 3.5, if (NEWS_CTR < 0.660239, if (PREV_DAY_NEWS_CTR_RATIO < 0.505308, 0.00214801, -0.00113168), if (NEWS_MAIN_SEARCHES_RATIO < 0.9266, 1.28813E-4, 0.0090932)), 0.0111807) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 1.27238, -9.46325E-4, if (NEWS_USERS < 0.0737993, 2.20417E-4, if (ISTITLE_AVG < 0.435, 0.0143694, if (MIN_SCORE < 243538.0, 1.76879E-4, 0.00682761)))) + \n" +
                     "if (NUM_WORDS < 3.5, if (NUM_WORDS < 1.5, -0.00153422, if (NEWS_USERS < 0.0737993, -6.54983E-4, if (PREV_DAY_NEWS_CTR < 0.55636, -4.40154E-4, 0.00666305))), 0.00961529) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 1.88316, -6.18023E-4, if (NEWS_USERS < 0.0737993, if (NUM_WORDS < 2.5, -4.22107E-4, 0.00583448), if (SEARCHES < 0.0202227, 0.0218746, 0.0061446))) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 1.91611, if (NEWS_MAIN_SEARCHES_RATIO < 0.384315, -0.0015553, 2.57266E-4), if (NEWS_CTR < 0.659281, if (NUM_WORDS < 2.5, 2.40504E-4, 0.00572176), 0.0105389)) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 2.68704, -5.65225E-4, if (NEWS_CTR < 0.782417, if (PREV_DAY_NEWS_CTR_RATIO < 0.990517, if (NEWS_SEARCHES < 0.339382, 0.0135414, 0.00113811), 5.21526E-4), 0.0112535)) + \n" +
                     "if (BIDDED_SEARCHES < 0.00581527, 0.00560086, if (NUM_WORDS < 1.5, -0.00130462, if (NEWS_USERS < 0.0737993, -7.52446E-4, if (BIDDED_SEARCHES < 1.29452, 0.00626868, 1.75195E-4)))) + \n" +
                     "if (NUM_WORDS < 3.5, if (NUM_WORDS < 1.5, -0.00114958, if (NEWS_USERS < 0.0737993, -5.00434E-4, if (PREV_DAY_NEWS_CTR < 0.563721, -6.96671E-4, 0.00517722))), 0.00807433) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 0.382901, -0.00122923, if (NEWS_USERS < 0.0737993, -4.15058E-4, if (ISABSTRACT_AVG < 0.095, if (PREV_DAY_NEWS_CTR < 0.557042, 8.71338E-4, 0.00994663), 1.56446E-4))) + \n" +
                     "if (BIDDED_SEARCHES < 0.00581527, if (MAX_SCORE < 379805.0, 0.00362486, 0.0132902), if (NEWS_CTR < 0.913345, -3.53901E-4, if (NEWS_USERS < 2.48409, 0.00191813, 0.013908))) + \n" +
                     "if (HAS_NEWS_QC == 0.0, if (NUM_WORDS < 3.5, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.90333, -6.26897E-4, if (ISTITLE_AVG < 0.355, 0.00723851, -2.62543E-5)), 0.0058211), 0.00433763) + \n" +
                     "if (NUM_WORDS < 2.5, if (NEWS_USERS < 2.28805, -5.10768E-4, 0.00255996), if (LW_MAIN_SEARCHES_RATIO < 1.84597, 3.31329E-4, if (DAY_WEEK_AVG_RATIO < 2.655, 0.00434755, 0.0196317))) + \n" +
                     "if (HAS_NEWS_QC == 0.0, if (BIDDED_SEARCHES < 0.0119577, if (PREV_DAY_NEWS_CTR_RATIO < 0.928266, 0.0111871, 0.00198432), -3.24627E-4), if (NEWS_MAIN_SEARCHES_RATIO < 2.71304, 0.00196875, 0.00945297)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 1.82872, -4.20354E-4, if (DAY_PD_HITS_RATIO < 3.61, if (NEWS_MAIN_SEARCHES_RATIO < 12.766, 7.51735E-4, if (LW_NEWS_SEARCHES_RATIO < 6.15807, 0.0147332, -0.0135118)), 0.010677)) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 0.327632, -0.00102446, if (NEWS_USERS < 0.0737993, -3.80041E-4, if (ISABSTRACT_AVG < 0.105, if (NEWS_SEARCHES < 0.286926, 0.00928139, 0.00265099), 8.96147E-5))) + \n" +
                     "if (ALGO_CTR < 1.05585, if (HAS_NEWS_QC == 0.0, -4.34462E-4, 0.00319487), if (PREV_DAY_NEWS_CTR_RATIO < 0.541632, if (DAY_PD_HITS_RATIO < 5.75, 0.00845667, 0.0571546), 0.00162096)) + \n" +
                     "if (NUM_WORDS < 3.5, if (LW_NEWS_CTR < 0.59494, -3.29593E-4, if (NEWS_MAIN_SEARCHES_RATIO < 1.24936, 3.83584E-4, if (MAX_SCORE < 263568.0, 0.00219784, 0.0104741))), 0.00532617) + \n" +
                     "if (NUM_WORDS < 3.5, if (MAX_SCORE < 268176.0, -5.00757E-4, if (NEWS_MAIN_SEARCHES_RATIO < 0.812821, -3.72572E-4, if (NEWS_CTR < 0.898792, 0.0017999, 0.00908918))), 0.00538528) + \n" +
                     "if (ISTITLE_AVG < 0.705, if (NEWS_USERS < 0.0737993, 2.51012E-5, if (BIDDED_SEARCHES < 1.61095, if (YSM_N_ALGO_CTR_RATIO < 6.42257E-4, 0.0804317, 0.00586482), -4.26664E-4)), -4.79119E-4) + \n" +
                     "if (NUM_WORDS < 3.5, if (HAS_NEWS_QC == 0.0, -1.93562E-4, if (LW_MAIN_SEARCHES_RATIO < 1.72448, 0.00109732, 0.00738421)), if (NEWS_MAIN_SEARCHES_RATIO < 0.406201, -0.00263026, 0.00733129)) + \n" +
                     "if (BIDDED_SEARCHES < 0.0120163, 0.00278665, if (NEWS_USERS < 2.75198, -3.22197E-4, if (NEWS_MAIN_CTR_RATIO < 1.4679, 0.00148229, if (PREV_DAY_USERS < 0.117185, 0.0517723, 0.010204)))) + \n" +
                     "if (LW_NEWS_CTR < 0.597955, if (SUGG_OVERLAP < 0.5, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.79767, 6.24799E-4, 0.0051004), -5.51886E-4), if (NEWS_MAIN_SEARCHES_RATIO < 0.660064, 2.21724E-4, 0.00474931)) + \n" +
                     "if (BIDDED_SEARCHES < 0.00581527, 0.0030367, if (NEWS_USERS < 2.65484, -3.02764E-4, if (LW_MAIN_SEARCHES_RATIO < 1.39539, 6.36888E-4, if (NEWS_MAIN_CTR_RATIO < 2.18629, 0.00661051, 0.0228632)))) + \n" +
                     "if (LW_NEWS_CTR < 0.619817, if (LW_USERS < 0.0143922, 0.0012313, -4.11044E-4), if (NEWS_MAIN_SEARCHES_RATIO < 1.63866, 6.94464E-4, if (LW_MAIN_SEARCHES_RATIO < 2.79335, 0.00448877, 0.0171177))) + \n" +
                     "if (HAS_NEWS_QC == 0.0, if (ALGO_CTR < 1.1644, -2.80479E-4, 0.002092), if (NUM_WORDS < 2.5, 9.21741E-4, if (LW_MAIN_CTR_RATIO < 0.771928, 0.018042, 0.00519068))) + \n" +
                     "if (MAX_SCORE < 270938.0, -3.72001E-4, if (NEWS_MAIN_SEARCHES_RATIO < 0.382818, -8.43057E-4, if (NEWS_USERS < 0.0737993, 2.74749E-4, if (ISABSTRACT_AVG < 0.355, 0.00699732, 9.68093E-4)))) + \n" +
                     "if (NEWS_CTR < 0.187967, -0.00236148, if (LW_NEWS_CTR_RATIO < 0.501045, if (ISABSTRACT_AVG < 0.065, if (USERS < 0.79806, 0.00751647, 5.67897E-4), -1.95953E-4), -1.28664E-4)) + \n" +
                     "if (NEWS_CTR < 0.916156, if (NEWS_CTR < 0.131787, -0.00260812, -2.96076E-6), if (LW_MAIN_SEARCHES_RATIO < 1.7079, if (LW_NEWS_CTR < 0.827357, -0.00103106, 0.00752405), 0.00712343)) + \n" +
                     "if (ALGO_CTR < 1.11796, -9.56953E-5, if (LW_NEWS_CTR_RATIO < 0.965768, if (PREV_DAY_NEWS_CTR_RATIO < 0.318964, -0.0068748, if (DAY_PD_HITS_RATIO < 5.9, 0.00781228, 0.0430918)), 0.0010225)) + \n" +
                     "if (ISTITLE_AVG < 0.785, if (PREV_DAY_NEWS_CTR_RATIO < 0.937235, if (BIDDED_SEARCHES < 0.549316, 0.00782989, 5.1726E-4), if (LW_MAIN_SEARCHES_RATIO < 14.3819, -7.98452E-5, 0.00931358)), -3.44667E-4) + \n" +
                     "if (NUM_WORDS < 4.5, if (HAS_NEWS_QC == 0.0, -1.1162E-4, if (LW_NEWS_CTR < 0.625492, 0.00137801, if (NEWS_MAIN_SEARCHES_RATIO < 3.2392, 0.00481811, 0.0203582))), 0.00957663) + \n" +
                     "if (NUM_WORDS < 4.5, if (NEWS_MAIN_SEARCHES_RATIO < 12.878, -7.973E-5, if (SUGG_LW < 0.5, 0.0113112, if (PREV_DAY_NEWS_USERS < 1.63248, -0.0093633, 0.0081117))), 0.00891687) + \n" +
                     "if (NEWS_CTR < 0.260948, -0.00146919, if (PREV_DAY_NEWS_CTR_RATIO < 0.949304, if (NEWS_MAIN_SEARCHES_RATIO < 0.305788, -5.28063E-4, if (MIN_SCORE < 199600.0, 8.23835E-4, 0.00533948)), -1.59293E-4)) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 0.116451, -0.00113111, if (PREV_DAY_NEWS_CTR_RATIO < 0.999206, if (NEWS_SEARCHES < 0.30129, if (ISTITLE_AVG < 0.61, 0.00769846, 0.00162987), -2.39796E-4), -1.20795E-4)) + \n" +
                     "if (NEWS_USERS < 2.75198, -1.04934E-4, if (NEWS_CTR < 0.504788, -3.87773E-4, if (BIDDED_SEARCHES < 3.77166, if (LW_MAIN_SEARCHES_RATIO < 1.76307, 0.00639344, 0.0180493), 0.00240808))) + \n" +
                     "if (NUM_WORDS < 4.5, if (LW_NEWS_CTR < 0.789202, -2.11327E-4, if (NEWS_USERS < 0.312345, -4.52231E-4, if (SCIENCE < 0.535, 0.00367411, 0.0491292))), 0.00847389) + \n" +
                     "if (NEWS_CTR < 0.182514, -0.00177053, if (LW_NEWS_CTR_RATIO < 0.501045, if (USERS < 1.36009, if (MIN_SCORE < 187234.0, 3.6643E-4, 0.0055156), -0.0011557), -8.54842E-5)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 0.32584, if (NEWS_CTR < 1.19657, 0.00362961, if (PREV_DAY_NEWS_CTR_RATIO < 2.37995, if (NEWS_MAIN_SEARCHES_RATIO < 2.07684, 0.0176304, 0.0773353), 0.00489339)), -2.00322E-5) + \n" +
                     "if (AVG_SCORE < 354962.0, -1.53495E-4, if (NEWS_CTR < 0.596437, if (LW_SEARCHES < 0.0532569, 0.00410978, -0.00116517), if (LW_MAIN_CTR_RATIO < 0.779754, 0.0149197, 0.00348209))) + \n" +
                     "if (PREV_DAY_NEWS_USERS < 14.0861, if (BIDDED_SEARCHES < 3.24749, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.63285, -8.28682E-5, if (NEWS_SEARCHES < 0.317829, 0.00348768, -6.08623E-4)), -0.00114994), 0.00458862) + \n" +
                     "if (ISABSTRACT_AVG < 0.295, if (NEWS_USERS < 0.0737993, -1.36945E-4, if (MIN_SCORE < 233429.0, 2.59393E-5, if (NEWS_MAIN_SEARCHES_RATIO < 0.221135, -7.57098E-4, 0.00463699))), -4.62083E-4) + \n" +
                     "if (ALGO_CTR < 1.01522, -1.09825E-4, if (LW_NEWS_CTR_RATIO < 0.55285, if (LW_MAIN_SEARCHES_RATIO < 5.11061, if (NEWS_SEARCHES < 1.02345, 0.00847552, -0.00437523), -0.0112885), 6.61898E-4)) + \n" +
                     "if (NEWS_USERS < 4.05804, if (LW_NEWS_SEARCHES_RATIO < 6.67644, -1.03466E-5, if (USERS < 0.101853, -0.0245653, -0.00297792)), if (NEWS_MAIN_CTR_RATIO < 1.09325, 6.6298E-4, 0.00723109)) + \n" +
                     "if (NUM_WORDS < 4.5, if (LW_NEWS_USERS < 31.8516, -4.91517E-5, 0.00701562), if (ALGO_CLICKS < 0.012133, 0.020461, if (DAY_WEEK_AVG_RATIO < 2.93, 8.3867E-4, 0.0326788))) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 3.9286, if (NEWS_MAIN_SEARCHES_RATIO < 60.9048, 6.59836E-5, 0.0391173), if (NEWS_USERS < 0.223578, -0.0109831, if (NEWS_MAIN_SEARCHES_RATIO < 36.1125, -9.18296E-4, -0.0321067))) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 3.92945, if (NEWS_MAIN_SEARCHES_RATIO < 12.878, 3.89745E-5, if (PREV_DAY_NEWS_CTR < 0.537022, -0.00162034, 0.0079279)), if (NEWS_USERS < 0.245347, -0.0101132, -0.00126814)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 0.480833, if (NEWS_USERS < 0.0737993, 9.57273E-5, if (SUGG_LW < 12.5, if (PUB_TODAY_AVG < 0.355, 0.0161319, -0.00334364), 0.00260343)), -7.52983E-5) + \n" +
                     "if (PREV_DAY_NEWS_USERS < 38.5221, if (BIDDED_SEARCHES < 3.7973, if (PREV_DAY_NEWS_CTR_RATIO < 0.999247, if (ISABSTRACT_AVG < 0.075, 0.00272842, -3.86777E-5), -1.51219E-4), -0.00100249), 0.00670928) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 2.77887, 9.37848E-5, if (NEWS_USERS < 0.245347, if (SEARCHES < 0.013024, if (ENTERTAINMENT_QC == 0.0, 0.0110759, 0.0905384), -0.00681271), -6.6913E-4)) + \n" +
                     "if (NEWS_CTR < 0.916322, if (LW_NEWS_SEARCHES_RATIO < 5.23703, 2.81507E-5, if (SEARCHES < 0.233024, -0.0177547, -0.00220902)), if (NEWS_USERS < 2.30165, 0.00110318, 0.00810944)) + \n" +
                     "if (HAS_NEWS_QC == 0.0, -1.08882E-4, if (MAX_SCORE < 137730.0, if (ALGO_CTR < 0.489733, 0.0199541, 0.0026349), if (NEWS_USERS < 2.20454, -3.16208E-4, 0.00699663))) + \n" +
                     "if (BIDDED_SEARCHES < 0.00581527, if (LW_NEWS_USERS < 1.81124, 0.00173624, if (PREV_DAY_USERS < 1.36892, 0.0405308, -0.00100716)), if (NEWS_MAIN_SEARCHES_RATIO < 58.9771, -1.26569E-4, 0.0286363)) + \n" +
                     "if (LW_NEWS_CTR < 0.621598, -1.10247E-4, if (LW_MAIN_SEARCHES_RATIO < 0.317173, 0.0110308, if (ALGO_CTR < 1.26031, 9.13964E-4, if (ALGO_CTR < 1.27034, 0.0667268, 0.00722662)))) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 25.7554, -6.12962E-6, if (LW_NEWS_SEARCHES < 0.765878, if (DAY_WEEK_AVG_RATIO < 1.475, if (PREV_DAY_NEWS_SEARCHES < 0.285188, 0.00389095, -0.0350617), -0.0440429), -7.44561E-4)) + \n" +
                     "if (DAY_PD_HITS_RATIO < 16.25, -5.78971E-5, if (INTLNEWS < 0.235, if (BIDDED_SEARCHES < 0.401931, if (PREV_DAY_MAIN_CTR_RATIO < 0.852642, 0.00517, 0.0517763), 0.00726245), 0.00172079)) + \n" +
                     "if (DAY_PD_HITS_RATIO < 18.89, -9.58573E-5, if (NEWS_MAIN_CTR_RATIO < 4.42646, if (LW_MAIN_SEARCHES_RATIO < 1.64955, -0.00540243, if (PREV_DAY_CTR < 0.823034, 0.0147119, -0.00456252)), 0.0476969)) + \n" +
                     "if (LW_CTR < 1.01377, -9.34648E-5, if (NEWS_USERS < 0.0737993, -6.338E-5, if (MIN_SCORE < 376483.0, 0.00251265, if (LW_MAIN_SEARCHES_RATIO < 0.683623, 0.0350855, 0.00794114)))) + \n" +
                     "if (ISABSTRACT_AVG < 0.315, if (NEWS_USERS < 0.0737993, -1.37636E-4, if (LW_MAIN_SEARCHES_RATIO < 0.661526, if (SUGG_LW < 3.5, 0.0168399, 0.00323338), 9.73973E-4)), -4.12741E-4) + \n" +
                     "if (LW_CTR < 1.01683, -1.32017E-4, if (LW_NEWS_CTR_RATIO < 0.500058, if (SCIENCE < 0.55, 0.0039965, 0.0428649), if (NEWS_CTR < 0.594088, 3.24961E-6, 0.00367602))) + \n" +
                     "if (LW_NEWS_CTR < 0.856244, -1.10246E-4, if (PREV_DAY_MAIN_SEARCHES_RATIO < 10.6833, if (LW_MAIN_SEARCHES_RATIO < 0.31726, if (LW_NEWS_CTR_RATIO < 1.23633, 0.00906872, 0.0473513), 0.00134361), 0.041372)) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 6.69974, -1.86907E-5, if (NEWS_MAIN_CTR_RATIO < 1.46029, if (LW_NEWS_SEARCHES_RATIO < 6.53657, if (PREV_DAY_NEWS_SEARCHES_RATIO < 0.316051, 0.0332713, 0.00117973), -0.010984), 0.00761193)) + \n" +
                     "if (NEWS_CTR < 0.237839, if (USERS < 0.0168938, 0.0267063, if (LW_USERS < 0.0827926, if (PREV_DAY_NEWS_CTR < 1.08233, -0.0138873, 0.0330313), -8.56477E-4)), 1.37177E-4) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 7.02911, 5.45191E-5, if (USERS < 0.118739, -0.0243638, if (NEWS_MAIN_CTR_RATIO < 1.63574, if (SEARCHES < 0.478602, -0.0123115, -0.00225071), 0.0054502))) + \n" +
                     "if (BIDDED_SEARCHES < 3.7973, if (NEWS_USERS < 2.20454, 8.53898E-5, if (NEWS_MAIN_CTR_RATIO < 1.9298, 0.00163898, if (SUGG_OVERLAP < 34.0, 0.0222897, 0.00356636))), -8.81981E-4) + \n" +
                     "if (BIDDED_SEARCHES < 0.00581527, if (MIN_SCORE < 253612.0, -5.12189E-4, if (MAX_MIN_SCORE < 35925.0, 0.00252377, if (PREV_DAY_NEWS_SEARCHES_RATIO < 0.610935, 0.0432434, 0.00906418))), -1.01198E-4) + \n" +
                     "if (DAY_PD_HITS_RATIO < 24.585, if (ALGO_CTR < 3.15833, -2.12884E-5, 0.0175937), if (PREV_DAY_CTR < 0.824546, if (LW_NEWS_CTR < 0.651434, 0.011673, 0.0567104), -0.00676867)) + \n" +
                     "if (LW_CTR < 1.551, if (LW_NEWS_USERS < 3.59178, -1.29153E-4, if (SUGG_LW < 46.5, 0.00702818, 2.27956E-4)), if (NEWS_MAIN_SEARCHES_RATIO < 8.86382, 0.0028952, 0.0366156)) + \n" +
                     "if (DAY_PD_HITS_RATIO < 18.89, -5.51307E-6, if (YSM_CTR < 0.0178362, if (ALGO_CLICKS < 0.127132, 0.0471277, if (SUGG_TW < 0.975545, 0.0048341, 0.0335537)), -0.00344397)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 8.21211, -5.10935E-5, if (DAY_WEEK_AVG_RATIO < 1.205, -4.84709E-4, if (NEWS_MAIN_SEARCHES_RATIO < 2.63328, if (LW_NEWS_SEARCHES_RATIO < 1.83743, 0.0125448, -0.00162932), 0.0144536))) + \n" +
                     "if (ALGO_CTR < 1.01463, -1.17159E-4, if (PREV_DAY_NEWS_CTR_RATIO < 0.780396, if (USERS < 0.614133, if (MAX_MIN_SCORE < 54869.8, 0.00624085, 0.0337856), 7.62548E-4), 3.62126E-4)) + \n" +
                     "if (NUM_WORDS < 3.5, -1.00136E-5, if (PREV_DAY_NEWS_CTR_RATIO < 0.958905, if (PREV_DAY_USERS < 0.377834, if (YSM_N_ALGO_CTR_RATIO < 0.189731, 0.0259994, -0.0142924), 4.37294E-4), 9.62911E-4)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 92.7164, if (LW_NEWS_CTR < 0.822371, -4.99393E-5, if (PREV_DAY_MAIN_SEARCHES_RATIO < 13.0501, if (NEWS_USERS < 0.309237, -8.38369E-4, 0.00312145), 0.043612)), -0.00674822) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 2.51597, 1.01649E-4, if (SEARCHES < 0.0202227, if (PREV_DAY_MAIN_CTR_RATIO < 1.20113, 0.00953861, 0.0583575), if (USERS < 0.295073, -0.00536031, -4.99861E-4))) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 0.146655, 0.00684325, if (LW_CTR < 1.43439, -5.31424E-5, if (NEWS_MAIN_SEARCHES_RATIO < 11.7367, if (PREV_DAY_NEWS_CTR_RATIO < 0.541013, 0.0101571, 0.0013804), 0.0362471))) + \n" +
                     "if (LW_NEWS_SEARCHES < 5.77429, -9.91104E-5, if (NEWS_CTR < 1.71804, if (SUGG_OVERLAP < 32.5, if (HAS_NEWS_QC == 0.0, 0.00333027, 0.0179206), 4.42358E-4), 0.0445137)) + \n" +
                     "if (ISABSTRACT_AVG < 0.435, if (NEWS_USERS < 0.158915, -2.22842E-5, if (PREV_DAY_NEWS_USERS < 0.0737993, 0.00311367, if (USERS < 0.119577, -0.00919024, 7.29693E-4))), -3.98811E-4) + \n" +
                     "if (ALGO_CLICKS < 4.04596, if (NEWS_USERS < 0.223578, if (NEWS_SEARCHES < 0.452288, 3.21367E-5, -0.00726485), if (LOCAL_QC == 1.0, -0.00144797, 0.00132603)), -9.1988E-4) + \n" +
                     "if (NEWS_CTR < 0.25921, -8.87978E-4, if (PREV_DAY_NEWS_CTR_RATIO < 0.530395, if (USERS < 0.710459, if (MAX_MIN_SCORE < 758.5, 0.00626933, 9.79114E-4), -3.43207E-4), -7.62231E-5)) + \n" +
                     "if (SUGG_TW < 0.0623373, if (LW_NEWS_SEARCHES_RATIO < 6.68433, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.89603, 1.96789E-4, if (LW_MAIN_SEARCHES_RATIO < 0.719144, 0.013244, 0.00182593)), -0.00570262), -2.16189E-4) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 2.07246, if (NEWS_MAIN_SEARCHES_RATIO < 53.2676, 8.99313E-5, 0.0338743), if (LW_SEARCHES < 0.216881, -0.00282376, if (PREV_DAY_SEARCHES < 0.0712414, 0.0484119, -3.84987E-4))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 2.51974, 9.68801E-5, if (ALGO_CTR < 1.86978, if (LW_USERS < 0.0798854, if (NEWS_MAIN_CTR_RATIO < 0.42837, -0.0141747, -0.00244278), -4.47252E-4), 0.0201717)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 10.0121, -1.42949E-5, if (PREV_DAY_MAIN_CTR_RATIO < 1.47714, 9.66134E-4, if (BIDDED_SEARCHES < 0.0585926, if (WEEKAVG < 0.36, 0.00997522, 0.0530748), 0.00387354))) + \n" +
                     "if (SUGG_TW < 0.984769, -3.34988E-5, if (PREV_DAY_NEWS_CTR < 1.13129, 0.0013372, if (BUSINESS < 0.05, 0.00681273, if (LOCAL_QC == 0.0, 0.0221056, 0.13305)))) + \n" +
                     "if (LW_CTR < 1.63323, -1.51312E-5, if (LW_NEWS_SEARCHES_RATIO < 1.28425, 0.00114219, if (ELECTRONICS_QC == 0.0, if (PREV_DAY_MAIN_CTR_RATIO < 0.530832, 0.0312363, 0.00679683), 0.0640472))) + \n" +
                     "if (PREV_DAY_NEWS_USERS < 4.25111, -4.70532E-5, if (PREV_DAY_MAIN_CTR_RATIO < 2.58573, if (YSM_NCTR < 0.00660392, if (NEWS_MAIN_SEARCHES_RATIO < 1.27373, -1.99449E-4, 0.00625635), -5.22971E-4), 0.0405083)) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 377.799, if (LW_NEWS_SEARCHES_RATIO < 6.67644, 1.17654E-5, if (PUB_TODAY_AVG < 0.0050, -0.00565339, if (NATIONALNEWS < 0.55, 2.61588E-4, 0.0318784))), 0.0238311) + \n" +
                     "if (PREV_DAY_CTR < 1.16424, -7.76883E-5, if (LW_NEWS_SEARCHES_RATIO < 8.68994, 0.00182771, if (NEWS_SEARCHES < 7.1215, -0.013084, if (NEWS_MAIN_SEARCHES_RATIO < 3.58161, -0.00835768, 0.0377434)))) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 26.7481, 4.45294E-5, if (LW_NEWS_SEARCHES_RATIO < 1.57387, if (LW_NEWS_SEARCHES_RATIO < 1.3782, if (LW_CTR < 0.34851, 0.0177335, -0.00964832), 0.024959), -0.016879)) + \n" +
                     "if (LOCAL_QC == 1.0, if (NEWS_USERS < 0.0737993, 1.57459E-4, if (ISTITLE_AVG < 0.515, -0.00580773, if (PREV_DAY_MAIN_SEARCHES_RATIO < 4.81114, -0.00140636, 0.0204618))), 1.02083E-4) + \n" +
                     "if (HAS_NEWS_QC == 0.0, -3.53931E-5, if (ALGO_CTR < 0.5969, if (MIN_SCORE < 30200.0, if (NEWS_CTR < 0.713517, 0.0124535, 0.049838), 0.00304798), -2.6664E-4)) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 59.3594, if (NEWS_MAIN_SEARCHES_RATIO < 46.9165, if (PREV_DAY_NEWS_SEARCHES_RATIO < 48.6166, -8.91528E-6, 0.0156096), -0.0194015), if (INTLNEWS < 0.275, 0.0391563, 2.94525E-5)) + \n" +
                     "if (ALGO_CTR < 3.09161, if (NEWS_MAIN_SEARCHES_RATIO < 71.6642, -7.16141E-5, 0.0245016), if (NEWS_MAIN_SEARCHES_RATIO < 5.48496, if (ELECTRONICS_QC == 0.0, 3.80175E-4, 0.134021), 0.0467547)) + \n" +
                     "if (LOCAL_QC == 1.0, if (PREV_DAY_NEWS_CTR_RATIO < 0.55814, if (LW_USERS < 0.179284, -0.0110475, -0.00187986), if (LW_NEWS_SEARCHES_RATIO < 11.9839, -4.62166E-4, 0.0120886)), 4.16986E-5) + \n" +
                     "if (LW_NEWS_USERS < 48.703, if (LW_MAIN_SEARCHES_RATIO < 104.672, -1.11529E-5, if (PUB_TODAY_AVG < 0.645, -0.0109524, if (LW_MAIN_CTR_RATIO < 0.820426, 0.0173264, -0.00598908))), 0.00642443) + \n" +
                     "if (NEWS_USERS < 26.8033, if (USERS < 2.70898, if (NEWS_USERS < 0.212247, if (NEWS_SEARCHES < 0.312345, 1.94111E-5, -0.00494194), 9.66727E-4), -7.27397E-4), 0.00366377) + \n" +
                     "if (PREV_DAY_NEWS_CTR_RATIO < 0.948678, if (ISTITLE_AVG < 0.565, if (PREV_DAY_MAIN_CTR_RATIO < 1.53864, 0.00145357, if (YSM_N_ALGO_CTR_RATIO < 0.00279164, 0.053982, 0.0096231)), 1.01252E-4), -9.24301E-5) + \n" +
                     "if (PREV_DAY_NEWS_CTR_RATIO < 0.999206, 5.03044E-4, if (LW_MAIN_SEARCHES_RATIO < 11.8351, -2.19647E-4, if (DAY_WEEK_AVG_RATIO < 2.785, 0.00174311, if (ISABSTRACT_AVG < 0.73, 0.020265, -0.00658421)))) + \n" +
                     "if (SUGG_OVERLAP < 0.5, if (BIDDED_SEARCHES < 0.00581527, if (SUGG_LW < 8.5, 0.00316453, if (ELECTRONICS_QC == 0.0, 0.0240488, 0.285332)), 2.9583E-4), -1.0113E-4) + \n" +
                     "if (ALGO_CTR < 1.15516, -9.02219E-5, if (LW_NEWS_CTR_RATIO < 0.131516, 0.0416615, if (NEWS_CTR < 0.841155, 5.45051E-4, if (ALGO_CLICKS < 0.0703111, 0.0508979, 0.00584922)))) + \n" +
                     "if (ENTERTAINMENT < 0.305, if (ALGO_CTR < 1.53687, -1.42467E-4, if (PREV_DAY_NEWS_SEARCHES_RATIO < 2.43692, 0.00172748, if (LW_NEWS_CTR_RATIO < 1.09767, 0.0382724, 3.85821E-4))), 9.95127E-4) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 3.61514, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.904, -6.72591E-5, if (USERS < 1.06349, 0.00243637, -8.96343E-4)), if (NEWS_USERS < 0.179867, -0.00813249, -0.0012514)) + \n" +
                     "if (PREV_DAY_NEWS_USERS < 13.0067, -3.50928E-5, if (PREV_DAY_NEWS_CTR < 0.714421, 7.97227E-4, if (USERS < 3.56693, if (YSM_NCTR < 0.036612, 0.0297616, -0.00692722), 0.00476212))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 2.51803, 5.8313E-5, if (PREV_DAY_MAIN_CTR_RATIO < 2.34354, -0.00134957, if (LW_USERS < 0.0410895, if (AVG_SCORE < 284173.0, 0.046743, 0.00519612), 2.52E-4))) + \n" +
                     "if (YSM_CTR < 0.106731, -1.71864E-4, if (NEWS_MAIN_SEARCHES_RATIO < 9.26668, 5.48603E-4, if (USERS < 0.0145216, if (MAX_SCORE < 273414.0, 0.0139875, -0.0068697), -0.00914662))) + \n" +
                     "if (LW_CTR < 2.10467, if (NEWS_USERS < 0.223578, if (NEWS_SEARCHES < 0.452288, -4.92949E-5, -0.00633483), 4.52239E-4), if (MAX_MIN_SCORE < 36225.0, 0.00340485, 0.0295635)) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 27.2801, -3.29416E-5, if (LW_NEWS_USERS < 0.516988, -0.0205183, if (AVG_RANK < 9.5, -0.00354209, if (POLITICS_QC == 0.0, 0.0108605, 0.0656188)))) + \n" +
                     "if (LW_NEWS_CTR_RATIO < 0.130813, if (LW_USERS < 0.0675101, -0.0246242, -0.00263751), if (LW_NEWS_CTR_RATIO < 0.132702, 0.0418786, if (LW_MAIN_SEARCHES_RATIO < 0.4981, 0.0014675, -1.14374E-5))) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 110.393, if (PREV_DAY_MAIN_SEARCHES_RATIO < 32.725, -1.42702E-6, if (NEWS_MAIN_SEARCHES_RATIO < 3.54764, if (DAY_PD_HITS_RATIO < 5.165, -0.00858847, 0.0288169), 0.0673668)), -0.00630045) + \n" +
                     "if (SUGG_TW < 0.0905167, if (LW_NEWS_SEARCHES_RATIO < 5.38735, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.82076, 1.83185E-4, if (PREV_DAY_USERS < 0.729889, 0.00456522, -6.55502E-4)), -0.00337268), -1.85203E-4) + \n" +
                     "if (SUGG_TW < 0.985223, -7.3888E-5, if (PREV_DAY_NEWS_SEARCHES_RATIO < 0.131265, 0.0410781, if (NEWS_USERS < 0.688593, 6.1809E-4, if (NEWS_USERS < 0.999268, 0.0215243, 0.00200864)))) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 0.164771, if (NEWS_CTR < 0.581094, 0.001345, if (NEWS_MAIN_SEARCHES_RATIO < 4.47209, 0.00479447, 0.0485025)), if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.759041, 6.85213E-4, -1.09858E-4)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 0.480853, if (NEWS_USERS < 0.0737993, -2.87273E-4, if (SUGG_TW < 0.0811122, if (PREV_DAY_NEWS_SEARCHES_RATIO < 3.02247, 0.00952516, 0.0353053), 0.00133144)), 4.44055E-6) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 10.0035, 1.25006E-5, if (NEWS_CTR < 0.530131, if (SEARCHES < 0.261805, if (ENTERTAINMENT_QC == 0.0, -0.00352081, 0.040869), -0.0108829), 0.00398639)) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.21358, -1.96787E-4, if (ALGO_CTR < 3.09691, if (NEWS_SEARCHES < 0.542027, 8.12659E-4, if (NEWS_USERS < 0.193619, -0.00715108, -2.37342E-4)), 0.0334561)) + \n" +
                     "if (LW_NEWS_CTR < 0.598979, -7.53961E-5, if (PREV_DAY_CTR < 1.09221, if (SCIENCE < 0.55, 4.60354E-4, if (PREV_DAY_MAIN_SEARCHES_RATIO < 1.34224, 0.00248627, 0.0781891)), 0.00584066)) + \n" +
                     "if (PREV_DAY_NEWS_USERS < 27.8368, if (BIDDED_SEARCHES < 5.85314, 6.36817E-5, -9.02941E-4), if (PREV_DAY_NEWS_CTR < 0.773569, 0.00156477, if (SEARCHES < 4.08125, 0.0385004, 0.00774733))) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 109.125, if (DAY_PD_HITS_RATIO < 18.75, -2.83223E-5, if (YSM_CTR < 0.0174757, if (PREV_DAY_USERS < 0.117185, 0.0405892, 0.0056735), -0.00114963)), -0.00632407) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 67.6563, if (PREV_DAY_MAIN_SEARCHES_RATIO < 32.127, 3.92781E-5, if (NEWS_CTR < 0.920256, -0.00180273, if (AVG_RANK < 8.9, 0.0401539, -0.0106621))), 0.0199772) + \n" +
                     "if (YSM_CTR < 0.0299671, if (PREV_DAY_NEWS_USERS < 1.57751, if (NEWS_MAIN_SEARCHES_RATIO < 9.09345, -3.3303E-4, if (PREV_DAY_HITS < 0.5, -0.0132526, 0.00213574)), 0.00135158), 2.86346E-4) + \n" +
                     "if (HAS_NEWS_QC == 0.0, -4.23535E-5, if (NUM_WORDS < 2.5, 1.89805E-4, if (MAX_MIN_SCORE < 34177.2, 0.00313415, if (MAX_MIN_SCORE < 38154.2, 0.0393482, 0.00649343)))) + \n" +
                     "if (NUM_WORDS < 4.5, -1.5123E-5, if (NEWS_MAIN_CTR_RATIO < 0.333385, 0.0256599, if (MIN_SCORE < 261595.0, if (LOCAL_QC == 0.0, 0.0096315, 0.0775677), -8.03435E-4))) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 4.7637, 4.65171E-5, if (SEARCHES < 0.273091, if (PREV_DAY_NEWS_SEARCHES_RATIO < 11.1511, if (PREV_DAY_NEWS_CTR_RATIO < 1.59426, -0.0164486, -6.06353E-4), 0.0148189), -7.27497E-4)) + \n" +
                     "if (NEWS_USERS < 1.78862, -9.34176E-5, if (NEWS_CTR < 0.503725, -3.05424E-4, if (SUGG_LW < 92.5, if (ALGO_CTR < 0.866144, 0.00709828, 2.29334E-4), -2.83122E-4))) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 31.9116, 1.62331E-5, if (SEARCHES < 0.437086, 0.05257, if (NEWS_MAIN_CTR_RATIO < 1.327, -0.00681457, if (PUB_TODAY_AVG < 0.365, -0.00897547, 0.0268691)))) + \n" +
                     "if (DAY_PD_HITS_RATIO < 79.5, if (BUSINESS < 0.195, 1.77773E-4, if (LW_MAIN_SEARCHES_RATIO < 59.4737, if (LW_MAIN_SEARCHES_RATIO < 50.3613, -3.2627E-4, 0.0335433), -0.0156041)), -0.0188673) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 20.9996, 2.5689E-5, if (PREV_DAY_MAIN_CTR_RATIO < 0.692676, if (DAY_WEEK_AVG_RATIO < 2.95, -0.00554275, 0.0235987), if (LW_NEWS_SEARCHES_RATIO < 1.70492, 0.00485286, -0.0165676))) + \n" +
                     "if (PREV_DAY_NEWS_CTR < 0.239879, if (NEWS_MAIN_SEARCHES_RATIO < 7.73296, -5.33314E-4, -0.00970318), if (NEWS_USERS < 0.223578, if (NEWS_SEARCHES < 0.312345, 3.91472E-5, -0.00407912), 6.05168E-4)) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 2.51675, 1.51048E-5, if (LW_USERS < 0.228893, if (PREV_DAY_MAIN_CTR_RATIO < 3.25636, -0.00414565, if (PREV_DAY_USERS < 0.085979, -0.00665102, 0.0314653)), -1.93031E-4)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 11.633, -3.54622E-5, if (NEWS_CTR < 0.341241, if (SEARCHES < 0.263899, if (DUDE < 0.121324, 0.0225604, -0.023524), -0.0147208), 0.0032981)) + \n" +
                     "if (DAY_WEEK_AVG_RATIO < 14.225, if (YSM_CTR < 0.0816637, -2.14327E-4, if (LW_USERS < 3.05964, if (NEWS_USERS < 0.0737993, 2.02599E-5, 0.00173562), -7.80059E-4)), 0.0180608) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.743695, if (PREV_DAY_NEWS_CTR < 0.855411, 3.7646E-4, if (PREV_DAY_CTR < 1.27851, if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.633963, 0.0103505, 0.0012006), -0.0123434)), -4.6606E-5) + \n" +
                     "if (DAY_WEEK_AVG_RATIO < 14.225, if (DAY_WEEK_AVG_RATIO < 6.985, -1.74518E-7, if (NEWS_USERS < 0.0737993, 0.00502863, -0.00831447)), if (LW_MAIN_SEARCHES_RATIO < 3.92095, -0.00421267, 0.0344091)) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.413706, if (PREV_DAY_NEWS_CTR < 0.874719, if (LW_MAIN_SEARCHES_RATIO < 11.2056, 0.00144004, -0.014018), if (ELECTRONICS_QC == 0.0, 0.0123258, 0.119451)), -3.38696E-5) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 1.77722, 4.48781E-5, if (PREV_DAY_NEWS_USERS < 0.0737993, if (SUGG_TW < 0.054069, 0.00321739, -5.83152E-4), if (SEARCHES < 0.120921, -0.0131926, -0.0011303))) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 6.83205, 1.51597E-5, if (LW_CTR < 0.831409, -7.90698E-4, if (USERS < 0.249336, -0.0239581, if (PREV_DAY_MAIN_CTR_RATIO < 0.604761, 0.0179905, -0.00651038)))) + \n" +
                     "if (SUGG_TW < 0.967398, -6.92965E-6, if (PREV_DAY_NEWS_SEARCHES_RATIO < 0.135403, 0.0310525, if (DUDE < 0.567766, if (SPORTS < 0.73, 0.00175153, 0.00962597), -0.00111687))) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 8.3318, 4.08033E-5, if (NEWS_CTR < 0.445404, if (USERS < 0.179365, if (TOPSTORY < 0.155, -0.00102, 0.0450773), -0.0100005), 0.00289212)) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.18832, -1.47618E-4, if (ISABSTRACT_AVG < 0.03, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.21666, if (LW_MAIN_SEARCHES_RATIO < 0.747696, 0.024334, 0.00586202), 8.905E-4), -1.50915E-4)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 108.679, -8.69379E-6, if (PREV_DAY_CTR < 1.00967, if (USERS < 1.24806, -0.0260056, if (NATIONALNEWS < 0.225, -0.00279946, 0.0163558)), -0.0151386)) + \n" +
                     "if (LW_CTR < 0.968595, -9.48294E-5, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.904, 2.43976E-4, if (ISTITLE_AVG < 0.37, if (WEEKAVG < 3.5, 0.00702639, 0.0745663), 8.32579E-4))) + \n" +
                     "if (POLITICS < 0.145, if (PREV_DAY_NEWS_CTR_RATIO < 0.999206, 4.19546E-4, -1.41595E-4), if (LW_NEWS_SEARCHES_RATIO < 2.08879, -0.00110749, if (PREV_DAY_SEARCHES < 0.108517, -0.0335177, -0.00494023))) + \n" +
                     "if (ENTERTAINMENT < 0.315, -5.15746E-5, if (NEWS_MAIN_SEARCHES_RATIO < 8.67009, 6.82081E-4, if (SUGG_TW < 0.0215705, if (NEWS_MAIN_SEARCHES_RATIO < 10.1884, 0.0604503, 0.0100963), -0.00450777))) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 66.4948, if (NUM_WORDS < 4.5, -1.49344E-5, if (DAY_WEEK_AVG_RATIO < 0.885, -0.00241279, if (NEWS_MAIN_SEARCHES_RATIO < 0.440079, -0.00883573, 0.0129644))), 0.0201648) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 168.382, if (NEWS_USERS < 1.4626, -9.7013E-5, 6.89976E-4), if (NEWS_MAIN_CTR_RATIO < 0.919145, -0.0160583, if (PREV_DAY_NEWS_SEARCHES_RATIO < 42.1812, -0.00586574, 0.0127268))) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 69.7876, if (LW_CTR < 2.10467, -2.15592E-5, if (PREV_DAY_NEWS_CTR < 0.64905, if (SUGG_LW < 1.5, 0.011141, -0.00207262), 0.0231231)), -0.01887) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 28.9579, -1.03443E-5, if (NATIONALNEWS < 0.315, -0.00440798, if (ISTITLE_AVG < 0.685, if (SEARCHES < 0.599257, 0.0596869, 0.0120629), -0.0170673))) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 25.791, 1.18225E-5, if (DAY_LW_DAY_HITS_RATIO < 1.96, if (POLITICS_QC == 0.0, if (AVG_SCORE < 377554.0, -0.00736807, 0.0228459), 0.0215737), -0.0192701)) + \n" +
                     "if (BIDDED_SEARCHES < 0.00581527, if (MAX_SCORE < 304693.0, -1.37144E-4, if (WEEKAVG < 0.325, 9.10176E-4, if (NEWS_USERS < 0.737974, 0.0207108, -0.0108051))), -4.27638E-5) + \n" +
                     "if (NATIONALNEWS < 0.215, -4.53121E-5, if (LW_NEWS_CTR < 1.22057, 6.18227E-4, if (NEWS_MAIN_SEARCHES_RATIO < 4.57054, if (MIN_SCORE < 241439.0, -0.00108969, 0.0178961), 0.0489683))) + \n" +
                     "if (LOCAL_QC == 1.0, if (LW_NEWS_CTR_RATIO < 0.592627, if (LW_SEARCHES < 0.101433, -0.0152231, if (PREV_DAY_USERS < 0.0833142, 0.0217818, -0.00211607)), -3.0503E-4), 7.1333E-5) + \n" +
                     "if (PREV_DAY_CTR < 1.27104, -1.52119E-5, if (DAY_PD_HITS_RATIO < 4.25, if (LW_NEWS_CTR_RATIO < 0.659092, if (PREV_DAY_NEWS_CTR_RATIO < 0.316981, -0.00815248, 0.00978334), 3.99397E-5), 0.0164301)) + \n" +
                     "if (YSM_CTR < 0.0209264, if (PREV_DAY_NEWS_USERS < 1.61612, if (NEWS_MAIN_SEARCHES_RATIO < 2.83652, -2.92569E-4, if (USERS < 0.0435647, -0.0269406, -0.00312742)), 0.00154452), 2.03401E-4) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 29.1019, -4.51494E-5, if (NATIONALNEWS < 0.58, if (NEWS_USERS < 0.0737993, 0.00750159, -0.00562872), if (YSM_NCTR < 0.0734117, -0.00843834, 0.0454542))) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES < 127.689, if (NEWS_MAIN_SEARCHES_RATIO < 20.0978, -5.5152E-5, if (ALGO_CTR < 1.31726, if (SPORTS < 0.55, -0.00726806, 0.0277824), 0.0380699)), 0.00603891) + \n" +
                     "if (NEWS_MAIN_CTR_RATIO < 0.118028, if (MIN_SCORE < 208142.0, 3.24283E-4, if (PREV_DAY_USERS < 0.364978, if (NEWS_SEARCHES < 0.405894, -0.00227332, -0.0219515), -0.00291436)), 7.25864E-5) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 59.3594, if (DAY_PD_HITS_RATIO < 20.625, 2.84167E-5, if (PREV_DAY_CTR < 0.822381, 0.00954114, -0.00760958)), if (POLITICS_QC == 0.0, 0.00278641, 0.063001)) + \n" +
                     "if (MIN_RANK < 5.0, if (ALGO_CTR < 1.02929, 1.47687E-4, if (LW_NEWS_CTR_RATIO < 0.131516, 0.0453262, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.88827, 7.66045E-4, 0.00609536))), -2.16222E-4) + \n" +
                     "if (MIN_SCORE < 730226.0, if (MAX_SCORE < 633968.0, 1.63517E-5, if (ISTITLE_AVG < 0.27, if (SUPERDUPER_AVG < 0.11, 0.00752064, 0.0466919), 3.46353E-4)), -0.0132458) + \n" +
                     "if (HAS_NEWS_QC == 0.0, -4.23514E-5, if (ALGO_CLICKS < 0.0117185, if (SUGG_OVERLAP < 0.5, if (MIN_RANK < 3.0, 0.0232483, 0.00659311), -0.00265598), 4.52324E-4)) + \n" +
                     "if (LW_SEARCHES < 0.189865, if (LW_NEWS_SEARCHES_RATIO < 2.08206, if (NEWS_MAIN_SEARCHES_RATIO < 38.5995, -1.13521E-4, 0.04933), if (LW_NEWS_CTR < 0.873417, -0.00342462, 0.0176586)), 1.50845E-4) + \n" +
                     "if (ALGO_CTR < 0.298421, -5.53583E-4, if (LW_MAIN_CTR_RATIO < 0.511342, if (NEWS_MAIN_SEARCHES_RATIO < 8.48751, if (NUM_WORDS < 2.5, -1.33754E-5, 0.00448769), 0.00836265), -6.6781E-6)) + \n" +
                     "if (BIDDED_SEARCHES < 2.76167, if (LW_NEWS_CTR_RATIO < 0.932077, if (LW_MAIN_SEARCHES_RATIO < 5.40525, if (SUGG_OVERLAP < 16.5, 0.00258433, 2.00886E-4), -0.00529792), -8.74994E-5), -5.14235E-4) + \n" +
                     "if (NEWS_CTR < 4.12971, if (NEWS_MAIN_SEARCHES_RATIO < 8.67009, 1.65575E-5, if (ALGO_CTR < 0.691525, if (PREV_DAY_NEWS_CTR_RATIO < 0.0958437, 0.0404329, 1.86524E-4), -0.00627032)), 0.0202226) + \n" +
                     "if (DAY_PD_HITS_RATIO < 79.5, if (DAY_PD_HITS_RATIO < 6.655, -3.12406E-5, if (USERS < 0.0351556, if (DAY_WEEK_AVG_RATIO < 3.82, 0.045008, 0.00842323), 0.00104995)), -0.0188449) + \n" +
                     "if (NEWS_CTR < 4.12578, if (PREV_DAY_NEWS_USERS < 14.0861, -2.21302E-5, if (LW_USERS < 0.956063, if (NEWS_USERS < 14.236, 0.0231446, 1.33354E-4), 0.00122231)), 0.0216971) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 11.8368, -5.34645E-5, if (TOPSTORY < 0.39, 9.98011E-4, if (DUDE < 0.0709353, 0.00678128, if (DUDE < 1.97964, 0.0552834, -0.00176926)))) + \n" +
                     "if (AVG_RANK < 8.325, 1.49825E-4, if (LW_NEWS_SEARCHES_RATIO < 1.73427, -6.98057E-5, if (LW_MAIN_SEARCHES_RATIO < 0.662255, if (ALGO_CTR < 1.04359, 0.001813, 0.0309613), -0.00160574))) + \n" +
                     "if (NEWS_USERS < 1.4626, if (NEWS_SEARCHES < 1.12712, 1.58784E-5, -0.00187785), if (ALGO_CTR < 1.42277, 7.02003E-4, if (ALGO_CLICKS < 3.32631, 0.048031, -0.003279))) + \n" +
                     "if (POLITICS < 0.24, if (NEWS_MAIN_SEARCHES_RATIO < 35.8437, 5.18183E-5, if (POLITICS_QC == 0.0, -0.0123086, 0.0199861)), if (NEWS_USERS < 0.0737993, -6.26382E-4, -0.00549246)) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 71.6642, if (NEWS_MAIN_SEARCHES_RATIO < 15.8372, 6.81273E-7, if (AVG_SCORE < 363153.0, -0.00465733, if (ALGO_CTR < 0.695395, 0.0265484, -0.00829536))), 0.025954) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 11.8213, -4.37253E-6, if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.405918, -0.0124363, if (NEWS_MAIN_SEARCHES_RATIO < 1.5753, if (LW_NEWS_SEARCHES_RATIO < 1.76106, 0.00532177, -0.00463922), 0.00671844))) + \n" +
                     "if (SUGG_TW < 0.999491, -2.8076E-5, if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.216777, -0.0226515, if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.268164, 0.0268326, if (LW_NEWS_SEARCHES < 3.78249, 0.00120701, 0.0234219)))) + \n" +
                     "if (LIFESTYLE < 0.26, -7.02645E-5, if (LW_NEWS_SEARCHES_RATIO < 1.51723, 2.96639E-4, if (USERS < 0.723483, if (LW_MAIN_SEARCHES_RATIO < 1.39783, 0.016844, -0.00205856), 4.40619E-4))) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 65.0244, if (BIDDED_SEARCHES < 7.80003, if (NEWS_USERS < 1.78862, 3.10706E-5, if (NEWS_CTR < 1.16946, 0.00108557, 0.00916809)), -7.32103E-4), -0.0167254) + \n" +
                     "if (MIN_SCORE < 706927.0, if (NEWS_MAIN_SEARCHES_RATIO < 34.6252, 2.64245E-7, if (PREV_DAY_USERS < 0.0768826, -0.018824, if (MAX_SCORE < 266368.0, -0.00725209, 0.0196337))), -0.0123073) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 6.50524, 1.03884E-5, if (PREV_DAY_MAIN_CTR_RATIO < 3.78226, if (SUGG_LW < 20.5, -0.00464095, if (LW_MAIN_SEARCHES_RATIO < 1.80191, -0.00156348, 0.00548999)), 0.0381174)) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.759041, if (PREV_DAY_NEWS_CTR < 0.863395, 2.90623E-4, if (PREV_DAY_NEWS_CTR < 0.888089, if (ELECTRONICS_QC == 0.0, 0.0295237, 0.234098), 0.00434771)), -1.29379E-4) + \n" +
                     "if (NUM_WORDS < 4.5, -1.45879E-5, if (LW_MAIN_SEARCHES_RATIO < 0.500824, 0.0301862, if (PREV_DAY_CTR < 0.774918, -0.00862254, if (NEWS_MAIN_CTR_RATIO < 0.379925, 0.0202843, 0.00289057)))) + \n" +
                     "if (NEWS_USERS < 0.223578, if (NEWS_SEARCHES < 0.368987, -2.11848E-5, if (SUGG_OVERLAP < 27.5, if (SUGG_LW < 0.5, -1.33621E-4, -0.0115821), -9.66948E-4)), 4.18664E-4) + \n" +
                     "if (PREV_DAY_NEWS_CTR < 2.62668, if (NEWS_MAIN_SEARCHES_RATIO < 59.3594, 2.95226E-5, if (POLITICS_QC == 0.0, 0.00292095, 0.0790473)), if (NEWS_MAIN_SEARCHES_RATIO < 1.59073, -0.00436975, -0.0244164)) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 7.02911, 6.98898E-5, if (USERS < 0.407094, if (PREV_DAY_CTR < 0.374015, if (SUGG_OVERLAP < 2.5, 0.0472886, -0.00535839), -0.0102684), -3.2528E-4)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 29.7446, 3.85782E-5, if (LW_NEWS_SEARCHES_RATIO < 1.21834, 0.0126136, if (NEWS_SEARCHES < 0.476744, -0.0168822, if (NEWS_SEARCHES < 0.555398, 0.020656, -0.00228292)))) + \n" +
                     "if (NEWS_USERS < 0.223578, -1.43802E-4, if (PREV_DAY_NEWS_USERS < 0.150071, if (BIDDED_SEARCHES < 0.867205, if (YSM_CTR < 0.297271, 0.00337525, 0.0192626), 3.30042E-4), -5.02865E-5)) + \n" +
                     "if (DAY_PD_HITS_RATIO < 24.585, -3.60812E-5, if (LW_NEWS_CTR < 0.657508, if (NEWS_SEARCHES < 0.504412, if (PREV_DAY_MAIN_CTR_RATIO < 1.04252, -0.00553346, 0.0408869), -0.0021064), 0.0343193)) + \n" +
                     "if (LW_NEWS_USERS < 53.7995, if (DAY_LW_DAY_HITS_RATIO < 32.5, -3.9933E-5, 0.0108883), if (LW_CTR < 0.355044, if (PREV_DAY_HITS < 50.0, 0.00394348, 0.0296827), 0.0024317)) + \n" +
                     "if (MAX_SCORE < 533059.0, if (NUM_WORDS < 4.5, 5.09566E-6, if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.45664, if (USERS < 0.0408067, 0.0119362, -0.00850073), 0.0335548)), -0.00280973) + \n" +
                     "if (ENTERTAINMENT < 0.385, -7.00557E-5, if (LW_NEWS_CTR_RATIO < 0.0964147, 0.0237102, if (DAY_LW_DAY_HITS_RATIO < 0.32, if (PREV_DAY_NEWS_CTR_RATIO < 0.357356, 0.0317688, 0.00391061), 4.78597E-4))) + \n" +
                     "if (YSM_CTR < 0.0466685, if (LW_NEWS_SEARCHES_RATIO < 1.77997, -3.31782E-5, if (USERS < 0.0159575, -0.0241302, if (LW_SEARCHES < 0.10844, -0.00372346, -5.03317E-4))), 2.60865E-4) + \n" +
                     "if (LW_NEWS_CTR_RATIO < 0.983428, if (PREV_DAY_MAIN_CTR_RATIO < 1.22783, 3.17497E-5, if (BIDDED_SEARCHES < 0.0585926, if (ALGO_CTR < 0.685543, 0.0286017, 0.00458196), 0.00141704)), -1.42453E-4) + \n" +
                     "if (DAY_WEEK_AVG_RATIO < 14.465, if (LW_NEWS_SEARCHES_RATIO < 2.48779, 5.43157E-5, if (LW_USERS < 0.0728421, if (LW_SEARCHES < 0.0462011, -0.00122403, -0.0108271), -1.06335E-4)), 0.0132433) + \n" +
                     "if (POLITICS < 0.24, if (BIDDED_SEARCHES < 7.93667, if (NEWS_MAIN_CTR_RATIO < 0.0682826, -0.003825, 8.27348E-5), -8.14933E-4), if (NEWS_MAIN_SEARCHES_RATIO < 13.1906, -0.00193925, -0.0207467)) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 31.9116, -2.99465E-5, if (SEARCHES < 0.43648, 0.044537, if (LW_MAIN_CTR_RATIO < 1.03393, 0.00821746, if (PREV_DAY_NEWS_CTR_RATIO < 1.72904, 9.38031E-5, -0.0300209)))) + \n" +
                     "if (ALGO_CLICKS < 3.95494, if (LW_NEWS_USERS < 3.59178, 9.55661E-5, if (PREV_DAY_MAIN_SEARCHES_RATIO < 1.45041, if (SUGG_OVERLAP < 19.5, 0.00838423, 2.60441E-4), 0.0134882)), -5.72155E-4) + \n" +
                     "if (LW_MAIN_CTR_RATIO < 0.44235, -8.26482E-4, if (LW_MAIN_CTR_RATIO < 0.572619, if (HAS_NEWS_QC == 0.0, 6.82192E-4, if (MIN_RANK < 3.0, 0.0197753, 0.00309895)), -2.28104E-5)) + \n" +
                     "if (PREV_DAY_NEWS_SEARCHES_RATIO < 1.82043, -4.7561E-5, if (MAX_MIN_SCORE < 99.25, if (PREV_DAY_MAIN_CTR_RATIO < 1.23087, 0.00100234, if (LW_NEWS_CTR < 1.55817, 0.00609794, 0.0443022)), -2.12393E-4)) + \n" +
                     "if (PREV_DAY_CTR < 1.25038, -5.38648E-6, if (DAY_WEEK_AVG_RATIO < 3.56, 9.65443E-4, if (ISABSTRACT_AVG < 0.17, if (INTLNEWS < 0.185, 0.0537142, 0.00762876), -0.00561531))) + \n" +
                     "if (NEWS_USERS < 2.76691, -1.93104E-5, if (DUDE < 1.53391, if (PREV_DAY_CTR < 0.546773, if (PREV_DAY_MAIN_CTR_RATIO < 0.500807, 0.0447824, 0.00516532), 0.00151797), -8.85898E-4)) + \n" +
                     "if (LW_NEWS_CTR < 1.82543, if (NUM_WORDS < 4.5, -9.66944E-6, if (ISTITLE_AVG < 0.225, if (NEWS_MAIN_CTR_RATIO < 0.784694, 0.00125644, 0.0174436), -0.00293329)), -0.00426485) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 32.8094, -6.29023E-5, if (MAX_MIN_SCORE < 29576.2, if (LW_MAIN_SEARCHES_RATIO < 10.4119, 0.0302981, -0.00444216), if (ALGO_CLICKS < 0.867944, 0.0333789, 0.00409533))) + \n" +
                     "if (LW_CTR < 1.02078, -3.94933E-5, if (LW_NEWS_CTR_RATIO < 0.144121, if (NEWS_CTR < 0.1349, -6.3538E-4, 0.0621699), if (NEWS_USERS < 0.158915, -3.31917E-5, 0.00191984))) + \n" +
                     "if (LW_USERS < 0.154237, if (SUGG_OVERLAP < 0.5, if (PREV_DAY_NEWS_CTR < 1.792, if (PREV_DAY_NEWS_CTR_RATIO < 0.922571, 0.00370056, -1.34817E-5), -0.0231586), -7.99336E-4), 1.05768E-4) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 38.9947, if (LW_MAIN_SEARCHES_RATIO < 0.855833, 4.19968E-4, -1.20742E-4), if (POLITICS_QC == 0.0, if (MAX_MIN_SCORE < 18453.8, 0.0234502, -0.00779192), 0.0350307)) + \n" +
                     "if (INTLNEWS < 0.575, 4.64842E-5, if (NEWS_CTR < 0.642032, -1.79661E-4, if (LW_USERS < 0.254706, if (PREV_DAY_MAIN_SEARCHES_RATIO < 13.2143, -0.0124018, 0.0123916), -0.00150583))) + \n" +
                     "if (LOCAL_QC == 1.0, if (NEWS_USERS < 0.0737993, 2.98545E-4, if (ALGO_CTR < 1.47845, if (ISTITLE_AVG < 0.515, -0.00529041, -0.00103532), 0.018429)), 6.65685E-5) + \n" +
                     "if (NEWS_USERS < 0.223578, if (NEWS_SEARCHES < 0.258474, -3.069E-6, if (SEARCHES < 0.0136022, 0.0176599, if (MIN_RANK < 1.0, -0.0134598, -0.00250337))), 3.60727E-4) + \n" +
                     "if (DAY_WEEK_AVG_RATIO < 11.945, if (LW_NEWS_USERS < 48.4961, -4.7398E-5, if (ALGO_CLICKS < 1.87723, 0.0178789, 0.00240131)), if (LW_NEWS_CTR_RATIO < 1.85865, -0.0164323, 0.00680461)) + \n" +
                     "if (PREV_DAY_NEWS_CTR_RATIO < 0.216219, if (PREV_DAY_USERS < 0.0840105, if (LW_NEWS_SEARCHES_RATIO < 1.96746, 0.00527106, -0.0232653), if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.533542, 0.0105354, -0.00147978)), 1.96032E-5) + \n" +
                     "if (LW_CTR < 2.10467, -2.34181E-5, if (ENTERTAINMENT < 0.055, if (LW_MAIN_CTR_RATIO < 0.545111, -0.00186289, if (NEWS_MAIN_SEARCHES_RATIO < 1.20214, -4.63276E-4, 0.0235053)), 0.0256623)) + \n" +
                     "if (NATIONALNEWS < 0.215, -9.95943E-5, if (LW_NEWS_CTR < 1.26504, 5.92433E-4, if (NEWS_MAIN_SEARCHES_RATIO < 4.23613, if (YSM_CTR < 0.00285117, 0.0324961, 0.00265865), 0.0435376))) + \n" +
                     "if (YSM_CTR < 0.0395997, if (LW_NEWS_SEARCHES_RATIO < 1.73969, if (LW_MAIN_SEARCHES_RATIO < 20.0256, -7.93453E-5, if (ALGO_CTR < 1.07219, 0.00480972, 0.0310903)), -9.97104E-4), 2.33769E-4) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.761868, if (PREV_DAY_NEWS_CTR_RATIO < 0.980053, if (AVG_SCORE < 400606.0, 0.00185437, if (LW_SEARCHES < 0.918927, 0.0198896, -0.0028412)), 6.98434E-6), -6.12361E-5) + \n" +
                     "if (NEWS_CTR < 1.31473, -1.47001E-5, if (LW_MAIN_SEARCHES_RATIO < 0.392613, if (PREV_DAY_NEWS_CTR < 0.855967, 0.00656145, 0.0531645), if (LW_MAIN_CTR_RATIO < 0.6064, 0.0105008, 2.09583E-4))) + \n" +
                     "if (ALGO_CTR < 3.3716, if (NEWS_CTR < 2.18598, 1.88911E-5, if (LW_NEWS_SEARCHES_RATIO < 2.3084, 8.27546E-4, -0.0121609)), if (NEWS_MAIN_SEARCHES_RATIO < 5.23803, -0.00440315, 0.0413186)) + \n" +
                     "if (DAY_PD_HITS_RATIO < 79.5, if (NEWS_MAIN_SEARCHES_RATIO < 72.49, if (PREV_DAY_NEWS_SEARCHES_RATIO < 11.2643, 3.08728E-5, if (DAY_HITS < 40.5, -0.00439766, 0.0200117)), -0.0203816), -0.0250875) + \n" +
                     "if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.422088, if (LW_MAIN_SEARCHES_RATIO < 15.8595, if (PREV_DAY_NEWS_CTR < 0.567401, 5.47179E-4, 0.0106651), if (BIDDED_SEARCHES < 0.77063, -0.0254713, 0.00761775)), -4.33956E-5) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 8.67546, -1.7051E-6, if (SEARCHES < 0.0118739, 0.0135766, if (ALGO_CTR < 0.697676, -2.48861E-4, if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.480888, -0.0243516, -0.00457767)))) + \n" +
                     "if (LOCAL_QC == 1.0, if (NEWS_CTR < 0.346638, if (LW_USERS < 0.160728, if (POLITICS_QC == 0.0, -0.0103889, 0.0692409), -0.00150749), 1.47672E-4), 7.59478E-5) + \n" +
                     "if (BIDDED_SEARCHES < 7.79863, if (NEWS_USERS < 0.212247, -7.88991E-5, if (AVG_SCORE < 176423.0, -2.66651E-4, if (LW_MAIN_SEARCHES_RATIO < 0.662064, 0.00587605, 7.70017E-4))), -6.54564E-4) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 27.2801, 3.4831E-5, if (BIDDED_SEARCHES < 0.0164564, if (ENTERTAINMENT_QC == 0.0, -0.0319078, 0.0305272), if (ENTERTAINMENT_QC == 1.0, -0.0309537, 4.40641E-4))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 9.60088, -2.84775E-6, if (MAX_MIN_RANK < 5.0, if (ALGO_CTR < 1.25608, -0.00440719, 0.00573622), if (NEWS_CTR < 1.49071, 0.00109495, 0.0298901))) + \n" +
                     "if (DAY_PD_HITS_RATIO < 80.5, if (LW_NEWS_SEARCHES_RATIO < 45.9165, -1.89532E-5, if (DAY_WEEK_AVG_RATIO < 0.595, 0.0244208, if (DAY_WEEK_AVG_RATIO < 1.15, -0.00810109, 0.00531513))), -0.016232) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 65.0244, if (NEWS_CTR < 2.21055, 1.4463E-5, if (PREV_DAY_MAIN_CTR_RATIO < 1.39998, if (USERS < 0.0902444, -0.0288837, -0.00520806), 0.0146236)), -0.0186142) + \n" +
                     "if (DAY_LW_DAY_HITS_RATIO < 33.75, if (AVG_SCORE < 297290.0, 7.35472E-5, -5.61732E-4), if (MAX_SCORE < 200514.0, -0.0113399, if (ALGO_CLICKS < 0.990362, 0.0451975, 0.0062547))) + \n" +
                     "if (LW_NEWS_SEARCHES_RATIO < 1.77794, 7.00143E-5, if (PREV_DAY_NEWS_USERS < 0.0737993, if (MAX_MIN_SCORE < 0.75, 0.00257668, -8.8479E-4), if (SEARCHES < 0.156212, -0.0110319, -9.0476E-4))) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 0.146655, if (NEWS_MAIN_SEARCHES_RATIO < 3.25715, -2.57748E-5, if (USERS < 0.0468741, 4.50433E-4, if (YSM_NCTR < 0.0110028, 0.00633345, 0.0451608))), 4.31141E-5) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 29.7446, 8.17879E-6, if (LW_MAIN_SEARCHES_RATIO < 41.136, -0.0118399, if (NEWS_MAIN_SEARCHES_RATIO < 9.18546, if (ALGO_CLICKS < 0.578097, 0.0106049, -0.0015147), -0.0281154))) + \n" +
                     "if (NEWS_MAIN_SEARCHES_RATIO < 59.3594, if (LW_MAIN_SEARCHES_RATIO < 16.1013, -3.93323E-5, if (PREV_DAY_MAIN_CTR_RATIO < 1.64641, 5.96626E-4, 0.0120328)), if (POLITICS_QC == 0.0, 0.00481287, 0.0549552)) + \n" +
                     "if (DAY_PD_HITS_RATIO < 38.5, 1.00083E-5, if (ISTITLE_AVG < 0.25, if (POLITICS_QC == 0.0, if (HAS_NEWS_QC == 0.0, 0.0110884, 0.0787268), 0.165545), -4.90381E-5)) + \n" +
                     "if (DAY_PD_HITS_RATIO < 52.405, if (LW_MAIN_SEARCHES_RATIO < 21.0036, -2.31432E-6, if (LW_NEWS_SEARCHES_RATIO < 1.10007, 0.00655827, -0.00322249)), if (DUDE < 0.0213316, 0.00165459, 0.0267117)) + \n" +
                     "if (NEWS_CTR < 2.36159, 3.01421E-5, if (PUB_TODAY_AVG < 0.535, if (BIDDED_SEARCHES < 0.0756912, -0.0325427, if (SUGG_LW < 3.5, 0.0120055, -0.00841523)), 0.013018)) + \n" +
                     "if (PREV_DAY_MAIN_CTR_RATIO < 14.1009, if (PREV_DAY_MAIN_SEARCHES_RATIO < 293.154, if (PREV_DAY_MAIN_SEARCHES_RATIO < 106.761, 2.29918E-6, if (PREV_DAY_NEWS_SEARCHES_RATIO < 13.676, -0.0258432, 3.98425E-5)), 0.0146062), 0.0145195) + \n" +
                     "if (ISABSTRACT_AVG < 0.435, if (NEWS_CTR < 0.778514, 6.28755E-5, if (PREV_DAY_MAIN_CTR_RATIO < 0.325327, 0.0171532, 0.00156784)), if (NUM_WORDS < 2.5, -8.6343E-5, -0.00307349)) + \n" +
                     "if (PREV_DAY_NEWS_CTR_RATIO < 0.922298, if (SEARCHES < 0.0120163, if (DAY_PD_HITS_RATIO < 1.25, 0.0163074, -0.0180352), 2.42644E-4), if (LW_NEWS_SEARCHES_RATIO < 1.74561, 9.23406E-7, -0.00112041)) + \n" +
                     "if (PREV_DAY_CTR < 1.06609, -5.66011E-5, if (NEWS_SEARCHES < 1.12712, if (NEWS_USERS < 0.158915, 2.4137E-4, if (SUGG_TW < 0.0829887, 0.00752324, 7.62554E-4)), -0.00259993)) + \n" +
                     "if (NATIONALNEWS < 0.105, -4.51166E-5, if (AVG_SCORE < 359807.0, 3.6945E-4, if (ISTITLE_AVG < 0.885, if (MIN_SCORE < 346564.0, 0.041974, 0.0097136), -9.19818E-4))) + \n" +
                     "if (DAY_LW_DAY_HITS_RATIO < 57.5, if (NEWS_MAIN_SEARCHES_RATIO < 7.52403, -8.69476E-5, if (NEWS_CTR < 1.28406, 4.93978E-4, if (LW_MAIN_CTR_RATIO < 1.5554, 0.00922772, 0.0449952))), 0.0178316) + \n" +
                     "if (NEWS_CTR < 2.81183, -2.30122E-5, if (PEOPLE_QC == 0.0, if (DAY_WEEK_AVG_RATIO < 1.715, if (PREV_DAY_MAIN_SEARCHES_RATIO < 0.906796, 0.0145094, -0.00780837), 0.0292031), 0.166154)) + \n" +
                     "if (LW_MAIN_SEARCHES_RATIO < 19.6323, -9.07867E-6, if (LW_MAIN_CTR_RATIO < 1.34263, if (MIN_SCORE < 229623.0, -0.00765222, 0.00226572), if (LW_NEWS_SEARCHES_RATIO < 1.08241, 0.0130526, -0.0101225))) + \n" +
                     "if (DAY_PD_HITS_RATIO < 4.205, -4.64359E-5, if (LW_NEWS_CTR_RATIO < 0.13101, 0.0518657, if (LW_MAIN_SEARCHES_RATIO < 1.77864, -0.00106588, if (DAY_PD_HITS_RATIO < 4.55, 0.0407926, 0.00454191))))";

}
