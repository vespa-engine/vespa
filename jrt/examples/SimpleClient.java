// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.jrt.*;

public class SimpleClient {

    public static void main(String args[]) {
        if (args.length != 3) {
            System.err.println("usage: SimpleClient spec n1 n2");
            System.exit(1);
        }
        Supervisor supervisor = new Supervisor(new Transport());
        Target target = supervisor.connect(new Spec(args[0]));
        Request req = new Request("add");
        req.parameters().add(new Int32Value(Integer.parseInt(args[1])));
        req.parameters().add(new Int32Value(Integer.parseInt(args[2])));
        target.invokeSync(req, 5.0);
        if (req.checkReturnTypes("i")) {
            System.out.println(args[1] + " + " + args[2] + " = "
                               + req.returnValues().get(0).asInt32());
        } else {
            System.out.println("Invocation failed: "
                               + req.errorCode() + ": " + req.errorMessage());
        }
        target.close();
        supervisor.transport().shutdown().join();
    }
}
