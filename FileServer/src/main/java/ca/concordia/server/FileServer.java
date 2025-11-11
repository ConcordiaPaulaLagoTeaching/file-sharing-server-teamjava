package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize) throws IOException{ // add IOException
        // Initialize the FileSystemManager
        FileSystemManager fsManager = new FileSystemManager(fileSystemName, totalSize );
        this.fsManager = fsManager;
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        String[] parts = line.split(" ");
                        String command = parts[0].toUpperCase();

                        switch (command) {
                            case "CREATE":
                                try {
                                    fsManager.createFile(parts[1]);
                                    writer.println("SUCCESS: File created.");
                                } catch (Exception e) {
                                    writer.println("ERROR: " + e.getMessage());
                                }
                                writer.flush();
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
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}
