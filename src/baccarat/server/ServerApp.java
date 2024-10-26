
// check if amount is sufficient under deal
// synchronised, volatile -- prevent race conditions
    //  -> reading the results - multi-threaded, writing the result - single thread

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerApp {
    // first arg = port number
    // second arg = no. of decks of cards
    // private static int numDeck;
    // private String username;

    public static List<String> generateCards(int numDeck){
        // create the shuffled deck of cards
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < numDeck; i++){
            for (int v = 1; v < 14; v++){
                // 11 - Joker, 12 - Queen, 13 - King
                for (int s = 1; s < 5; s++){
                    cards.add(Integer.toString(v) + "." + Integer.toString(s));
                }
            }
        }
        Collections.shuffle(cards);
        
        // create a "cards.db" database and save the shuffled cards
        try{
            File file = new File("cards.db");
            FileWriter fw = new FileWriter(file, false);
            BufferedWriter bw = new BufferedWriter(fw);

            for (String s : cards){
                bw.write(s);
                bw.newLine();
            }
            bw.flush();
            bw.close();
            fw.close();
        } catch (IOException e){
            System.out.println("Error writing to cards.db: " + e.getMessage());
        }

        return cards;
    }

    public static void main(String[] args) {
        
        // ensure that application can accept two arguments
        if (args.length < 2){
            System.out.println("Usage: java -cp classes baccarat.server.ServerApp <port number> <deck>");
            System.exit(0);
        }

        int port;
        int numDeck;
        
        // check that input must be able to be parsed -- no special characters
        try{
            port = Integer.parseInt(args[0]);
            numDeck = Integer.parseInt(args[1]);
            generateCards(numDeck);
        } catch (NumberFormatException e){
            System.out.println("Invalid arguments. Port and number of decks must be integers");
            return;
        }

    
        // reset game history on server restart
        resetGameHistory();

        // start the server with a thread pool
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        String threadName = Thread.currentThread().getName();
        try{
            ServerSocket server = new ServerSocket(port);

            while (true){
                System.out.printf("[%s] Waiting for connection on port %d\n", threadName, port);
                Socket sock = server.accept();

                System.out.println("Got a new connection");

                BaccaratEngine handler = new BaccaratEngine(sock);

                executorService.execute(handler);

            }


            } catch (IOException e){
                System.out.println("Error starting server: " + e.getMessage());
            } finally {
            executorService.shutdown();
            }

    }

    private static void resetGameHistory(){
        try{
            File file = new File("game_history.csv");
            FileWriter fw = new FileWriter(file, false);
            // opening the file in overwrite mode without writing anything will empty it
            fw.write("");
            fw.flush();
            fw.close();
            System.out.println("Game history has been reset");
        } catch (IOException e){
            System.out.println("Error resetting game history: " + e.getMessage());
        }
    }

}


