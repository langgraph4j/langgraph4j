package org.bsc.langgraph4j.serializer;

import java.io.*;

/**
 * Extended ObjectOutputStream that supports writeUTF for strings larger than 65k bytes.
 * The standard ObjectOutputStream.writeUTF() has a 65k byte limit, so this implementation
 * uses a custom approach to handle larger strings by writing the length as a long instead
 * of a short, and then writing the UTF bytes.
 */
public class ExtendedObjectOutputStream extends ObjectOutputStream {

    public ExtendedObjectOutputStream(OutputStream out) throws IOException {
        super(out);
    }

    /**
     * Writes a string in UTF format, automatically choosing between standard writeUTF
     * for small strings and writeExtendedUTF for large strings.
     * 
     * @param str the string to write
     * @throws IOException if an I/O error occurs
     */
    public void writeUTF(String str) throws IOException {
        if (str == null) {
            writeLong(-1);
            return;
        }
        
        byte[] utfBytes = str.getBytes("UTF-8");
        writeLong(utfBytes.length);
        write(utfBytes);
    }
}
