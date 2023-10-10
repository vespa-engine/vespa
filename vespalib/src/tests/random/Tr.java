// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import java.util.*;

class Tr
{
    public static void main(String[] args) {
        Random rng = new Random(1);
        for (int i=0; i<10; i++) {
            double d = rng.nextDouble();
            System.out.println("double["+i+"] = "+d);
        }
    }
}
