package baccarat.client;

import java.io.*;
import java.net.*;

public class ClientApp {

    public static void menu(){
        System.out.println("Enter one of the following commands:\n");
        System.out.println("Login <username> <balance>\n");
        System.out.println("Bet <betamount> <username>\n");
        System.out.println("Deal B/P <betamount> <username>\n");
        System.out.println("Exit");
    }
    public static void main(String[] args) {
        
        if (args.length < 1){
            System.out.println("Usage: client.ClientApp <server_address>:<port>");
            System.exit(0);
        }

        System.out.println("Connecting to the server");

        String[] input = args[0].split(":");
        String serverAddress = input[0];
        int port;

        try {
            port = Integer.parseInt(input[1]);
        } catch (NumberFormatException e){
            System.out.println("Invalid port number.");
            return;
        }

        try{
            Socket sock = new Socket(serverAddress, port);
            System.out.println("Connected!");

            // output stream
            OutputStream os = sock.getOutputStream();
            Writer writer = new OutputStreamWriter(os);
            BufferedWriter bw = new BufferedWriter(writer);

            // input stream
            InputStream is = sock.getInputStream();
            Reader reader = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(reader);

            // enable user input for commands
            Console cons = System.console();
            menu();
            String command = cons.readLine(">>> ");

            // write command to server
            command = command.replaceAll(" ", "|");
            bw.write(command);
            bw.newLine();
            bw.flush();
            os.flush();

            // read from server
            String serverResponse;
            System.out.println(">>> SERVER: ");
            while ((serverResponse = br.readLine()) != null){
                System.out.printf("%s\n", serverResponse);
            }
            
        } catch (IOException e){
            System.out.println("Error connecting to server: " + e.getMessage());
        }
    }
}
