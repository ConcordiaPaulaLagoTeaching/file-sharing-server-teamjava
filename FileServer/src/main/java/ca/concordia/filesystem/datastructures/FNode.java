//Represents each block of data belonging to a file (like FAT filesystem)
package ca.concordia.filesystem.datastructures;

public class FNode {

    private int blockIndex;

    public FNode(int blockIndex) {
        this.blockIndex = blockIndex;
    }
    
    // Getters 

    //getBlock to know where on disk to read from 
    public int getBlockIndex() {
        return blockIndex;
    }

    // Setters

    //setBlockIndex to track which blocks are available (free or used)
    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }
}