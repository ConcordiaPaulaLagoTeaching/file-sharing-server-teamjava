package ca.concordia.server;
import java.io.*;
import java.net.Socket;
import ca.concordia.filesystem.FileSystemManager;

public class ClientHandler implements Runnable { // runnable for threading

    private final Socket clientSocket;
    private final FileSystemManager fsManager;

    // constructor
    public ClientHandler(Socket clientSocket, FileSystemManager fsManager) {
        this.clientSocket = clientSocket;
        this.fsManager = fsManager;
    }

    @Override //override run method for threading
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Received from client: " + line);

                // split the command safely 
                String[] parts = line.trim().split("\\s+");
                String command = parts[0].toUpperCase();

                switch (command) {
                    case "CREATE":
                        try {
                            fsManager.createFile(parts[1]); //parts[1] is filename {FILE1, FILE2, etc}
                            writer.println("SUCCESS: File created.");
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        writer.flush(); // send data immediately with flush
                        break; 

                    case "WRITE":
                        if (parts.length < 3) { // handle incorrect, command format: WRITE + filename + data
                            writer.println("ERROR: Command must look like: WRITE <filename> <data>");
                            writer.flush();
                            break;
                        }
                    
                        String writeFileName = parts[1];
                    
                        // reconstruct content including spaces
                        StringBuilder sbWrite = new StringBuilder();
                        for (int i = 2; i < parts.length; i++) {
                            sbWrite.append(parts[i]);
                            if (i < parts.length - 1) sbWrite.append(" "); 
                        }
                        byte[] writeData = sbWrite.toString().getBytes();
                    
                        try {
                            fsManager.writeFile(writeFileName, writeData);
                            writer.println("SUCCESS: File written.");
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        writer.flush();
                        break;

                    case "READ": // added error handling
                        try {
                            byte[] data = fsManager.readFile(parts[1]);
                            writer.println("FILE CONTENTS: " + new String(data));
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        writer.flush();
                        break;  
                        
                    case "LIST": 
                        String[] fileList = fsManager.listFiles();
                        writer.println("FILES: " + String.join(", ", fileList));
                        writer.flush();
                        break;
                        
                    case "DELETE":
                        try {
                            fsManager.deleteFile(parts[1]);
                            writer.println("SUCCESS: File deleted.");
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        writer.flush();
                        break;

                    case "QUIT":
                        try{
                            writer.println("Connection closing.");
                            writer.flush();
                            clientSocket.close();
                            System.out.println("Client disconnected.");
                            return;
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }

                    default:
                        writer.println("ERROR: Unknown command.");
                        break;
                }
            }

        } catch (IOException e) {
            System.out.println("Client disconnected.");
        }
    }
}
