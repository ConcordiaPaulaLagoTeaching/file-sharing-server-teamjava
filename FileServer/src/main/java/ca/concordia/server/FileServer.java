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
                                fsManager.createFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                writer.flush();
                                break;
                            case "WRITE":
                                String dataToWrite = line.substring(line.indexOf(parts[2]));
                                fsManager.writeFile(parts[1], dataToWrite.getBytes());
                                writer.println("SUCCESS: Written to file '" + parts[1] + "'.");
                                writer.flush();
                                break;
                            case "READ":
                                byte[] data = fsManager.readFile(parts[1]);
                                writer.println("DATA: " + new String(data));
                                writer.flush();
                                break;
                            case "LIST": // List commmand wasnt implemented yet
                                String[] files = fsManager.listFiles();
                                writer.println("FILES: " + String.join(", ", files));
                                writer.flush();
                                break;
                            case "DELETE": // delete command wasnt implemented yet
                                fsManager.deleteFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                                writer.flush();
                                break;
                            case "QUIT":
                                writer.println("SUCCESS: Disconnecting.");
                                return;
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
