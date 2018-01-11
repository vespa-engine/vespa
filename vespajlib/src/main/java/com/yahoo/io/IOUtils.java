// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;


import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;


/**
 * <p>Some static io convenience methods.</p>
 *
 * @author  bratseth
 * @author  Bjorn Borud
 */
public abstract class IOUtils {

    static private final Charset utf8Charset = Charset.forName("utf-8");

    /** Closes a writer, or does nothing if the writer is null */
    public static void closeWriter(Writer writer) {
        if (writer == null) return;
        try { writer.close(); } catch (IOException e) {}
    }

    /** Closes a reader, or does nothing if the reader is null */
    public static void closeReader(Reader reader) {
        if (reader == null) return;
        try { reader.close(); } catch (IOException e) {}
    }

    /** Closes an input stream, or does nothing if the stream is null */
    public static void closeInputStream(InputStream stream) {
        if (stream == null) return;
        try { stream.close(); } catch (IOException e) {}
    }

    /** Closes an output stream, or does nothing if the stream is null */
    public static void closeOutputStream(OutputStream stream) {
        if (stream == null) return;
        try { stream.close(); } catch (IOException e) {}
    }

    /**
     * Creates a buffered reader
     *
     * @param filename the name or path of the file
     * @param encoding the encoding of the file, for instance "UTF-8"
     */
    public static BufferedReader createReader(File filename, String encoding) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding));
    }

    /**
     * Creates a buffered reader
     *
     * @param filename the name or path of the file
     * @param encoding the encoding of the file, for instance "UTF-8"
     */
    public static BufferedReader createReader(String filename, String encoding) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding));
    }

    /** Creates a buffered reader in the default encoding */
    public static BufferedReader createReader(String filename) throws IOException {
        return new BufferedReader(new FileReader(filename));
    }

    /**
     * Creates a buffered writer,
     * and the directories to contain it if they do not exist
     *
     * @param filename the name or path of the file
     * @param encoding the encoding to use, for instance "UTF-8"
     * @param append whether to append to the files if it exists
     */
    public static BufferedWriter createWriter(String filename, String encoding, boolean append) throws IOException {
        createDirectory(filename);
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename, append), encoding));
    }

    /**
     * Creates a buffered writer,
     * and the directories to contain it if they do not exist
     *
     * @param file the file to write to
     * @param encoding the encoding to use, for instance "UTF-8"
     * @param append whether to append to the files if it exists
     */
    public static BufferedWriter createWriter(File file, String encoding, boolean append) throws IOException {
        createDirectory(file.getAbsolutePath());
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, append),encoding));
    }

    /**
     * Creates a buffered writer in the default encoding
     *
     * @param filename the name or path of the file
     * @param append whether to append to the files if it exists
     */
    public static BufferedWriter createWriter(String filename, boolean append) throws IOException {
        createDirectory(filename);
        return new BufferedWriter(new FileWriter(filename, append));
    }

    /**
     * Creates a buffered writer in the default encoding
     *
     * @param file the file to write to
     * @param append whether to append to the files if it exists
     */
    public static BufferedWriter createWriter(File file, boolean append) throws IOException {
        createDirectory(file.getAbsolutePath());
        return new BufferedWriter(new FileWriter(file, append));
    }

    /** Creates the directory path of this file if it does not exist */
    public static void createDirectory(String filename) {
        File directory = new File(filename).getParentFile();

        if (directory != null)
            directory.mkdirs();
    }

    /**
     * Copies the n first lines of a file to another file.
     * If the out file exists it will be overwritten
     *
     * @throws IOException if copying fails
     */
    public static void copy(String inFile, String outFile, int lineCount) throws IOException {
        BufferedReader reader = null;
        BufferedWriter writer = null;

        try {
            reader = createReader(inFile);
            writer = createWriter(outFile, false);
            int c;

            int newLines = 0;
            while (-1 != (c=reader.read()) && newLines<lineCount) {
                writer.write(c);
                if (c=='\n')
                    newLines++;
            }
        } finally {
            closeReader(reader);
            closeWriter(writer);
        }
    }

    /**
     * Copies a file to another file.
     * If the out file exists it will be overwritten.
     *
     * @throws IOException if copying fails
     */
    public static void copy(String inFile, String outFile) throws IOException {
        copy(new File(inFile), new File(outFile));
    }

    /**
     * Copies a file to another file.
     * If the out file exists it will be overwritten.
     */
    public static void copy(File inFile, File outFile) throws IOException {
        try (FileChannel sourceChannel = new FileInputStream(inFile).getChannel();
             FileChannel destChannel = new FileOutputStream(outFile).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    /**
     * Copies all files and subdirectories in a directory to another.
     * Any existing files are overwritten.
     *
     * @param sourceLocation the source directory
     * @param targetLocation the target directory
     * @param maxRecurseLevel if this is 1, only files immediately in sourceLocation are copied,
     *        if it is 2, then files contained in immediate subdirectories are copied, etc.
     *        If it is 0, sourceLocation will only be copied if it is a file, not a directory.
     *        If it is negative, recursion is infinite.
     * @throws IOException if copying any file fails. This will typically result in some files being copied and
     *         others not, i.e this method is not exception safe
     */
    public static void copyDirectory(File sourceLocation , File targetLocation, int maxRecurseLevel) throws IOException {
        copyDirectory(sourceLocation, targetLocation, maxRecurseLevel, new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return true;
            }
        });
    }

    /**
     * Copies all files and subdirectories in a directory to another.
     * Any existing files are overwritten.
     *
     * @param sourceLocation the source directory
     * @param targetLocation the target directory
     * @param maxRecurseLevel if this is 1, only files immediately in sourceLocation are copied,
     *        if it is 2, then files contained in immediate subdirectories are copied, etc.
     *        If it is 0, sourceLocation will only be copied if it is a file, not a directory.
     *        If it is negative, recursion is infinite.
     * @param filter Only copy files passing through filter.
     * @throws IOException if copying any file fails. This will typically result in some files being copied and
     *         others not, i.e this method is not exception safe
     */
    public static void copyDirectory(File sourceLocation , File targetLocation, int maxRecurseLevel, FilenameFilter filter) throws IOException {
        if ( ! sourceLocation.isDirectory()) { // copy file
            InputStream in=null;
            OutputStream out=null;
            try {
                in = new FileInputStream(sourceLocation);
                out = new FileOutputStream(targetLocation);
                // Copy the bits from instream to outstream
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            finally {
                closeInputStream(in);
                closeOutputStream(out);
            }
        }
        else if (maxRecurseLevel!=0) { // copy directory if allowed
            if (!targetLocation.exists())
                targetLocation.mkdirs();

            String[] children = sourceLocation.list(filter);
            for (int i=0; i<children.length; i++)
                copyDirectory(new File(sourceLocation, children[i]),
                              new File(targetLocation, children[i]),
                              maxRecurseLevel-1);
        }
    }

    /**
     * Copies all files and subdirectories (infinitely recursively) in a directory to another.
     * Any existing files are overwritten.
     *
     * @param sourceLocation the source directory
     * @param targetLocation the target directory
     * @throws IOException if copying any file fails. This will typically result in some files being copied and
     *         others not, i.e this method is not exception safe
     */
    public static void copyDirectory(File sourceLocation , File targetLocation) throws IOException {
        copyDirectory(sourceLocation, targetLocation, -1);
    }

    /**
     * Copies the whole source directory (infinitely recursively) into the target directory.
     * @throws IOException if copying any file fails. This will typically result in some files being copied and
     *         others not, i.e this method is not exception safe
     */
    public static void copyDirectoryInto(File sourceLocation, File targetLocation) throws IOException {
        File destination = new File(targetLocation, sourceLocation.getAbsoluteFile().getName());
        copyDirectory(sourceLocation, destination);
    }

    /**
     * Returns the number of lines in a file.
     * If the file does not exists, 0 is returned
     */
    public static int countLines(String file) {
        BufferedReader reader = null;
        int lineCount = 0;

        try {
            reader = createReader(file,"utf8");
            while (reader.readLine() != null)
                lineCount++;
            return lineCount;
        } catch (IOException e) {
            return lineCount;
        } finally {
            closeReader(reader);
        }
    }

    /**
     * Returns a list containing the lines in the given file as strings
     *
     * @return a list of Strings for the lines of the file, in order
     * @throws IOException if the file could not be read
     */
    public static List<String> getLines(String fileName) throws IOException {
        BufferedReader reader = null;

        try {
            List<String> lines = new java.util.ArrayList<>();

            reader = createReader(fileName,"utf8");
            String line;

            while (null != (line = reader.readLine()))
                lines.add(line);
            return lines;
        } finally {
            closeReader(reader);
        }
    }

    /**
     * Recursive deletion of directories
     */
    public static boolean recursiveDeleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();

            for (String child : children) {
                boolean success = recursiveDeleteDir(new File(dir, child));

                if (!success) return false;
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    /**
     * Encodes string as UTF-8 into ByteBuffer
     */
    public static ByteBuffer utf8ByteBuffer(String s) {
        return utf8Charset.encode(s);
    }

    /**
     * Reads the contents of a UTF-8 text file into a String.
     *
     * @param file the file to read, or null
     * @return the file content as a string, or null if the input file was null
     */
    public static String readFile(File file) throws IOException {
        try {
            if (file == null) return null;
            return new String(Files.readAllBytes(file.toPath()), "utf-8");
        }
        catch (NoSuchFileException e) {
            throw new NoSuchFileException("Could not find file '" + file.getAbsolutePath() + "'");
        }
    }

    /**
     * Reads all the content of the given array, in chunks of at max chunkSize
     */
    public static byte[] readBytes(InputStream stream, int chunkSize) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[chunkSize];
        while ((nRead = stream.read(data, 0, data.length)) != -1)
            buffer.write(data, 0, nRead);
        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * Reads the content of a file into a byte array
     */
    public static byte[] readFileBytes(File file) throws IOException {
        long lengthL = file.length();
        if (lengthL>Integer.MAX_VALUE)
            throw new IllegalArgumentException("File too big for byte array: "+file.getCanonicalPath());

        InputStream in = null;
        try {
            in = new FileInputStream(file);
            int length = (int)lengthL;
            byte[] array = new byte[length];
            int offset = 0;
            int count=0;
            while (offset < length && (count = in.read(array, offset, (length - offset)))>=0)
                offset += count;
            return array;
        }
        finally {
            if (in != null)
                in.close();
        }
    }

    /**
     * Reads all data from a reader into a string. Uses a buffer to speed up reading.
     */
    public static String readAll(Reader reader) throws IOException {
        StringBuilder ret=new StringBuilder();
        BufferedReader buffered = new BufferedReader(reader);
        int c;
        while ((c=buffered.read())!=-1)
            ret.appendCodePoint(c);
        buffered.close();
        return ret.toString();
    }

    /** Read an input stream completely into a string */
    public static String readAll(InputStream stream, Charset charset) throws IOException {
        return readAll(new InputStreamReader(stream, charset));
    }

    /** Convenience method for closing a list of readers. Does nothing if the given reader list is null. */
    public static void closeAll(List<Reader> readers) {
        if (readers==null) return;
        for (Reader reader : readers)
            closeReader(reader);
    }

    /**
     * Writes the given string to the file
     */
    public static void writeFile(File file, String text, boolean append) throws IOException {
       BufferedWriter out = null;
       try {
           out = createWriter(file, append);
           out.write(text);
       }
       finally {
           closeWriter(out);
       }
    }

    /** Writes the given content to the file (replacing any existing content) */
    public static void writeFile(File file, byte[] content) throws UncheckedIOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes the given string to the file
     */
    public static void writeFile(String file, String text, boolean append) throws IOException {
        writeFile(new File(file), text, append);
     }

}
