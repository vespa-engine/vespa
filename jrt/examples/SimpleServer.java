// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.jrt.*;

public class SimpleServer {

    public void rpc_add(Request req) {
        int n1 = req.parameters().get(0).asInt32();
        int n2 = req.parameters().get(1).asInt32();
        req.returnValues().add(new Int32Value(n1 + n2));
    }

    public static void main(String args[]) {
        if (args.length != 1) {
            System.err.println("usage: SimpleServer spec");
            System.exit(1);
        }
        Supervisor supervisor = new Supervisor(new Transport());
        supervisor.addMethod(new Method("add", "ii", "i",
                                        new SimpleServer(), "rpc_add")
                             .methodDesc("calculate the sum of 2 integers")
                             .paramDesc(0, "n1", "an integer")
                             .paramDesc(1, "n2", "another integer")
                             .returnDesc(0, "ret", "n1 + n2"));
        try {
            Acceptor acceptor = supervisor.listen(new Spec(args[0]));
            System.out.println("Listening at " + args[0]);
            supervisor.transport().join();
            acceptor.shutdown().join();
        } catch (ListenFailedException e) {
            System.err.println("Could not listen at " + args[0]);
            supervisor.transport().shutdown().join();
        }
    }
}
