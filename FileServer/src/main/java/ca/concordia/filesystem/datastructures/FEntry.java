// Fentry rpresents one file (name, size, pointer to first block)

package ca.concordia.filesystem.datastructures;

public class FEntry {

    private String filename;
    private short filesize;
    private short firstBlock; // Pointers to data blocks
    private boolean inUse; // Indicates if this FEntry is in use


    //default constructor creates an empty FEntry
    public FEntry() {
        this.filename = "";
        this.filesize = 0;
        this.firstBlock = -1; // -1 indicates no blocks assigned
        this.inUse = false;
    }

    //parameterized constructor
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

    public short getFirstBlock() {
        return firstBlock;
    }

    public void setFirstBlock(short firstBlock) {
        this.firstBlock = firstBlock;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    //clear the FEntry
    public void clear() {
        this.filename = "";
        this.filesize = 0;
        this.firstBlock = -1;
        this.inUse = false; 
    }
}
