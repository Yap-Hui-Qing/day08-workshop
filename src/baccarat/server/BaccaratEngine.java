import java.math.BigInteger;
import java.net.Socket;
import java.io.*;
import java.util.*;

// server logic
public class BaccaratEngine implements Runnable {
    private final Socket sock;
    private List<String> cards;
    private static volatile List<String> gameHistory = new ArrayList<>();
    // volatile: makes values written by one thread visible to other threads
    // immediately
    // multiple threads can modify or refer to data coherently

    public BaccaratEngine(Socket s) {
        sock = s;
        this.cards = loadCards();
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();

        try {
            // output stream
            OutputStream os = sock.getOutputStream();
            Writer writer = new OutputStreamWriter(os);
            BufferedWriter bw = new BufferedWriter(writer);

            // input stream
            InputStream is = sock.getInputStream();
            Reader reader = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(reader);

            BigInteger betAmount = BigInteger.ZERO;

            // read input from client
            String clientResponse = br.readLine();
            System.out.printf(">>> CLIENT: %s\n", clientResponse);
            String[] input = clientResponse.trim().split("\\|");
            String command = input[0];

            // process the user input commands
            switch (command.toLowerCase()) {
                case "login":
                    String username = input[1];
                    BigInteger balance = new BigInteger(input[2]);

                    // create a file named "kenneth.db"
                    // with the value of '100' as the content
                    File userFile = new File(username + ".db");
                    FileWriter fw = new FileWriter(userFile, false);
                    BufferedWriter bwFile = new BufferedWriter(fw);
                    bwFile.write(String.valueOf(balance));
                    bwFile.flush();

                    // write result back to client
                    bw.write("User " + username + " logged in with balance: " + balance);
                    bw.newLine();
                    bw.flush();
                    os.flush();
                    break;

                // bet <betamount> <username>
                case "bet":
                    // check if balance is sufficient
                    System.out.println(input);
                    username = input[2];
                    betAmount = new BigInteger(input[1]);
                    balance = getBalance(username);
                    if (balance.compareTo(betAmount) < 0) {
                        bw.write("Insufficient amount");
                    } else {
                        bw.write(username + " - Bet of " + betAmount + " placed.");
                    }
                    bw.newLine();
                    bw.flush();
                    os.flush();
                    break;

                // deal B <betamount> <username>
                case "deal":
                    betAmount = new BigInteger(input[2]);
                    username = input[3];
                    String side = input[1];
                    balance = getBalance(username);
                    System.out.println(betAmount);
                    if (balance.compareTo(betAmount) < 0) {
                        bw.write("Insufficient amount");
                        bw.newLine();
                    } else {
                        // "P|1|10|3,B|10|10|7 - Banker wins with 7 points"
                        String result = dealCards(side);
                        bw.write(result);
                        bw.newLine();
                        System.out.println(result);

                        if (result.contains("Not enough cards")){
                            bw.newLine();
                        } else if (result.contains("wins")) {
                            if (result.contains("Banker") && side.equals("B")) {
                                if (result.contains("6-Card Rule")){
                                    balance = balance.add(betAmount.multiply(new BigInteger("2")));
                                } else{
                                    balance = balance.add(betAmount);
                                    bw.write("Bet won. Balance updated: " + balance);
                                    bw.newLine();
                                }
                            } else if (result.contains("Player") && side.equals("P")) {
                                balance = balance.add(betAmount);
                                bw.write("Bet won. Balance updated: " + balance);
                                bw.newLine();
                            } else {
                                balance = balance.subtract(betAmount);
                                bw.write("Bet lost. Balance remains: " + balance);
                                bw.newLine();
                            }
                            
                        } else {
                            if (side.equals("D")){
                                // "Tie" bet -- both hands tie
                                // payout is 8 times
                                balance = balance.add(betAmount.multiply(new BigInteger("8")));
                                bw.write("It's a 'Tie' bet. Balance updated: " + balance);
                                bw.newLine();
                            } else {
                                bw.write("It's a draw. Bet refunded.");
                                bw.newLine();
                            }
                        }
                        System.out.println(balance);
                        updateBalance(username, balance);
                    }
                    bw.flush();
                    os.flush();
                    break;

                case "exit":
                    try{
                        bw.write("You have exited the game!");
                        bw.flush();
                        os.flush();
                    } catch (IOException e){
                        System.out.println("Error in exiting the game: " + e.getMessage());
                    } finally {
                        sock.close();
                        System.exit(1);
                    }


                default:
                    bw.write("Invalid command.");
                    bw.flush();
                    os.flush();
                    break;

            }

            os.close();
            is.close();
            sock.close();

        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
        }

    }

    // get balance from username.db file
    private BigInteger getBalance(String username) {
        try {
            FileReader reader = new FileReader(username + ".db");
            try (BufferedReader bufferedReader = new BufferedReader(reader)) {
                return new BigInteger(bufferedReader.readLine());
            }
        } catch (IOException e) {
            System.out.println("Error reading balance for user " + username + ": " + e.getMessage());
            return BigInteger.ZERO;
        }
    }

    private void updateBalance(String username, BigInteger balance) {
        try (FileWriter writer = new FileWriter(username + ".db")) {
            writer.write(String.valueOf(balance));
        } catch (IOException e) {
            System.out.println("Error updating balance for user " + username + ": " + e.getMessage());
        }
    }

    private synchronized int getCardValue(String card) {
        int idx = card.indexOf(".");
        int value = Integer.parseInt(card.substring(0, idx));
        if (value == 11 | value == 12 | value == 13) {
            value = 10;
        }
        System.out.println("#### getCardValue >>>>" + value);
        return value;
    }

    private synchronized List<String> loadCards() {
        List<String> cards = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("cards.db"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                cards.add(line);
            }
        } catch (IOException e) {
            System.out.println("Error loading cards: " + e.getMessage());
        }
        return cards;
    }

    private String dealCards(String side) {

        if (cards.size() < 4) {
            return "Not enough cards to deal.";
        }

        int playerSum = 0;
        int bankerSum = 0;
        List<String> playerCards = new ArrayList<>();
        List<String> bankerCards = new ArrayList<>();
        String result;
        int[] milestones = { 10, 20 }; // array to store milstone values

        synchronized (cards) {
            // draw initial 2 cards for player
            for (int i = 0; i < 2; i++) {
                String playerCard = cards.get(0);
                cards.remove(0);
                playerCards.add(playerCard);
                int value = getCardValue(playerCard);
                // get the total sum of the first two cards
                playerSum += value;
            }

            // draw initial 2 cards for banker
            for (int i = 0; i < 2; i++) {
                String bankerCard = cards.get(0);
                cards.remove(0);
                bankerCards.add(bankerCard);
                int value = getCardValue(bankerCard);
                // get the total sum of the first two cards
                bankerSum += value;
            }

            // implement rules for drawing the third card
            // draw third card if sum <= 15
            if (playerSum <= 15) {
                if (cards.size() < 1){
                    return "Not enough cards to deal.";
                } else{
                    String playerCard = cards.get(0);
                    playerCards.add(playerCard);
                    cards.remove(0);
                    int value = getCardValue(playerCard);
                    // get the total sum of the first two cards
                    playerSum += value;
                }
            }

            if (bankerSum <= 15) {
                if (cards.size() < 1)
                    return "Not enough cards to deal.";
                else{
                    String bankerCard = cards.get(0);
                    bankerCards.add(bankerCard);
                    cards.remove(0);
                    int value = getCardValue(bankerCard);
                    // get the total sum of the first two cards
                    bankerSum += value;
                }
            }

            // determine result
            System.out.println("playerSum > " + playerSum);
            System.out.println("bankerSum > " + bankerSum);

            if (playerSum >= 10 && playerSum < 20) {
                System.out.println("P more than 10 and less than 20");
                playerSum -= milestones[0];
            }

            if (playerSum >= 20) {
                System.out.println("P more than 20");
                playerSum -= milestones[1];
            }

            if (bankerSum >= 10 && bankerSum < 20) {
                System.out.println("B more than 10 and less than 20");
                bankerSum -= milestones[0];
            }

            if (bankerSum >= 20) {
                System.out.println("B more than 20");
                bankerSum -= milestones[1];
            }
            System.out.println("aft playerSum > " + playerSum);
            System.out.println("aft bankerSum > " + bankerSum);

            if (playerSum > bankerSum) {
                result = "Player wins with " + playerSum + " points.";
            } else if (bankerSum > playerSum) {
                if (bankerSum == 6){
                    result = "Banker wins with '6-Card Rule'";
                } else{
                    result = "Banker wins with " + bankerSum + " points.";
                }
            } else {
                result = "Draw";
            }
        }

        // synchronised: restricts access around a particular piece of code to one
        // thread at a time
        // only one thread can execute the section of the code -- prevents race
        // conditions
        synchronized (gameHistory) {
            if (result.contains("Banker wins")) {
                // System.out.println("Adding 'B' to gameHistory!");
                gameHistory.add("B");
            } else if (result.contains("Player wins")) {
                // System.out.println("Adding 'P' to gameHistory!");
                gameHistory.add("P");
            } else if (result.contains("Draw")) {
                // System.out.println("Adding 'D' to gameHistory!");
                gameHistory.add("D");
            }
            
            System.out.println(">>> " + gameHistory);

            // write to history if game count reaches 6
            if (gameHistory.size() == 6) {
                // System.out.println("gameHistory has reached 6 entries");
                // write into csv file and clear
                writeGameHistory(new ArrayList<>(gameHistory));
                gameHistory.clear();
            } 
                    
        }

        // save the updated cards list back to "cards.db"
        // remove those cards that were drawn from "cards.db"
        saveCards();

        // send the outcome to client
        // P|1|10|3,B|10|10|7
        String serverResponse = "P";
        for (String playerCard : playerCards) {
            serverResponse = serverResponse + "|" + Integer.toString(getCardValue(playerCard));
        }
        serverResponse = serverResponse + ",B";
        for (String bankerCard : bankerCards) {
            serverResponse = serverResponse + "|" + Integer.toString(getCardValue(bankerCard));
        }

        return serverResponse + " - " + result;
    }

    private static synchronized void writeGameHistory(List<String> gameHistorySnapshot) {
        try (FileWriter csvWriter = new FileWriter("game_history.csv", true)) {
            synchronized (gameHistorySnapshot) {
                if (!gameHistorySnapshot.isEmpty()) {
                    csvWriter.append(String.join(",", gameHistorySnapshot)).append("\n");
                    csvWriter.flush();
                }
            }
        } catch (IOException e) {
            System.out.println("Error writing game history: " + e.getMessage());
        }
    }

    private synchronized void saveCards() {
        // save the shuffled cards to a data file named "cards.db"
        try (FileWriter writer = new FileWriter("cards.db")) {
            for (String card : cards) {
                writer.write(card + "\n");
            }
            System.out.println("Shuffled cards saved to cards.db");
        } catch (IOException e) {
            System.out.println("Error writing to cards.db: " + e.getMessage());
        }
    }
}
