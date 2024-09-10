import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;

public class GopherClient {

    static String   serviceHost = "comp3310.ddns.net";
    static int      servicePort = 70;
    static String servPort = "70";
    static Set<String> visitedSelectors = new HashSet<>();
    static int directoryCount = 0;
    static List<String> textFiles = new ArrayList<>();
    static List<String> binaryFiles = new ArrayList<>();
    static long smallestTextSize = Long.MAX_VALUE;
    static String smallestTextFileContents = "";
    static long largestTextSize = 0;
    static long smallestBinarySize = Long.MAX_VALUE;
    static long largestBinarySize = 0;
    static Set<String> externalServers = new HashSet<>();
    static Set<String> invalidReferences = new HashSet<>();
    static Map<String, Boolean> externalServerStatus = new HashMap<>();
    //Time formatter
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    static Queue<String> que = new LinkedList<>();
    static String blank = "\r\n";
    static Set<String> visited = new HashSet<>();


    /**
     * Main entry point for the GopherClient application. Initializes the application and starts
     * the crawling process.
     *
     * @param args Command line arguments for specifying the host and port of the Gopher server.
     */
    public static void main(String[] args) {
        try {
            processArgs(args);
            que.add("");
            visited.add("");
            //crawlGopherServer(serviceHost, servicePort, "", "\r\n"); // Start crawling
            int timeout = 5000;
            while(!que.isEmpty()) {
                String path = que.poll();
                browseGopher(serviceHost, servicePort, path, timeout);
            }
            System.out.println("Crawling complete.");
            printStatistics();
            printExternalServerStatus();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Connects to the Gopher server and initiates the browsing process.
     *
     * @param host    The hostname of the Gopher server.
     * @param port    The port number of the Gopher server.
     * @param path    The initial path to start browsing from.
     * @param timeout The timeout value for the socket connection.
     * @throws IOException if an I/O error occurs when creating or accessing the socket.
     */
    public static void browseGopher(String host, int port, String path, int timeout) throws IOException {
        Socket sock = new Socket();
        try {
            sock.connect(new InetSocketAddress(host, port), timeout);
            sock.setSoTimeout(timeout);

            sendRequest(sock, path);
            readReply(sock);
        } finally {
            sock.close();
        }
    }

    /**
     * Reads the reply from the Gopher server and processes each line received.
     *
     * @param sock The socket connected to the Gopher server.
     * @throws IOException if an I/O error occurs when reading from the socket.
     */
    public static void readReply(Socket sock) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        String reply;
        while ((reply = reader.readLine()) != null && reply.length() > 0) {
            System.out.println(reply);
            // Skip empty lines or those that don't split into at least two parts
            String[] parts = reply.split("\t");
            if (parts.length < 2 || parts[0].length() == 0) {
                continue;
            }

            char type = parts[0].charAt(0); // The first character is the type
            String selector = parts[1];

            // Skip info lines
            if (type == 'i' || selector.isEmpty()) {
                continue;
            }

            // For other types, check if the line is well formed
            if (parts.length < 4) {
                invalidReferences.add(reply);
                continue;
            }

            String host = parts[2];
            int port;

            try {
                port = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                invalidReferences.add(reply);
                continue;
            }

            // Only process items from our server
            if (!host.equals(serviceHost) || port != servicePort) {
                externalServers.add(host + ":" + port);
                continue;
            }
            switch (type) {
                case '0': {
                    try{
                    String fileContent = downloadTextFile(serviceHost, servicePort, selector, 10000, 5000);
                    updateFileStatistics(selector, fileContent.length(), fileContent, false);
                    saveFile(selector, fileContent);
                    }catch(SocketTimeoutException e){
                        System.err.println("Timeout occurred while trying to download the text file: " + selector);
                    }
                    break;
                }
                case '1': {
                    directoryCount++;
                    if(!visited.contains(selector)) {
                        que.add(selector);
                        visited.add(selector);
                    }
                    break;
                }
                case '9': {
                    try{
                    byte[] fileContent = downloadBinaryFile(host, port, selector, 5000, Integer.MAX_VALUE);
                    updateFileStatistics(selector, fileContent.length, null, true);
                    saveFile(selector, fileContent);
                    }catch (SocketTimeoutException e){
                        System.err.println("Timeout occurred while trying to download the binary file: " + selector);
                    }
                    break;
                }
                default : {
                    System.err.println("Received unknown or invalid type: " + type);
                    invalidReferences.add(reply);
                    break;
                }
            }
        }
    }


    /**
     * Sends a request to the Gopher server over an open socket connection.
     *
     * @param sock    The socket connected to the Gopher server.
     * @param request The request string to send.
     * @throws IOException if an I/O error occurs when writing to the socket.
     */
    protected static void sendRequest(Socket sock, String request) throws IOException {
        PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
        out.print(request + blank);  // use "\r\n" to end the request
        out.flush();  // ensure the request send immi

        //Get the current time and print the request and timestamp
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println("[" + timestamp + "] Sent request to server: '" + request + "'");
    }

    /**
     * Downloads a text file from the Gopher server.
     *
     * @param host      The hostname of the Gopher server.
     * @param port      The port number of the Gopher server.
     * @param selector  The selector string to identify the file on the server.
     * @param maxLength The maximum allowed length of the text file.
     * @param timeout   The timeout value for the socket connection.
     * @return The content of the text file as a String.
     * @throws IOException if an I/O error occurs when creating or accessing the socket.
     */
    public static String downloadTextFile(String host, int port, String selector, int maxLength, int timeout) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.print(selector + "\r\n");
            out.flush();

            StringBuilder fileContent = new StringBuilder();
            String line;
            int accumulatedLength = 0;
            while ((line = in.readLine()) != null && accumulatedLength < maxLength) {
                if (line.equals(".")) {
                    break;
                }
                fileContent.append(line).append("\n");
                accumulatedLength += line.length() + 1;
                if (accumulatedLength >= maxLength) break;
            }
            if (fileContent.length() > 0 && fileContent.charAt(fileContent.length() - 1) == '\n') {
                fileContent.deleteCharAt(fileContent.length() - 1);
            }
            return fileContent.toString();
        } finally {
            socket.close();
        }
    }

    /**
     * Downloads a binary file from the Gopher server.
     *
     * @param host      The hostname of the Gopher server.
     * @param port      The port number of the Gopher server.
     * @param selector  The selector string to identify the file on the server.
     * @param maxLength The maximum allowed length of the binary file.
     * @param timeout   The timeout value for the socket connection.
     * @return The content of the binary file as a byte array.
     * @throws IOException if an I/O error occurs when creating or accessing the socket.
     */
    public static byte[] downloadBinaryFile(String host, int port, String selector, int timeout, int maxLength) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeout * 2);
            socket.setSoTimeout(timeout * 2);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write((selector + "\r\n").getBytes());
            out.flush();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int accumulatedLength = 0;
            byte[] data = new byte[4096];
            int nRead;

            while (accumulatedLength < maxLength && (nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
                accumulatedLength += nRead;
                if (accumulatedLength >= maxLength) break;
            }
            return buffer.toByteArray();
        } finally {
            socket.close();
        }
    }

    /**
     * Saves the content of a file to the disk.
     *
     * @param selector The selector string to identify the file on the server, used as the filename.
     * @param data     The content of the file to be saved.
     * @throws IOException if an I/O error occurs when writing to the file.
     */
    private static void saveFile(String selector, byte[] data) throws IOException {
        String extension = ".bin";
        String filename = sanitizeFilename(selector) + extension;
        Path path = Paths.get(filename);
        Files.write(path, data);
    }

    /**
     * Saves the content of a text file to the disk.
     *
     * @param selector The selector string to identify the file on the server, used as the filename.
     * @param data     The content of the file to be saved.
     * @throws IOException if an I/O error occurs when writing to the file.
     */

    private static void saveFile(String selector, String data) throws IOException {
        String extension = ".txt";
        String filename = sanitizeFilename(selector) + extension;
        Path path = Paths.get(filename);
        Files.write(path, data.getBytes(StandardCharsets.UTF_8));
    }


    /**
     * Sanitizes a filename by replacing all characters that are not alphanumeric, periods, or hyphens with underscores.
     * Truncates the filename if it exceeds the filesystem limit of 255 characters.
     *
     * @param selector The selector string that identifies the file on the server.
     * @return A safe filename suitable for use on the filesystem.
     */
    private static String sanitizeFilename(String selector) {
        String safeFilename = selector.replaceAll("[^a-zA-Z0-9._-]", "_");

        final int MAX_FILENAME_LENGTH = 100;
        if (safeFilename.length() > MAX_FILENAME_LENGTH) {
            int extensionIndex = safeFilename.lastIndexOf('.');
            String extension = "";
            if (extensionIndex > 0) {
                extension = safeFilename.substring(extensionIndex);
                safeFilename = safeFilename.substring(0, extensionIndex);
            }
            safeFilename = safeFilename.substring(0, MAX_FILENAME_LENGTH - extension.length()) + extension;
        }

        return safeFilename;
    }


    /**
     * Updates the statistics for the downloaded files.
     *
     * @param fullPath     The full path to the file on the server.
     * @param fileSize     The size of the file in bytes.
     * @param fileContent  The content of the file.
     * @param isBinary     Flag indicating if the file is binary.
     */
    protected static void updateFileStatistics(String fullPath, long fileSize, String fileContent, boolean isBinary) {
        if (!isBinary) {
            if (fileSize < smallestTextSize) {
                smallestTextSize = fileSize;
                smallestTextFileContents = fileContent.isEmpty() ? "" : fileContent;
            }
            largestTextSize = Math.max(largestTextSize, fileSize);
            textFiles.add(fullPath);
        } else {
            if (fileSize < smallestBinarySize) {
                smallestBinarySize = fileSize;
            }
            largestBinarySize = Math.max(largestBinarySize, fileSize);
            binaryFiles.add(fullPath);
        }
    }

    /**
     * Checks the status of an external server by attempting to open a socket connection
     * to the specified host and port. Updates the external server status map.
     *
     * @param host The hostname of the external server.
     * @param port The port number of the external server.
     */
    private static void checkExternalServerStatus(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            externalServerStatus.put(host + ":" + port, true);
        } catch (IOException e) {
            externalServerStatus.put(host + ":" + port, false);
        }
    }



    /**
     * Processes command line arguments to set up the Gopher server's host and port.
     *
     * @param args Command line arguments passed to the application.
     */
    protected static void processArgs(String[] args) {
        if (args.length > 0) {
            serviceHost = args[0];
            if (args.length > 1) {
                try {
                    servicePort = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    System.out.println("Error: Port must be an integer.");
                    System.exit(-1);
                }
            }
        }
    }




    /**
     * Prints the final statistics after crawling the Gopher server.
     */
    private static void printStatistics() {
        System.out.println("Directories visited: " + directoryCount);
        System.out.println("Text files: " + textFiles.size() + " " + textFiles);
        System.out.println("Binary files: " + binaryFiles.size() + " " + binaryFiles);
        System.out.println("Smallest text file contents: " + smallestTextFileContents);
        System.out.println("Size of the largest text file: " + largestTextSize);
        System.out.println("Size of the smallest binary file: " + smallestBinarySize);
        System.out.println("Size of the largest binary file: " + largestBinarySize);
        System.out.println("Invalid references: " + invalidReferences.size() + " " + invalidReferences);
        System.out.println("External servers: " + externalServers);
    }

    /**
     * Prints the status of all external servers encountered during the crawl.
     */
    private static void printExternalServerStatus() {
        System.out.println("External servers and their status:");
        externalServerStatus.forEach((server, isUp) ->
            System.out.println(server + " is " + (isUp ? "up" : "down")));
    }

}