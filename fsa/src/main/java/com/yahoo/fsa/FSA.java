// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Finite-State Automaton.
 *
 * @author Peter Boros
 */
public class FSA implements Closeable {

    /**
     * Thread local state object used to traverse a Finite-State Automaton.
     */
    public static class State {

        FSA fsa;
        int state = 0;
        int hash = 0;

        private State(FSA fsa) {
            this.fsa = fsa;
            start();
        }

        public void start(){
            state = fsa.start();
            hash = 0;
        }

        public void delta(byte symbol) {
            delta(fsa.map(), symbol);
        }

        private void delta(Maps m, byte symbol) {
            hash += m.hashDelta(state,symbol);
            state = m.delta(state,symbol);
        }

        /** Returns whether the given symbol would take us to a valid state, without changing the state */
        public boolean peekDelta(byte symbol) {
            return fsa.delta(state,symbol)!=0;
        }

        public boolean tryDelta(byte symbol) {
            int lastHash=hash;
            int lastState=state;
            delta(symbol);
            if (isValid()) return true;

            hash=lastHash;
            state=lastState;
            return false;
        }

        public void delta(char chr){
            CharBuffer chrbuf = CharBuffer.allocate(1);
            chrbuf.put(0,chr);
            ByteBuffer buf = fsa.encode(chrbuf);
            Maps m = fsa.map();
            while(state >0 && buf.position()<buf.limit()){
                delta(m, buf.get());
            }
        }

        /** Jumps ahead by string */
        public void delta(String string){
            ByteBuffer buf = fsa.encode(string);
            Maps m = fsa.map();
            while (state >0 && buf.position()<buf.limit()){
                delta(m, buf.get());
            }
        }

        /**
         * Jumps ahead by string if that puts us into a valid state, does nothing otherwise
         *
         * @return whether we jumped to a valid state (true) or di nothing (false)
         */
        public boolean tryDelta(String string){
            int lastHash=hash;
            int lastState=state;
            delta(string);
            if (isValid()) return true;

            hash=lastHash;
            state=lastState;
            return false;
        }

        /** Jumps ahead by a word -  if this is not the first word, it must be preceeded by space. */
        public void deltaWord(String string){
            if (state != fsa.start()) {
                delta((byte)' ');
            }
            delta(string);
        }

        /**
         * Tries to jump ahead by one word. If the given string is not the next complete valid word, nothing is done.
         */
        public boolean tryDeltaWord(String string){
            int lastHash=hash;
            int lastState=state;
            tryDelta((byte)' ');
            delta(string);
            if (isValid() && peekDelta((byte)' ')) return true;
            if (isFinal()) return true;

            hash=lastHash;
            state=lastState;
            return false;
        }

        public boolean isFinal(){
            return fsa.isFinal(state);
        }

        public boolean isStartState() {
            return fsa.start() == state;
        }

        public boolean isValid(){
            return state !=0;
        }

        public ByteBuffer data(){
            return fsa.data(state);
        }

        public String dataString(){
            return fsa.dataString(state);
        }

        public int hash(){
            return hash;
        }

        public ByteBuffer lookup(String str){
            start();
            delta(str);
            return fsa.data(state);
        }

        public boolean hasPerfectHash(){
            return fsa.hasPerfectHash();
        }

    }

    /**
     * Class used to iterate over all accepted strings in the fsa.
     */
    public static class Iterator implements java.util.Iterator<Iterator.Item> {

        /**
         * Internally, this class stores the state information for the iterator.
         * Externally, it is used for accessing the data associated with the iterator position.
         */
        public static class Item {
            private FSA fsa;
            private java.util.Stack<Byte> string;
            private int symbol;
            private int state;
            private java.util.Stack<Integer> stack;

            /**
             * Constructor
             *
             * @param fsa the FSA object the iterator is associated with.
             * @param state the state used as start state.
             */
            public Item(FSA fsa, int state) {
                this.fsa = fsa;
                this.string = new java.util.Stack<>();
                this.symbol = 0;
                this.state = state;
                this.stack = new java.util.Stack<>();
            }

            /**
             * Copy constructor. (Does not copy the state stack)
             */
            public Item(Item item) {
                this.fsa = item.fsa;
                this.string = new java.util.Stack<>();
                for (java.util.Iterator<Byte> itr = item.string.iterator(); itr.hasNext(); ) {
                    byte b = itr.next();
                    this.string.push(b);
                }
                this.symbol = item.symbol;
                this.state = item.state;
                // no need to fill the stack as this constructor is used by Iterator::next()
                this.stack = null;
            }

            public String getString() {
                ByteBuffer buffer = ByteBuffer.allocate(string.size());
                for (java.util.Iterator<Byte> itr = string.iterator(); itr.hasNext(); ) {
                    byte b = itr.next();
                    buffer.put(b);
                }
                buffer.flip();
                return fsa.decode(buffer);
            }

            public ByteBuffer getData() {
                return fsa.data(state);
            }

            public String getDataString() {
                return fsa.dataString(state);
            }

            @Override
            public String toString() {
                return "string: " + string + "(" + getString() + "), symbol: " + symbol + ", state: " + state;
            }
        }

        private Item item;
        boolean useInitState = false;

        /**
         * Constructor.
         * @param state the state to create the iterator from.
         */
        public Iterator(State state) {
            item = new Item(state.fsa, state.state);
            if (state.isFinal()) {
                useInitState = true;
            } else {
                findNext();
            }
        }

        private void findNext() {
            int nextState;
            int depth;

            if (item.symbol == 256 || item.fsa == null) {
                throw new NoSuchElementException();
            }

            // flip the flag now that the first state has been returned
            if (useInitState) {
                useInitState = false;
            }

            // try to find the next final state
            for(;;) {
                item.symbol++;
                if (item.symbol < 256) {
                    byte symbol = (byte)item.symbol;
                    nextState = item.fsa.delta(item.state, (byte)item.symbol);
                    if (nextState != 0) {
                        item.string.push((byte)item.symbol);
                        item.stack.push(item.state);
                        item.state = nextState;
                        item.symbol = 0;
                        if (item.fsa.isFinal(nextState)) {
                            break;
                        }
                    }
                } else { // backtrack
                    if ((depth = item.string.size()) > 0) {
                        byte b = item.string.pop(); // remove the last byte
                        item.symbol = b < 0 ? b + 256 : b;
                        item.state = item.stack.pop();
                    } else {
                        item.state = 0;
                        break;
                    }
                }
            }
        }

        public boolean hasNext() {
            return item.state != 0 || useInitState;
        }

        public Item next() {
            Item retval = new Item(item);
            findNext();
            return retval;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public State getState(){
        return new State(this);
    }

    /**
     * Returns a new iterator to the start state.
     */
    public Iterator iterator() {
        return new Iterator(getState());
    }

    /**
     * Returns a new iterator to the given state.
     * @param state the state to create the iterator from.
     */
    public Iterator iterator(State state) {
        return new Iterator(state);
    }

    private static class Maps implements Closeable {
        Maps(FileInputStream file) throws IOException {
            _header = file.getChannel().map(MapMode.READ_ONLY,0,256);
            _header.order(ByteOrder.LITTLE_ENDIAN);
            if (h_magic()!=2038637673) {
                throw new IOException("Stream does not contain an FSA: Wrong file magic number " + h_magic());
            }
            _symbol_tab = file.getChannel().map(MapMode.READ_ONLY, 256, h_size());
            _symbol_tab.order(ByteOrder.LITTLE_ENDIAN);
            _state_tab = file.getChannel().map(MapMode.READ_ONLY, 256+h_size(), 4*h_size());
            _state_tab.order(ByteOrder.LITTLE_ENDIAN);
            _data = file.getChannel().map(MapMode.READ_ONLY, 256+5*h_size(), h_data_size());
            _data.order(ByteOrder.LITTLE_ENDIAN);
            if (h_has_phash()>0){
                _phash = file.getChannel().map(MapMode.READ_ONLY, 256+5*h_size()+h_data_size(), 4*h_size());
                _phash.order(ByteOrder.LITTLE_ENDIAN);
            } else {
                _phash = null;
            }
            _ok = true;
        }
        private int h_magic(){
            return _header.getInt(0);
        }
        private int h_version(){
            return _header.getInt(4);
        }
        private int h_checksum(){
            return _header.getInt(8);
        }
        private int h_size(){
            return _header.getInt(12);
        }
        private int h_start(){
            return _header.getInt(16);
        }
        private int h_data_size(){
            return _header.getInt(20);
        }
        private int h_data_type(){
            return _header.getInt(24);
        }
        private int h_fixed_data_size(){
            return _header.getInt(28);
        }
        private int h_has_phash(){
            return _header.getInt(32);
        }
        private int h_serial(){
            return _header.getInt(36);
        }
        private int hashDelta(int state, byte symbol){
            int s=symbol;
            if(s<0){
                s+=256;
            }
            if(_ok && h_has_phash()==1 && s>0 && s<255){
                if(getSymbol(state+s)==s){
                    return _phash.getInt(4*(state+s));
                }
            }
            return 0;
        }
        private int delta(int state, byte symbol){
            int s=symbol;
            if(s<0){
                s+=256;
            }
            if(_ok && s>0 && s<255){
                if(getSymbol(state+s)==s){
                    return _state_tab.getInt(4*(state+s));
                }
            }
            return 0;
        }

        private int getSymbol(int index){
            int symbol = _symbol_tab.get(index);
            if(symbol<0){
                symbol += 256;
            }
            return symbol;
        }
        private boolean isFinal(int state){
            return _ok && (getSymbol(state+255)==255);
        }
        private static void clean(MappedByteBuffer mmap) {
            if ((mmap == null) || !mmap.isDirect()) return;

            try {
                Class<?> unsafeClass;
                try {
                    unsafeClass = Class.forName("sun.misc.Unsafe");
                } catch (Exception ex) {
                    // jdk.internal.misc.Unsafe doesn't yet have an invokeCleaner() method,
                    // but that method should be added if sun.misc.Unsafe is removed.
                    unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
                }
                Method clean = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
                clean.setAccessible(true);
                Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                Object theUnsafe = theUnsafeField.get(null);
                clean.invoke(theUnsafe, mmap);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed unmapping ", e);
            }
        }
        @Override
        public void close() {
            clean(_header);
            clean(_data);
            clean(_phash);
            clean(_state_tab);
            clean(_symbol_tab);
        }

        private final MappedByteBuffer _header;
        private final MappedByteBuffer _symbol_tab;
        private final MappedByteBuffer _state_tab;
        private final MappedByteBuffer _data;
        private final MappedByteBuffer _phash;
        private final boolean _ok;
    }
    private final boolean _ok;
    private final Charset _charset;
    private final AtomicReference<Maps> maps = new AtomicReference<>();


    /**
     * Loads an FSA from a resource file name, which is resolved from the class path of the
     * class loader of the given class.
     * <p>
     * This is useful for loading fsa's deployed within OSGi bundles.
     *
     * @param  resourceFileName the name of the file, relative to any element on the classpath.
     *         For example, if the classpath contains resources/ and the file is resources/myfsa.fsa
     *         this argument should be myfsa.fsa
     * @param  loadingClass a class which provides the class loader to use for loading. Any class which is loaded
     *         from the same class path as the resource will do (e.g with OSGi - any class in the same bundle jar)
     * @return the loaded FSA
     * @throws RuntimeException if the class could not be loaded
     */
    public static FSA loadFromResource(String resourceFileName, Class<?> loadingClass) {
        URL fsaUrl = loadingClass.getResource(resourceFileName);
        if ( ! "file".equals(fsaUrl.getProtocol())) {
            throw new RuntimeException("Could not open non-file url '" + fsaUrl + "' as a file input stream: " +
                    "The classloader of " + loadingClass + "' does not return file urls");
        }
        return new FSA(fsaUrl.getFile());
    }

    private static FileInputStream createInputStream(String filename) {
        try {
            return new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Could not find FSA file '" + filename + "'",e);
        }
    }

    /**
     * Loads an FSA from a file using utf-8 encoding
     *
     * @throws IllegalArgumentException if the file is not found
     */
    public FSA(String filename) {
        this(filename,"utf-8");
    }

    /**
     * Loads an FSA from a file using the specified character encoding.
     *
     * @throws IllegalArgumentException if the file is not found
     */
    public FSA(String filename, String charsetname) {
        this(createInputStream(filename), charsetname, true);
    }

    /** Loads an FSA from a file input stream using utf-8 encoding */
    public FSA(FileInputStream file) {
        this(file,"utf-8");
    }

    public FSA(FileInputStream file, String charsetname) {
        this(file, charsetname, false);
    }
    /** Loads an FSA from a file input stream using the specified character encoding */
    private FSA(FileInputStream file, String charsetname, boolean closeInput) {
        try {
            _charset = Charset.forName(charsetname);
            maps.set(new Maps(file));
            _ok=true;
        }
        catch (IOException e) {
            throw new RuntimeException("IO error while reading FSA file",e);
        } finally {
            if (closeInput) {
                try {
                    file.close();
                } catch (IOException e) {
                    // Do nothing
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        Maps m = map();
        maps.set(null);
        m.close();
    }

    private Maps map() { return maps.get(); }

    private ByteBuffer encode(String str){
        return _charset.encode(str);
    }

    private ByteBuffer encode(CharBuffer chrbuf){
        return _charset.encode(chrbuf);
    }

    private String decode(ByteBuffer buf){
        return _charset.decode(buf).toString();
    }

    public boolean isOk(){
        return _ok;
    }

    public boolean hasPerfectHash(){
        return _ok && map().h_has_phash()==1;
    }

    public int version(){
        if(_ok){
            return map().h_version();
        }
        return 0;
    }

    public int serial(){
        if(_ok){
            return map().h_serial();
        }
        return 0;
    }

    protected int start(){
        if(_ok){
            return map().h_start();
        }

        return 0;
    }

    protected int delta(int state, byte symbol){
        return map().delta(state, symbol);
    }

    protected int hashDelta(int state, byte symbol){
        return map().hashDelta(state, symbol);
    }

    protected boolean isFinal(int state){
        return (_ok && map().isFinal(state));
    }

    /**
     * Retrieves data for the given state using the underlying fsa data buffer.
     * @param state The fsa state to retrieve data from.
     * @return A new buffer containing the data for the given state.
     **/
    protected ByteBuffer data(int state) {
        Maps m = maps.get();
        if(_ok && m.isFinal(state)){
            int offset = m._state_tab.getInt(4*(state+255));
            int length;
            if(m.h_data_type()==1){
                length = m.h_fixed_data_size();
            }
            else{
                length = m._data.getInt(offset);
                offset += 4;
            }
            ByteBuffer meta = ByteBuffer.allocate(length);
            meta.order(ByteOrder.LITTLE_ENDIAN);
            byte[] dst = meta.array();
            for (int i = 0; i < length; ++i) {
                dst[i] = m._data.get(i + offset);
            }
            return meta;
        }
        return null;
    }

    /**
     * Retrieves data for the given state using the underlying fsa data buffer.
     * @param state The fsa state to retrieve data from.
     * @return A string representation of the data for the given state.
     **/
    protected String dataString(int state) {
        ByteBuffer meta = data(state);
        if(meta!=null){
            // Remove trailing '\0' if it exists. This is usually the
            // case for automata built with text format (makefsa -t)
            String data = decode(meta);
            if (data.endsWith("\0")) {
                data = data.substring(0, data.length()-1);
            }
            return data;
        }
        return null;
    }

    /**
     * Convenience method that returns the metadata string in the fsa
     * for the input lookup String, or null if the input string does
     * not exist in the fsa.
     * @param str The string to look up.
     * @return Metadata string from the fsa.  */
    public String lookup(String str){
        State s = getState();
        s.lookup(str);
        return s.dataString();
    }


    //// test ////
    public static void main(String[] args) {
        String test = "sour cherry";
        if (args.length >= 1) {
            test = args[0];
        }

        String fsafile = "/home/gv/fsa/test/__testfsa__.__fsa__";
        //String fsafile = "/home/p13n/prelude/automata/query2dmozsegments.fsa";

        FSA fsa = new FSA(fsafile);

        System.out.println("Loading FSA file "+fsafile+": "+fsa.isOk());
        System.out.println("    version: " + fsa.version()/1000000 + "." +
                           (fsa.version()/1000) % 1000 + "." +
                           fsa.version() % 1000);
        System.out.println("    serial:  " + fsa.serial());
        System.out.println("    phash:   " + fsa.hasPerfectHash());

        FSA.State s = fsa.getState();

        s.start();
        for (int i=0; i < test.length(); i++) {
            s.delta(test.charAt(i));
        }
        System.out.println("\ndelta() char test " + test + ": " +
                           s.isFinal() + ", info: " + s.dataString() +
                           ", hash value: " + s.hash());

        s.start();
        s.delta(test);
        System.out.println("\ndelta() test " + test + ": " +
                           s.isFinal() + ", info: " + s.dataString() +
                           ", hash value: " + s.hash());

        s.lookup(test);
        String data =  s.dataString();
        System.out.println("\nlookup() test \"" + test + "\": " +
                           (s.lookup(test) != null) +
                           ", info: " + data + ", hash value: " + s.hash());

        String data2 = fsa.lookup(test);
        System.out.println("\nFSA.lookup() test \"" + test + "\": " + data2);
    }
}


