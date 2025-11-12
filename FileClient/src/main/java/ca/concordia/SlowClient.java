// Simulates a slow client to test multithreading and concurrency in the server.

package ca.concordia;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SlowClient {

    public static void main(String[] args) {
        try {
            System.out.println("Connecting to server...");
            Socket socket = new Socket("localhost", 12345);
            System.out.println("Connected. Waiting for 60 seconds. Please Wait...");

            Thread.sleep(60000); 

            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            writer.println("LIST");
            System.out.println("Sent LIST. Waiting for response...");

            String response = reader.readLine();
            System.out.println("Server responded: " + response);

            writer.println("QUIT");

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
