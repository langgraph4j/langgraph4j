package org.bsc.langgraph4j.serializer;

import java.io.*;

/**
 * Extended ObjectInputStream that supports readUTF for strings larger than 65k bytes.
 * This implementation can read strings written by ExtendedObjectOutputStream.
 */
public class ExtendedObjectInputStream extends ObjectInputStream {

    public ExtendedObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    /**
     * Reads a string in UTF format, automatically detecting whether it was written
     * using standard writeUTF or extended writeUTF.
     * 
     * @return the string that was read
     * @throws IOException if an I/O error occurs
     */
    public String readUTF() throws IOException {
        long length = readLong();
        if (length == -1) {
            return null;
        }
        
        byte[] utfBytes = new byte[(int) length];
        readFully(utfBytes);
        return new String(utfBytes, "UTF-8");
    }
}
