// Fentry rpresents one file (name, size, pointer to first block)

package ca.concordia.filesystem.datastructures;

import java.util.LinkedList;

public class FEntry {

    private String filename;
    private short filesize;
    private short firstBlock; // Pointers to data blocks
    private boolean inUse;

    public FEntry() {
        this.filename = "";
        this.filesize = 0;
        this.firstBlock = -1;
        this.inUse = false;
    }


    public FEntry(String filename, short filesize, short firstblock) throws IllegalArgumentException{
        //Check filename is max 11 bytes long
        if (filename.length() > 11) {
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
        }
        this.filename = filename;
        this.filesize = filesize;
        this.firstBlock = firstblock;
        this.inUse = true;
    }

    // Getters and Setters
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        if (filename.length() > 11) {
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
        }
        this.filename = filename;
    }

    public short getFilesize() {
        return filesize;
    }

    public void setFilesize(short filesize) {
        if (filesize < 0) {
            throw new IllegalArgumentException("Filesize cannot be negative.");
        }
        this.filesize = filesize;
    }
    
    public void setFirstBlock(short firstBlock) { 
        this.firstBlock = firstBlock;
    }

    public void setInUse(boolean inUse) { 
        this.inUse = inUse;
    }

    public short getFirstBlock() {
        return firstBlock;
    }

    public void clear() { // convenience for deleteFile
        this.filename = "";
        this.filesize = 0;
        this.firstBlock = -1;
        this.inUse = false;
    }
    public boolean isInUse() {
        return inUse;
    }
}
