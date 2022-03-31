// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.security.Permission;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class GbdtConverterTestCase {

    @Before
    @SuppressWarnings("removal")
    public void enableSecurityManager() {
        System.setSecurityManager(new NoExitSecurityManager());
    }

    @After
    @SuppressWarnings("removal")
    public void disableSecurityManager() {
        System.setSecurityManager(null);
    }

    @Test
    public void testOnlyOneArgumentIsAccepted() throws UnsupportedEncodingException {
        assertError("Usage: GbdtConverter <filename>\n", new String[0]);
        assertError("Usage: GbdtConverter <filename>\n", new String[] { "foo", "bar" });
    }

    @Test
    public void testFileIsFound() throws UnsupportedEncodingException {
        assertError("Could not find file 'not.found'.\n", new String[] { "not.found" });
    }

    @Test
    public void testFileParsingExceptionIsCaught() throws UnsupportedEncodingException {
        assertError("An error occurred while parsing the content of file 'src/test/files/gbdt_err.xml': " +
                    "Node 'Unknown' has no 'DecisionTree' children.\n",
                    new String[] { "src/test/files/gbdt_err.xml" });
    }

    @Test
    public void testEmptyTreesAreIgnored() throws Exception {
        assertConvert("src/test/files/gbdt_empty_tree.xml",
                      "if (INFD_SCORE < 3.2105989, if (GMP_SCORE < 0.013873, if (INFD_SCORE < 1.8138845, 0.0018257, if (GMP_SCORE < 0.006184, 0.0034753, 0.0062119)), if (INFD_SCORE < 1.5684295, if (GMP_SCORE < 0.0217475, 0.0043064, 0.0082065), 0.0110743)), if (GMP_SCORE < 0.010012, if (INFD_SCORE < 5.5982456, if (GMP_SCORE < 0.0052305, 0.0060169, 0.0094888), 0.0119292), 0.017415))\n" +
                      "\n");
    }

    @Test
    public void testTreesMayContainAResponse() throws Exception {
        assertConvert("src/test/files/gbdt_tree_response.xml",
                      "if (INFD_SCORE < 2.128036, -1.12E-5, 8.71E-5) +\n" +
                      "if (value(0) < 1.0, 2.8E-6, 0.0) +\n" +
                      "if (GMP_SCORE < 0.016798, if (INFD_SCORE < 3.9760852, if (INFD_SCORE < 0.1266405, -5.98E-5, 2.25E-5), -1.383E-4), 1.529E-4)\n" +
                      "\n");
    }

    @Test
    public void testConvertedModelIsPrintedToSystemOut() throws Exception {
        assertConvert("src/test/files/gbdt.xml",
                      "if (F55 < 2.0932798, if (F42 < 1.7252731, if (F33 < 0.5, if (F38 < 1.5367546, 1.7333333, 1.3255814), if (F37 < 0.675922, 1.9014085, 1.0)), if (F109 < 0.5, if (F116 < 5.25, if (F111 < 0.0521445, 1.0, 1.9090909), if (F38 < 4.0740733, 0.8, if (F38 < 6.6152048, 1.7142857, 0.625))), 1.5945946)), if (F109 < 0.5, if (F113 < 0.7835808, if (F110 < 491.0, if (F56 < 2.5423126, if (F108 < 243.5, 1.375, 0.78), 0.5), 2.0), if (F103 < 0.9918365, 1.6, 0.3333333)), if (F59 < 0.9207, if (F30 < 0.86, 1.5890411, 0.625), if (F100 < 5.9548216, 1.0, 0.0)))) +\n" +
                      "if (F55 < 59.5480576, if (F42 < 1.8308522, if (F100 < 5.9549484, if (F107 < 0.5, -0.3406279, if (F56 < 1.7057916, if (F36 < 3.778285, if (F103 < 0.5600199, 0.047108, if (F36 < 1.2203553, if (F102 < 1.5, 0.0460316, -0.473794), -0.9825869)), -0.8848045), if (F47 < 15.5, 0.348047, -1.0890411))), 1.75), if (F113 < 0.8389627, if (F110 < 7.5, -0.5778378, if (F111 < 0.8596972, if (F114 < 831.5, if (F113 < 0.3807178, 0.0497646, if (F110 < 63.0, 0.6549377, 0.2486999)), if (F39 < 8.9685574, 0.3222195, -0.1690968)), 1.0381818)), if (F58 < 0.889763, -0.0702703, -1.6))), if (F102 < 3.5, -0.3059684, -1.5890411)) +\n" +
                      "if (F55 < 119.6311035, if (F55 < 90.895813, if (F39 < 12.162282, if (F35 < 1.1213787, if (F55 < 34.9389648, if (F45 < 3.5, if (F51 < 0.0502058, if (F103 < 0.8550526, if (F55 < 4.96804, 0.048519, 0.6596588), if (F38 < 1.3808891, -0.7416763, 0.0176633)), 0.4502234), -0.6811898), 0.5572351), if (F100 < 3.3971992, if (F39 < 7.0869236, if (F43 < 5.5100875, if (F46 < 4.5, -0.1702421, -0.9797453), -1.5426025), 0.0774408), if (F52 < 22.3562355, if (F35 < 4.4263992, 0.4011598, -0.3898472), -1.75))), if (F39 < 14.5762558, if (F109 < 0.5, 1.6616928, 0.4001626), if (F100 < 3.0519419, 0.616491, -0.1808479))), -1.2135522), 0.5535716) +\n" +
                      "if (F43 < 9.272151, if (F36 < 9.0613861, if (F115 < 36.5, if (F34 < 1.4407213, if (F41 < 10.4713802, if (F34 < 1.2610778, if (F105 < 8.2159586, if (F46 < 88.5, 0.0075843, -0.6358738), if (F105 < 9.5308332, 1.4464284, -0.0895592)), 0.3532708), -1.8289603), if (F45 < 24.5, if (F111 < 0.9095335, if (F113 < 0.0529755, -0.6272416, if (F50 < 34.2163391, if (F113 < 0.0813664, 0.3683843, if (F34 < 1.6283135, -0.6334628, -0.1610307)), 1.5559684)), -1.7492068), 1.5060212)), if (F49 < 23.5787125, if (F100 < 6.5115452, if (F37 < 0.8601408, if (F57 < 6.5, 0.0547747, 1.193346), 0.6402962), 1.7395205), 2.5559684)), -3.1016318), 1.8657542) +\n" +
                      "if (F55 < 764.9404297, if (F34 < 23.2379246, if (F36 < 9.2296076, if (F114 < 116.0, if (F108 < 13.5, if (F108 < 12.5, -0.2736142, -1.7384173), if (F110 < 10.5, 0.0794336, -0.2171646)), if (F114 < 129.0, if (F109 < 0.5, 1.4407836, -0.1458547), if (F111 < 0.9703438, if (F47 < 18.5, if (F32 < 3.5, 0.0708936, if (F118 < 0.6794872, if (F119 < 3.8533711, if (F34 < 0.1213822, -2.0046196, -8.566E-4), -0.9490828), 0.0790339)), if (F113 < 0.3637481, 0.1161088, -0.9997786)), 1.3003114))), if (F111 < 0.2438112, -2.0582902, 0.6918949)), if (F115 < 95.0, -2.8602383, -0.0063699)), if (F101 < 0.9411763, -2.0253283, -0.6417007)) +\n" +
                      "if (F114 < 516.0, if (F49 < 8.9197922, if (F48 < 3.5, if (F36 < 1.3889931, if (F43 < 0.9699799, if (F34 < 9.6113167, if (F106 < 8.5, if (F108 < 153.5, if (F110 < 130.5, 0.180242, 2.545163), if (F108 < 161.5, -2.2253985, if (F55 < 31.4965668, -0.0122572, 0.7364454))), -0.2596613), 0.7247348), if (F111 < 0.2817393, -0.6409092, 0.2100071)), if (F116 < 18.75, 0.511352, -0.1093323)), 0.9379161), 0.3603908), if (F46 < 32.5, if (F46 < 5.5, if (F39 < 11.7440758, if (F115 < 774.0, -0.0433343, -1.7439904), -0.3662575), 0.5413771), if (F110 < 67.0, if (F46 < 34.5, -2.6581287, -0.9399502), 0.075664))) +\n" +
                      "if (F42 < 24.3080139, if (F118 < 0.8452381, if (F119 < 6.2847767, if (F100 < 3.2778931, if (F46 < 30.0, if (F43 < 1.2712233, if (F104 < 3.5, 0.1365837, 0.5592712), if (F39 < 0.6294491, -0.8729556, -0.0123421)), 3.7677864), if (F111 < 0.6580936, if (F103 < 0.9319581, -0.2822538, if (F107 < 1.5, -0.3983539, if (F104 < 5.5, 0.0792465, 0.7273864))), if (F104 < 3.5, -1.1550477, 0.0490706))), 1.4735778), if (F111 < 0.3724709, if (F51 < 16.0989189, if (F114 < 154.0, if (F108 < 57.5, -0.0675733, -0.3994327), -0.0250285), -1.4871782), if (F34 < 2.1943491, 0.0229469, if (F108 < 1527.0, 1.4706301, 0.0285333)))), 3.489949) +\n" +
                      "if (F34 < 30.3465347, if (F103 < 0.9996098, if (F38 < 0.558669, if (F105 < 3.6287756, if (F104 < 3.5, if (F31 < 0.86, 0.1121421, 1.8153648), -0.8281607), if (F55 < 37.6819153, 0.9656266, 0.1585065)), if (F113 < 0.840385, if (F38 < 9.6623116, if (F46 < 136.0, if (F53 < 0.5548913, if (F38 < 8.4469957, if (F34 < 3.1969421, if (F114 < 20.0, -0.2944335, 0.03499), if (F34 < 3.4671984, -1.3154796, -0.1742507)), 0.4071658), if (F105 < 2.315434, if (F110 < 59.5, -0.1713032, -1.420465), -0.1456236)), 0.5520287), if (F108 < 12156.5, if (F111 < 0.3892631, -0.16285, -0.9015614), -2.6391831)), 0.2011691)), -3.073049), -3.2461861) +\n" +
                      "if (F55 < 28.4668102, if (F34 < 0.4929269, if (F30 < 0.86, if (F37 < 0.8360082, -0.0815482, -0.7898247), -0.5144471), if (F108 < 20498.0, if (F44 < 1.1856511, if (F56 < 1.0706565, if (F39 < 8.377079, if (F59 < 0.5604, 0.0429508, if (F34 < 0.7287493, -1.0264078, 0.6052195)), -0.4814408), if (F119 < 3.7530813, if (F115 < 8.5, 0.4916013, 0.0457533), if (F114 < 1093.5, 1.1673864, 0.3411176))), -0.6176305), if (F100 < 3.151973, 2.6908011, 0.3835885))), if (F116 < 62.0, if (F114 < 562.0, -0.415543, if (F103 < 0.9826763, -0.1169933, if (F104 < 0.5, -0.0665763, 1.0238317))), if (F100 < 5.8046961, -3.2954836, 0.2781039))) +\n" +
                      "if (F34 < 26.9548168, if (F35 < 18.4714928, if (F115 < 698.0, if (F116 < 41.5, if (F38 < 1.1138718, if (F46 < 9.0, if (F31 < 0.86, 0.1059075, -0.2995292), if (F46 < 25.5, if (F46 < 13.0, 0.6297316, 1.8451736), 0.2079161)), if (F38 < 19.3839836, if (F49 < 29.9797497, if (F46 < 235.5, if (F38 < 1.2626771, -0.5165347, if (F35 < 10.3027954, if (F50 < 0.2823648, -0.0424489, if (F113 < 0.0776736, 0.7495954, -0.2948665)), 0.3229146)), -1.0711968), 0.3153474), if (F116 < 5.2182379, 2.8017734, 0.3444192))), if (F113 < 0.5691726, 1.7530511, 0.3534861)), -2.4915219), if (F103 < 0.9680555, -2.1724317, 0.2143739)), 3.1712332)\n" +
                      "\n");
    }

    @Test
    public void testSetTestsWork() throws Exception {
        assertConvert("src/test/files/gbdt_set_inclusion_test.xml",
                      "if (AGE_GROUP$ in [2], if (EDUCATION_LEVEL$ in [0], -0.25, 0.125), if (AGE_GROUP$ in [1], 0.125, 0.25)) +\n" +
                      "if (AGE_GROUP$ in [2], if (EDUCATION_LEVEL$ in [0], -0.2189117, -0.0), if (EDUCATION_LEVEL$ in [0], 0.1094559, 0.2343953)) +\n" +
                      "if (AGE_GROUP$ in [2], -0.0962185, if (EDUCATION_LEVEL$ in [0], if (AGE_GROUP$ in [1], 0.0, 0.2055456), 0.205553)) +\n" +
                      "if (EDUCATION_LEVEL$ in [0], 0.0905977, 0.1812016) +\n" +
                      "if (EDUCATION_LEVEL$ in [0, 1], if (AGE_GROUP$ in [2], if (EDUCATION_LEVEL$ in [0], -0.191772, -0.0), if (AGE_GROUP$ in [1], if (EDUCATION_LEVEL$ in [0], 0.0, 0.1608304), 0.1708644)), 0.1923393) +\n" +
                      "if (EDUCATION_LEVEL$ in [\"foo\", \"bar\"], if (AGE_GROUP$ in [2], if (EDUCATION_LEVEL$ in [\"baz\"], -0.1696624, -0.0), if (AGE_GROUP$ in [1], if (EDUCATION_LEVEL$ in [0], 0.0, 0.1438091), 0.1521967)), 0.2003772) +\n" +
                      "if (value(0) < 1.0, -0.0108278, 0.0) +\n" +
                      "if (EDUCATION_LEVEL$ in [0], -0.1500528, if (GENDER$ in [1], 0.0652894, 0.1543407)) +\n" +
                      "if (AGE_GROUP$ in [1], 0.0, 0.1569706) +\n" +
                      "if (AGE_GROUP$ in [1], 0.0, if (EDUCATION_LEVEL$ in [1], 0.0, 0.1405829))\n" +
                      "\n");
    }

    @Test
    public void testExtModelCausesBranchProbabilitiesToBeUsed() throws Exception {
        assertConvert("src/test/files/gbdt.ext.xml",
                      "if (F4 < 0.6972222, if (F1 < 0.7928572, if (F54 < 0.9166666, 0.1145211, if (F111 < 1105.0, 0.3115265, 1.6772487, 0.77256316), 0.89193755), 1.493617, 0.970347), if (F111 < 85.5, 1.1202186, 2.5111421, 0.33763838), 0.93598676) +\n" +
                      "if (F1 < 0.8875, if (F1 < 0.0634921, 0.4755052, if (F111 < 8765.0, -0.0572274, 0.542222, 0.983461), 0.04500549), if (F114 < 55.0, -0.2409815, if (F54 < 0.55, 0.2211539, 1.3125561, 0.29620853), 0.21268657), 0.9683477) +\n" +
                      "if (F4 < 0.6972222, if (F3 < 0.9285715, if (F8 < 0.0540936, -0.007629, 0.322873, 0.95869595), if (F1 < 0.8166667, 0.843579, 0.1053924, 0.57522124), 0.97148263), if (F4 < 0.7619048, -0.5500016, 0.0274784, 0.5784133), 0.93598676) +\n" +
                      "if (F74 < 0.875, if (F54 < 0.8452381, -0.0031926, if (F111 < 141.5, -0.1402742, if (F4 < 0.5871212, 1.2691849, 0.2681826, 0.35703), 0.47206005), 0.92346483), if (F111 < 1105.0, -0.0588169, -0.7294473, 0.7697161), 0.92512107) +\n" +
                      "if (F1 < 0.7619048, 0.0089472, if (F3 < 0.9285715, if (F114 < 36.5, -1.1389426, if (F97 < 0.0468557, if (F6 < 0.5357143, 0.5614127, -0.2162048, 0.32456142), -0.8289478, 0.742671), 0.21483377), 0.0168442, 0.3867458), 0.9402976) +\n" +
                      "if (F1 < 0.6583333, -0.0187975, if (F74 < 0.2104235, 0.1951745, if (F68 < 0.8158333, if (F68 < 0.7616667, -0.0701389, -1.908711, 0.8685714), if (F91 < 0.9516667, 0.2880719, 0.0202404, 0.08918849), 0.043402776), 0.12821622), 0.72688085) +\n" +
                      "if (F97 < 0.0104738, if (F4 < 0.6833333, -0.1119661, -0.7331711, 0.795539), if (F111 < 1.5, -0.0487729, if (F54 < 0.0294118, if (F6 < 0.225, 0.3140816, 0.0241852, 0.44444445), 0.0063921, 0.077068806), 0.20816082), 0.015885202) +\n" +
                      "if (F8 < 0.0488095, if (F97 < 0.0196587, -0.037317, if (F4 < 0.5527778, 0.0085123, if (F111 < 4064.5, if (F111 < 109.5, 0.2020749, -0.1841633, 0.5994437), 0.4359319, 0.8789731), 0.86483806), 0.24595065), -0.1090751, 0.94791543) +\n" +
                      "if (F111 < 7801.5, 0.005243, if (F4 < 0.5444444, -0.4434354, if (F4 < 0.725, if (F111 < 86382.5, if (F77 < 0.0250039, 0.9485625, 0.1099304, 0.2840909), -1.5740248, 0.9361702), -0.2924902, 0.48205128), 0.47580644), 0.97803235) +\n" +
                      "if (F4 < 0.9270834, if (F1 < 0.8166667, 0.0033574, if (F4 < 0.7071428, -0.2470163, 0.0482702, 0.5796915), 0.9535162), if (F54 < 0.5833334, 0.8142192, if (F1 < 0.95, 1.2211719, -0.0357525, 0.07643312), 0.20304568), 0.9883666) +\n" +
                      "if (F113 < 37.5050011, if (F111 < 252.5, -0.0110506, if (F4 < 0.69375, if (F5 < 0.9, 0.0488562, 0.3987899, 0.9362022), if (F74 < 0.75, -0.2113237, 0.3806402, 0.8606272), 0.8527072), 0.7694356), -0.5899943, 0.9981103) +\n" +
                      "if (F3 < 0.4365079, -0.0192181, if (F77 < 0.1715686, if (F111 < 1187.5, 0.016142, if (F112 < 467.5, if (F68 < 0.855, 0.9831077, 0.227789, 0.12048193), 0.0345274, 0.36617646), 0.89238805), 0.7605657, 0.9962163), 0.62542814) +\n" +
                      "if (F5 < 0.6125, if (F4 < 0.7928572, 0.0063205, 1.68561, 0.99925923), if (F113 < 1.6900001, if (F113 < 1.635, -0.0275853, 1.1438084, 0.99412453), if (F97 < 0.0363399, -0.0843354, -0.346791, 0.552356), 0.8166987), 0.876934) +\n" +
                      "if (F8 < 0.1396104, -0.001079, if (F54 < 0.55, if (F111 < 513.5, if (F77 < 0.0380987, -0.1117221, 0.9370234, 0.6551724), 1.654114, 0.7631579), if (F113 < 1.0700001, 0.1069487, -1.0835573, 0.8292683), 0.48101267), 0.9953348) +\n" +
                      "if (F6 < 0.7321429, 0.0033418, if (F111 < 74.5, if (F4 < 0.6708333, if (F1 < 0.5435606, 0.5229282, -0.451666, 0.11594203), 0.253665, 0.3270142), if (F113 < 2.47, -0.2267124, 0.2586769, 0.8803419), 0.4741573), 0.947443)\n" +
                      "\n");
    }

    private static void assertConvert(String gbdtModelFile, String expectedExpression)
            throws ParseException, UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        GbdtConverter.main(new String[] { gbdtModelFile });
        String actualExpression = out.toString("UTF-8");
        assertEquals(expectedExpression, actualExpression);
        assertNotNull(new RankingExpression(actualExpression));
    }

    private static void assertError(String expected, String[] args) throws UnsupportedEncodingException {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));
        try {
            GbdtConverter.main(args);
            fail();
        } catch (ExitException e) {
            assertEquals(1, e.status);
            assertEquals(expected, err.toString("UTF-8"));
        }
    }

    @SuppressWarnings("removal")
    private static class NoExitSecurityManager extends SecurityManager {

        @Override
        public void checkPermission(Permission perm) {
            // allow anything
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            // allow anything
        }

        @Override
        public void checkExit(int status) {
            throw new ExitException(status);
        }
    }

    private static class ExitException extends SecurityException {

        final int status;

        ExitException(int status) {
            this.status = status;
        }
    }
}
