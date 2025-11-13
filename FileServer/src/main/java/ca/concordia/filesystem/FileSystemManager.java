package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemManager {

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static final int METADATA_SIZE = 90; // 80 bytes for inode table + 10 bytes for free block list
    private static FileSystemManager instance = null;
    private final RandomAccessFile disk;

    private static final int BLOCK_SIZE = 128; // Example block size

    private final FEntry[] inodeTable; // Array of inodes
    private final boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) throws IOException {
        if (instance != null) {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

        instance = this; // set the singleton instance
        this.disk = new RandomAccessFile(filename, "rw");
        this.inodeTable = new FEntry[MAXFILES];
        this.freeBlockList = new boolean[MAXBLOCKS];
        
        if (disk.length() == 0) {
            // if new disk initialize and save
            initializeFileSystem(totalSize);
            saveMetadata(); // save initial state
        } else {
            // else: existing disk, reload saved metadata
            loadMetadata();
        }
    }

    // initialize the file system structures 
    private void initializeFileSystem(int totalSize) throws IOException {
        // mark all blocks as free
        for (int i = 0; i < MAXBLOCKS; i++) {
            freeBlockList[i] = true;  // false = used, true = free
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
        rwLock.writeLock().lock();
        try {
            // check filename length
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

            if (freeEntry == null) { // add this to handle case if user creates more than 5 files
                throw new Exception("ERROR: Maximum number of files reached. Delete a file before creating a new one.");
            }

            // initialize the new file 
            if (freeEntry != null) { //Unnecessary null check but just in case
                freeEntry.setFilename(fileName);
                freeEntry.setFilesize((short) 0);
                freeEntry.setFirstBlock((short) -1); // No blocks assigned yet
                freeEntry.setInUse(true);
                saveMetadata(); // persist changes
            }
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        // Deletes existing file by overwriting data with zeros
    public void deleteFile(String fileName) throws Exception {
        rwLock.writeLock().lock();
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
            saveMetadata(); // persist changes

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // List all files in use
    public String[] listFiles() {
        rwLock.readLock().lock();
        try {
            List<String> fileList = new ArrayList<>();
            for (FEntry entry : inodeTable) {
                if (entry.isInUse()) {
                    fileList.add(entry.getFilename());
                }
            }
            /* 
            //  debugging
            System.out.println("Free block list:");
            for (int i = 0; i < freeBlockList.length; i++) {
            System.out.println("Block " + i + ": " + freeBlockList[i]);
            }
            */
            return fileList.toArray(new String[0]);

        } finally {
            rwLock.readLock().unlock();
        }
    }

    // method to write file
    public void writeFile(String fileName, byte[] contents) throws Exception {
        rwLock.writeLock().lock();
        try {

            /* debugging
             * Thread.sleep(5000);
             */

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
    
            disk.seek(METADATA_SIZE + (long) newBlock * BLOCK_SIZE); // seek to block position
            disk.write(blockData); //now that we have seeked, write the data
    
            // update FEntry
            fileEntry.setFirstBlock((short) newBlock);
            fileEntry.setFilesize((short) contents.length);

            saveMetadata();
    
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    public byte[] readFile(String fileName) throws Exception {
        rwLock.readLock().lock(); // aquire lock
        try {

            /* 
            * debugging
            * Thread.sleep(5000);
            */

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
            disk.seek(METADATA_SIZE + (long) blockIndex * BLOCK_SIZE);
            disk.read(data, 0, size);
    
            return data;
    
        } finally {
            rwLock.readLock().unlock();
        }
    }
    

    // Overwrite block with zeros
    private void zeroOutBlock(int blockIndex) throws IOException {
        byte[] zeros = new byte[BLOCK_SIZE];
        long offset = (long) blockIndex * BLOCK_SIZE;
        disk.seek(offset);
        disk.write(zeros);
    }

    // SAVING STATE METHODS
    // helper to read fixed size string from disk file
    private String readFixedString(int length) throws IOException {
        // must read exactly 11 bytes
        byte[] data = new byte[length];
        disk.readFully(data);     
        return new String(data).trim();
    }

    void saveMetadata() throws IOException {
        rwLock.writeLock().lock();
        try {
            // save inode table
            disk.seek(0);

            for (FEntry entry : inodeTable) {
                String name = entry.getFilename();
                if (name == null) {
                    name = "";
                }
                if (name.length() < 11){
                    name = String.format("%-11s", name); // pad with spaces   
                } 
                disk.writeBytes(name); //write bytes, helper no longer needed
                disk.writeShort(entry.getFilesize());
                disk.writeShort(entry.getFirstBlock());
                disk.writeBoolean(entry.isInUse());
            }
            // save free block list
            for (boolean isFree : freeBlockList) {
                disk.writeBoolean(isFree);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void loadMetadata() throws IOException {
        rwLock.writeLock().lock();
        try {
            disk.seek(0); // seek to start

            for (int i = 0; i < MAXFILES; i++) {
                String name = readFixedString(11).trim();
                short size = disk.readShort();
                short firstBlock = disk.readShort();
                boolean inUse = disk.readBoolean();
                FEntry e = new FEntry();
                e.setFilename(name);
                e.setFilesize(size);
                e.setFirstBlock(firstBlock);
                e.setInUse(inUse);
                inodeTable[i] = e;
            }

            for (int i = 0; i < MAXBLOCKS; i++) {
                freeBlockList[i] = disk.readBoolean();
            }

        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
