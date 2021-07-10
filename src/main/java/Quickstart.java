import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import models.HeaderInfo;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;


//@Slf4j
public class Quickstart {
    static final Logger log = LogManager.getLogger(Quickstart.class.getName());
    static final String sendersPath = Paths.get("log", "popularSenders.txt").toString();
    /**
     * Application name.
     */
    private static final String APPLICATION_NAME =
            "Gmail API Java Quickstart";

    /**
     * Directory to store user credentials for this application.
     */
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            System.getProperty("user.home"), ".credentials/gmail-java-quickstart");
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY =
            GsonFactory.getDefaultInstance();
    /**
     * Global instance of the scopes required by this quickstart.
     * <p>
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/gmail-java-quickstart
     */
    private static final List<String> SCOPES =
            Arrays.asList(GmailScopes.MAIL_GOOGLE_COM);
    /**
     * Global instance of the {@link FileDataStoreFactory}.
     */
    private static FileDataStoreFactory DATA_STORE_FACTORY;
    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport HTTP_TRANSPORT;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (final Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException {
        // Load client secrets.
        final InputStream in = Quickstart.class.getResourceAsStream("/client_secret.json");
        final GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        final GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        final Credential credential = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver()).authorize("user");
        System.out.println(
                "Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Build and return an authorized Gmail client service.
     *
     * @return an authorized Gmail client service
     * @throws IOException
     */
    public static Gmail getGmailService() throws IOException {
        final Credential credential = authorize();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static void main(final String[] args) throws IOException {
        log.info("STARTING----");
        // Populate Bad Senders
        final Set<String> badSenders = getBadSenders();
        final Map<String, Integer> popularSenders = new HashMap<>();
        // Build a new authorized API client service.
        final Gmail service = getGmailService();

        // Print the labels in the user's account.
        final String user = "me";
        final ListLabelsResponse listResponse = service.users().labels().list(user).execute();
        final List<Label> labels = listResponse.getLabels();
        if (labels.size() == 0) {
            System.out.println("No labels found.");
        } else {
            log.info("Labels:");
            for (final Label label : labels) {
                log.info("- {}", label.getName());
            }
        }

        ListMessagesResponse listMessagesResponse = service.users().messages().list(user).setMaxResults(500L).execute();
        String nextPageToken = listMessagesResponse.getNextPageToken();
        int total = 0;
        long start = Instant.now().toEpochMilli();
        while (listMessagesResponse.getMessages().size() > 0) {
            final long newTime = Instant.now().toEpochMilli();
            log.info("event=processed msgs={} timeTaken={} sec", total, (newTime - start) / 1000);
            start = newTime;
            total += listMessagesResponse.getMessages().size();
            listMessagesResponse.getMessages().parallelStream().forEach(
                    message -> {
                        if ((int) (Math.random() * 100) == 15) {
                            try (final BufferedWriter bw = new BufferedWriter(new FileWriter(sendersPath))) {
                                bw.write("email,count\n");
                                for (final Map.Entry<String, Integer> e : popularSenders.entrySet()) {
                                    bw.write(e.getKey() + "," + e.getValue() + "\n");
                                }
                            } catch (final Exception ex) {
                                log.error("event=errorUpdatingPopularSenders", ex);
                            }
                        }
                        processGivenMessage(badSenders, popularSenders, service, user, message);
                    }
            );
            listMessagesResponse = service.users().messages().list(user).setPageToken(nextPageToken).setMaxResults(500L).execute();
            nextPageToken = listMessagesResponse.getNextPageToken();
        }

    }

    private static void processGivenMessage(final Set<String> badSenders, final Map<String, Integer> popularSenders, final Gmail service, final String user, final Message message) {
        try {
            final Message currentMessage = service.users().messages().get(user, message.getId()).execute();
            final List<MessagePartHeader> headerList = currentMessage.getPayload().getHeaders();
            final HeaderInfo headerInfo = new HeaderInfo();
            headerInfo.setId(currentMessage.getId());
            for (final MessagePartHeader header : headerList) {
                if ("Subject".equals(header.getName())) {
                    headerInfo.setSubject(header.getValue());
                }
                if ("From".equals(header.getName())) {
                    headerInfo.setFrom(header.getValue());
                }
                if ("Date".equals(header.getName())) {
                    headerInfo.setDate(header.getValue());
                }
            }
            final String from = getFrom(headerInfo);
//            log.info("event=from from={}", from);

            //update popular senders
            popularSenders.put(from, popularSenders.getOrDefault(from, 0) + 1);

//                Delete Promotions
            if (currentMessage.getLabelIds().contains("CATEGORY_PROMOTIONS") || currentMessage.getLabelIds().contains("CATEGORY_SOCIAL")) {
//                        service.users().messages().delete(user, headerInfo.getId()).execute();
                writeToFileAndLog("bad_category", from, currentMessage, headerInfo);

            } else {
//                    Delete bad Senders
                if (badSenders.contains(from)) {
//                            service.users().messages().delete(user, headerInfo.getId()).execute();
                    writeToFileAndLog("bad_sender", from, currentMessage, headerInfo);
                }
            }
        } catch (final Exception ex) {
            log.error("event=errorInProcess", ex);
        }
    }


    private static void writeToFileAndLog(final String reason, final String from, final Message currentMessage, final HeaderInfo headerInfo) {

        log.info("event=Deleting reason={} from={} labels={} details={}", reason, from, currentMessage.getLabelIds(), headerInfo);
        final String filePath = Paths.get("log", "Emails", String.format("%s_%s_%s.txt", reason, from, headerInfo.getDate())).toString();
//        log.info("event=FilePath path={}", filePath);
        try (final BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            bw.write(headerInfo.getSubject());
            bw.write("\n");
            if (currentMessage.getPayload() != null && currentMessage.getPayload().getBody() != null && currentMessage.getPayload().getBody().getData() != null) {
                bw.write(new String(Base64.decodeBase64(currentMessage.getPayload().getBody().getData())));
            }
        } catch (final Exception ex) {
            log.error("event=errorWritingToFile", ex);
        }
    }

    private static String getFrom(final HeaderInfo headerInfo) {
        final String from_complete = headerInfo.getFrom().toLowerCase();
        final int fromSt = from_complete.lastIndexOf('<');
        final int fromEn = from_complete.lastIndexOf('>');
        if (fromSt >= 0) {
            return from_complete.substring(fromSt + 1, fromEn);
        } else {
            return from_complete;
        }
    }

    public static Set<String> getBadSenders() {
        final Set<String> badSenders = new HashSet<>();
        // Load client secrets.
        final InputStream in = Quickstart.class.getResourceAsStream("/badSenders.txt");
        try (final InputStreamReader isr = new InputStreamReader(in)) {
            final BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                badSenders.add(line.toLowerCase());
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
        return badSenders;
    }
}
