// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import static com.yahoo.language.LinguisticsCase.toLowerCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import com.yahoo.text.Utf8;

public class CasingVariants {

    public static void main(String[] args) throws FileNotFoundException, IOException {
        int read = 0;
        char[] buffer = new char[5000];
        String raw;
        File f = new File(args[0]);
        StringBuilder s = new StringBuilder();
        InputStream in = new FileInputStream(f);

        Reader r = new InputStreamReader(in, Utf8.getCharset());
        while (read != -1) {
            read = r.read(buffer);
            if (read > 0) {
                s.append(buffer, 0, read);
            }
        }
        raw = s.toString();
        System.out.write(Utf8.toBytes(toLowerCase(raw)));
    }
}
