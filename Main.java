import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.concurrent.*;

public class Main {

    private static final int THREAD_NUM = 16;
    private static final long SIZE_THRESHOLD = 16 * 1024 * 1024; // 16MB
    private static final int BUFFER_SIZE = 8192; // 8KB
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_RESET = "\u001B[0m";

    public static void main(String[] args) throws Exception {

        System.out.println("請輸入需要下載文件的網址:");
        
        String fileUrl;
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            fileUrl = reader.readLine();
        }
        
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("HEAD");
        int responseCode = connection.getResponseCode();

        if (responseCode != HttpURLConnection.HTTP_OK) {
            System.out.println("錯誤！請檢查網址是否正確輸入或者文件是否存在！");
            return;
        }

        long fileSize = connection.getContentLengthLong();
        String fileName = Paths.get(url.getPath()).getFileName().toString();

        if (fileName.equals("") || fileName == null) {
            fileName = "downloaded_file";
        }

        final String finalFileName = fileName; 

        if (fileSize > SIZE_THRESHOLD) {
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUM);
            long chunkSize = fileSize / THREAD_NUM;

            for (int i = 0; i < THREAD_NUM; i++) {
                long startByte = chunkSize * i;
                long endByte = (i == THREAD_NUM - 1) ? fileSize - 1 : startByte + chunkSize - 1;

                executor.execute(() -> {
                    try {
                        downloadFilePart(fileUrl, finalFileName, startByte, endByte, chunkSize);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        } else {
            downloadFilePart(fileUrl, finalFileName, 0, fileSize - 1, fileSize);
        }

        String md5Checksum = getChecksum(finalFileName, "MD5");
        String sha1Checksum = getChecksum(finalFileName, "SHA-1");

        System.out.println(ANSI_BLUE + "MD5: " + md5Checksum + ANSI_RESET);
        System.out.println(ANSI_RED + "SHA1: " + sha1Checksum + ANSI_RESET);
        System.out.println(ANSI_GREEN + "下載完成啦！" + ANSI_RESET);
    }

    private static void downloadFilePart(String urlString, String fileName, long startByte, long endByte, long partSize) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);

        try (InputStream in = connection.getInputStream();
             RandomAccessFile raf = new RandomAccessFile(fileName, "rw")) { 

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            raf.seek(startByte);

            while ((bytesRead = in.read(buffer)) != -1) {
                raf.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                double percentageDownloaded = ((double) totalBytesRead / partSize) * 100;
                System.out.printf("Downloaded %.2f%% of this part.%n", percentageDownloaded);
            }
        }
    }

    private static String getChecksum(String filename, String algorithm) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream fis = new FileInputStream(filename)) {
            int nRead;
            while ((nRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, nRead);
            }
        }
        byte[] digest = md.digest();
        StringBuilder result = new StringBuilder();
        for (byte b : digest) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}