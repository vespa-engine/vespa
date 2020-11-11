// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchlib.tensor;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.BooleanValue;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleCompatibleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class EvaluateTensorConformance {

    public static void main(String[] args) {
        var app = new EvaluateTensorConformance();
        app.evaluateStdIn();
    }

    OutputStream outStream = new BufferedOutputStream(System.out);

    void evaluateStdIn() {
        int count = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            String test = br.readLine();
            while (test != null) {
                boolean success = testCase(test, count);
                if (!success) {
                    System.err.println("FAILED testcase "+count);
                }
                ++count;
                test = br.readLine();
            }
        } catch (IOException e) {
            System.err.println(count + " FAILED : " + e.toString());
        }
    }

    void output(Slime result) {
        try {
            new JsonFormat(true).encode(outStream, result);
            outStream.write('\n');
            outStream.flush();
        } catch (IOException e) {
            System.err.println("FAILED writing output: "+e);
            System.exit(1);
        }
    }
        
    private boolean testCase(String test, int count) {
        boolean wasOk = false;
        try {
            Slime input = SlimeUtils.jsonToSlime(test);
            Slime result = new Slime();
            var top = result.setObject();
            SlimeUtils.copyObject(input.get(), top);
            var num_tests = input.get().field("num_tests");
            if (input.get().field("num_tests").valid()) {
                long expect = input.get().field("num_tests").asLong();
                wasOk = (expect == count);
            } else if (input.get().field("expression").valid()) {
                String expression = input.get().field("expression").asString();
                MapContext context = getInput(input.get().field("inputs"));
                Tensor actual = evaluate(expression, context);
                top.field("result").setData("vespajlib", TypedBinaryFormat.encode(actual));
                wasOk = true;
            } else {
                System.err.println(count + " : Invalid input >>>"+test+"<<<");
                wasOk = false;
            }
            output(result);
        } catch (Exception e) {
            System.err.println(count + " : " + e.toString());
            wasOk = false;
        }
        return wasOk;
    }

    private Tensor evaluate(String expression, MapContext context) throws ParseException {
        Value value = new RankingExpression(expression).evaluate(context);
        if (!(value instanceof TensorValue)) {
            throw new IllegalArgumentException("Result is not a tensor");
        }
        return ((TensorValue)value).asTensor();
    }

    private MapContext getInput(Inspector inputs) {
        MapContext context = new MapContext();
        inputs.traverse(new ObjectTraverser() {
            public void field(String name, Inspector contents) {
                String value = contents.asString();
                Tensor tensor = getTensor(value);
                context.put(name, new TensorValue(tensor));
            }
        });
        return context;
    }

    private Tensor getTensor(String binaryRepresentation) {
        byte[] bin = getBytes(binaryRepresentation);
        return TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(bin));
    }

    private byte[] getBytes(String binaryRepresentation) {
        return parseHexValue(binaryRepresentation.substring(2));
    }

    private byte[] parseHexValue(String s) {
        final int len = s.length();
        byte[] bytes = new byte[len/2];
        for (int i = 0; i < len; i += 2) {
            int c1 = hexValue(s.charAt(i)) << 4;
            int c2 = hexValue(s.charAt(i + 1));
            bytes[i/2] = (byte)(c1 + c2);
        }
        return bytes;
    }

    private int hexValue(Character c) {
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        } else if (c >= '0' && c <= '9') {
            return c - '0';
        }
        throw new IllegalArgumentException("Hex contains illegal characters");
    }

}

