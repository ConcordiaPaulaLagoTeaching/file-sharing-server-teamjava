
package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode; // needed for creating and managing blocks

import java.io.File; // needed for checking if a file exists
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock; // needed for different read/write locks, better concurrency

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    // singleton instance no longer needed, this implementation is better for multithreading

    private final RandomAccessFile disk;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(); // for concurent reads and exclusive writes

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private FNode[] fnodeTable; // array of data blocks, for storage
    private boolean[] freeBlockList; // Bitmap for free blocks

    private int firstDataBlock; // for tracking where data blocks start

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        try{
            // first: calculate metadata size and keep track of where data blocks start
            int fnodeBytes = MAXBLOCKS * 8;
            int fentryBytes = MAXFILES * 15;
            int metadataBytes = fentryBytes + fnodeBytes;
            this.firstDataBlock = (metadataBytes + BLOCK_SIZE - 1) / BLOCK_SIZE; // calculate index of first data block

            // next: open or create disk file
            File diskFile = new File(filename); // the disk file

            if (diskFile.exists()) {
                disk = new RandomAccessFile(diskFile, "rw"); // if file exists call function to populate memory
                initializeFileSystem();
            } else {
                disk = new RandomAccessFile(diskFile, "rw"); // create new file
                disk.setLength(totalSize); // set the size of the disk file
                initializeFileSystem(); // initialize file system
            }
        }
            catch (Exception e) {
                throw new RuntimeException("Failed to initialize file system", e);
            }
    }
    
    private void initializeFileSystem() throws Exception {
        // initialize inode table, fnode table and free block list
        inodeTable = new FEntry[MAXFILES];
        fnodeTable = new FNode[MAXBLOCKS];
        freeBlockList = new boolean[MAXBLOCKS];

        // for each index, create a Fnode object (Fnodes represent disk block) 
        for (int i = 0; i < MAXBLOCKS; i++) {
            fnodeTable[i] = new FNode(i);
        }

        // reserve metadata blocks to preserve then from being allocated to files
        for (int i = 0; i < firstDataBlock; i++) {
            freeBlockList[i] = true; 
        }

        // all blocks after the metadata blocks are free to be used for file data, so mark them as free
        for (int i = firstDataBlock; i < MAXBLOCKS; i++) {
            freeBlockList[i] = false;
        }

        // print initialization complete
        System.out.println("File system initialized. Metadata size: " + (MAXFILES * 15 + MAXBLOCKS * 8) + " bytes.");      
    }
    //this function creates an empty file if possible
    public void createFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

    // writeFile replaces the entire contents of an existing file.
    // This operation must be atomic: if anything fails, the file must remain unchanged.

    // helper: finds a file entry in the inode table by filename
    private FEntry findFile(String filename) {
        for (int i = 0; i < MAXFILES; i++) {
            if (inodeTable[i] != null && inodeTable[i].isInUse() 
                && inodeTable[i].getFilename().equals(filename)) {
                return inodeTable[i];
            }
        }
        return null;
    }
    // helper: returns a list of all block indices belonging to this file, by following FNode.next
    private java.util.List<Integer> getBlocksForFile(FEntry entry) {
        java.util.List<Integer> blocks = new java.util.ArrayList<>();

        int block = entry.getFirstBlock();
        while (block != -1) {
            blocks.add(block);
            block = fnodeTable[block].getNext();
        }
        return blocks;
    }
    // helper: allocate a certain number of free blocks and mark them as used
    private java.util.List<Integer> allocateBlocks(int blocksNeeded) throws Exception {
        java.util.List<Integer> allocated = new java.util.ArrayList<>();

        for (int i = firstDataBlock; i < MAXBLOCKS && allocated.size() < blocksNeeded; i++) {
            if (!freeBlockList[i]) {          // false = free
                freeBlockList[i] = true;      // mark allocated
                allocated.add(i);
            }
        }

        // if not enough blocks → undo allocation + fail
        if (allocated.size() < blocksNeeded) {
            for (int blockIndex : allocated) {
                freeBlockList[blockIndex] = false;
            }
            throw new Exception("ERROR: Not enough free blocks available.");
        }

        return allocated;
    }


    public void writeFile(String fileName, byte[] contents) throws Exception {

        // request exclusive write lock (no readers or writers allowed during this update)
        rwLock.writeLock().lock(); 

        try {
            // find the file in inode table
            FEntry fileEntry = findFile(fileName);
            if (fileEntry == null || !fileEntry.isInUse()) {
                throw new Exception("ERROR: File " + fileName + " does not exist.");
            }

            // determine how many blocks are required for the new contents
            int newSize = contents.length;
            int blocksNeeded = (newSize + BLOCK_SIZE - 1) / BLOCK_SIZE; // round up

            // save old state for rollback (important for atomicity)
            short originalFirstBlock = fileEntry.getFirstBlock();
            short originalFileSize = fileEntry.getFilesize();
            var originalBlockList = getBlocksForFile(fileEntry); // chain of blocks currently used

            // this list will store *newly allocated* blocks
            var newBlockList = new java.util.ArrayList<Integer>();

            try {
                // free old blocks temporarily (mark as free, break links)
                // we do not permanently lose them yet because rollback below can restore them.
                for (int blockIndex : originalBlockList) {
                    freeBlockList[blockIndex] = false;     // false means "free"
                    fnodeTable[blockIndex].setNext(-1);    // unlink
                }

                // if the new content is empty, the file becomes empty → update metadata and stop
                if (blocksNeeded == 0) {
                    fileEntry.setFirstBlock((short) -1);
                    fileEntry.setFilesize((short) 0);
                    return;
                }

                // allocate new blocks
                newBlockList = (ArrayList<Integer>) allocateBlocks(blocksNeeded);

                // write new data into the allocated blocks
                for (int i = 0; i < newBlockList.size(); i++) {
                    int blockIndex = newBlockList.get(i);

                    // compute how much data goes in this block
                    int startOffset = i * BLOCK_SIZE;
                    int length = Math.min(BLOCK_SIZE, newSize - startOffset);

                    // prepare block buffer
                    byte[] blockData = new byte[BLOCK_SIZE];
                    System.arraycopy(contents, startOffset, blockData, 0, length);

                    // write to disk at correct block position
                    disk.seek((long) blockIndex * BLOCK_SIZE);
                    disk.write(blockData);
                }

                // link allocated blocks together using FNode.next (forming a linked list)
                for (int i = 0; i < newBlockList.size() - 1; i++) {
                    fnodeTable[newBlockList.get(i)].setNext(newBlockList.get(i + 1));
                }
                fnodeTable[newBlockList.get(newBlockList.size() - 1)].setNext(-1); // last block points to none

                // commit update to inode (this is the "no turning back" moment)
                fileEntry.setFirstBlock(newBlockList.get(0).shortValue());
                fileEntry.setFilesize((short) newSize);

            } catch (Exception e) {
                // undo everything if any step failed

                // unallocate any new blocks
                for (int blockIndex : newBlockList) {
                    freeBlockList[blockIndex] = false;
                    fnodeTable[blockIndex].setNext(-1);
                }

                // restore old blocks
                for (int blockIndex : originalBlockList) {
                    freeBlockList[blockIndex] = true;
                }

                // restore inode metadata
                fileEntry.setFirstBlock(originalFirstBlock);
                fileEntry.setFilesize(originalFileSize);

                throw e; // rethrow after rollback
            }

        } finally {
            // release lock no matter what
            rwLock.writeLock().unlock();
        }
    }


}