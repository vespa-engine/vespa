// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.document.Document;
import com.yahoo.docproc.*;

public class ExampleDocumentProcessor extends DocumentProcessor {

    public Progress process(Document document, Arguments arguments, Processing processing) {
        document.setValue("title", "Worst music ever");
	System.out.println("Processed " + document);
        return Progress.DONE;
    }

}
