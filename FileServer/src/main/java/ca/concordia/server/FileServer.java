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


    // clienHandler now handles each client connection
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port + "...");
    
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket);
    
                // we just create a thread for each client here
                Thread clientThread = new Thread(new ClientHandler(clientSocket, fsManager));
                clientThread.start();
            }
    
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }
    

}
