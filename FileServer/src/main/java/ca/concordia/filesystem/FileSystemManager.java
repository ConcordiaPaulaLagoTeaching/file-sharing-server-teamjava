
package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode; // needed for creating and managing blocks

import java.io.File; // needed for checking if a file exists
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

public class FileSystemManager {

    private final ReentrantLock globalLock = new ReentrantLock();

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(); // for concurent reads and exclusive writes

    private static final int BLOCK_SIZE = 128; // Example block size

    private final FEntry[] inodeTable; // Array of inodes
    private final boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) throws IOException {
        if (instance != null) {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

        instance = this;
        this.disk = new RandomAccessFile(filename, "rw");
        this.inodeTable = new FEntry[MAXFILES];
        this.freeBlockList = new boolean[MAXBLOCKS];
        // initialize file system
        initializeFileSystem(totalSize);
    }

    // initialize the file system structures 
    private void initializeFileSystem(int totalSize) throws IOException {
        // mark all blocks as free
        for (int i = 0; i < MAXBLOCKS; i++) {
            freeBlockList[i] = true;
        }

        // initialize file entries
        for (int i = 0; i < MAXFILES; i++) {
            inodeTable[i] = new FEntry();
        }

        // Set the file size
        if (disk.length() < totalSize) {
            disk.setLength(totalSize);
        }
    }

    // create a singleton empty file system
    public void createFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            if (fileName.length() > 11) {
                throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
            }
            // Check for no duplicates
            for (FEntry entry : inodeTable) {
                if (entry.isInUse() && entry.getFilename().equals(fileName)) {
                    throw new Exception("File already exists.");
                }
            }

            // Find a free FEntry
            FEntry freeEntry = null;
            for (FEntry entry : inodeTable) {
                if (!entry.isInUse()) {
                    freeEntry = entry;
                    break;
                }
            }

            // initialize the new file 
            if (freeEntry != null) { //Unnecessary null check but just in case
                freeEntry.setFilename(fileName);
                freeEntry.setFilesize((short) 0);
                freeEntry.setFirstBlock((short) -1); // No blocks assigned yet
                freeEntry.setInUse(true);
            }
            } finally {
                globalLock.unlock();
            }
        }

        // Deletes existing file by overwriting data with zeros
    public void deleteFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            FEntry targetEntry = null;
            for (FEntry entry : inodeTable) {
                if (entry.isInUse() && entry.getFilename().equals(fileName)) {
                    targetEntry = entry;
                    break;
                }
            }

            if (targetEntry == null) {
                throw new IllegalArgumentException("File " + fileName + " not found.");
            }

            // Free up blocks
            int blockIndex = targetEntry.getFirstBlock();
            if (blockIndex >= 0 && blockIndex < MAXBLOCKS) {
                try {
                    zeroOutBlock(blockIndex); // this throws IOException
                } catch (IOException e) { // handle it
                    throw new RuntimeException("Failed to zero out block " + blockIndex, e);
                }
                freeBlockList[blockIndex] = true;
        }

            // reset data
            targetEntry.clear();

        } finally {
            globalLock.unlock();
        }
    }

    // List all files in use
    public String[] listFiles() {
        globalLock.lock();
        try {
            List<String> fileList = new ArrayList<>();
            for (FEntry entry : inodeTable) {
                if (entry.isInUse()) {
                    fileList.add(entry.getFilename());
                }
            }
            return fileList.toArray(new String[0]);
        } finally {
            globalLock.unlock();
        }
    }

    // method to write file
    public void writeFile(String fileName, byte[] contents) throws Exception {
        globalLock.lock();
        try {
            // find the file
            FEntry fileEntry = null;
            for (FEntry entry : inodeTable) {
                if (entry.isInUse() && entry.getFilename().equals(fileName)) {
                    fileEntry = entry; // found it
                    break;
                }
            }
            if (fileEntry == null) {
                throw new Exception("ERROR: File " + fileName + " does not exist.");
            }
    
            // make sure file size is within limits
            if (contents.length > BLOCK_SIZE) {
                throw new Exception("ERROR: File too large. Max size is " + BLOCK_SIZE + " bytes.");
            }
    
            // free old block if it exists
            int oldBlock = fileEntry.getFirstBlock();
            if (oldBlock >= 0) {
                freeBlockList[oldBlock] = true; // mark old block free
            }
    
            // find a new free block
            int newBlock = -1;
            for (int i = 0; i < MAXBLOCKS; i++) {
                if (freeBlockList[i]) { // true = free
                    newBlock = i;
                    freeBlockList[i] = false; // mark allocated
                    break;
                }
            }
            if (newBlock == -1) {
                throw new Exception("ERROR: No free blocks available.");
            }
    
            // write contents to the new block
            byte[] blockData = new byte[BLOCK_SIZE];
            System.arraycopy(contents, 0, blockData, 0, contents.length);
    
            disk.seek((long) newBlock * BLOCK_SIZE);
            disk.write(blockData);
    
            // update FEntry
            fileEntry.setFirstBlock((short) newBlock);
            fileEntry.setFilesize((short) contents.length);
    
        } finally {
            globalLock.unlock();
        }
    }
    
    public byte[] readFile(String fileName) throws Exception {
        globalLock.lock(); // aquire lock
        try {
            // find file in inode table
            FEntry fileEntry = null;
            for (FEntry entry : inodeTable) {
                if (entry.isInUse() && entry.getFilename().equals(fileName)) {
                    fileEntry = entry;
                    break;
                }
            }
            if (fileEntry == null) {
                throw new Exception("ERROR: File " + fileName + " does not exist.");
            }
    
            // check if file has data
            int blockIndex = fileEntry.getFirstBlock();
            if (blockIndex < 0 || blockIndex >= MAXBLOCKS || freeBlockList[blockIndex]) {
                throw new Exception("ERROR: File " + fileName + " has no data stored.");
            }
    
            // determine how many bytes to read
            int size = fileEntry.getFilesize();
            byte[] data = new byte[size];
    
            // read data from disk
            disk.seek((long) blockIndex * BLOCK_SIZE);
            disk.read(data, 0, size);
    
            return data;
    
        } finally {
            globalLock.unlock();
        }
    }
    

    // Overwrite block with zeros
    private void zeroOutBlock(int blockIndex) throws IOException {
        byte[] zeros = new byte[BLOCK_SIZE];
        long offset = (long) blockIndex * BLOCK_SIZE;
        disk.seek(offset);
        disk.write(zeros);
    }
}

