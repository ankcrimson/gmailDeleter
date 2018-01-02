import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import models.HeaderInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


//@Slf4j
public class Quickstart {
    static final Logger log = LogManager.getLogger(Quickstart.class.getName());
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
     * Global instance of the {@link FileDataStoreFactory}.
     */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY =
        JacksonFactory.getDefaultInstance();

    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this quickstart.
     * <p>
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/gmail-java-quickstart
     */
    private static final List<String> SCOPES =
        Arrays.asList(GmailScopes.MAIL_GOOGLE_COM);

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
        final InputStream in =
            Quickstart.class.getResourceAsStream("/client_secret.json");
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

        // Populate Bad Senders
        final Set<String> badSenders = getBadSenders();

        // Build a new authorized API client service.
        final Gmail service = getGmailService();

        // Print the labels in the user's account.
        final String user = "me";
        final ListLabelsResponse listResponse =
            service.users().labels().list(user).execute();
        final List<Label> labels = listResponse.getLabels();
        if (labels.size() == 0) {
            System.out.println("No labels found.");
        } else {
            log.info("Labels:");
            for (final Label label : labels) {
                log.info("- {}", label.getName());
            }
        }

        ListMessagesResponse listMessagesResponse = service.users().messages().list(user).execute();
        String nextPageToken = listMessagesResponse.getNextPageToken();
        while (listMessagesResponse.getMessages().size() > 0) {
            for (final Message message : listMessagesResponse.getMessages()) {

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



                boolean deleted = false;

//                Delete Promotions
                if(currentMessage.getLabelIds().contains("CATEGORY_PROMOTIONS")||currentMessage.getLabelIds().contains("CATEGORY_SOCIAL")){
                    service.users().messages().delete(user, headerInfo.getId()).execute();
                    deleted=true;
                }
                else {
//                    Delete bad Senders
                    for (final String badSender : badSenders) {
                        if (headerInfo.getFrom() != null && headerInfo.getFrom().contains(badSender)) {
                            service.users().messages().delete(user, headerInfo.getId()).execute();
                            deleted = true;
                        }
                    }
                }
                if (deleted) {
                    log.info("Deleting Message: {} - {}",currentMessage.getLabelIds(), headerInfo);
                } else {
//                log.info("List: {}", headerInfo);
                }


            }
            listMessagesResponse = service.users().messages().list(user).setPageToken(nextPageToken).execute();
            nextPageToken = listMessagesResponse.getNextPageToken();
        }

    }

    public static Set<String> getBadSenders() {
        final Set<String> badSenders = new HashSet<>();
        // Load client secrets.
        final InputStream in = Quickstart.class.getResourceAsStream("/badSenders.txt");
        try (InputStreamReader isr = new InputStreamReader(in)) {
            final BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                badSenders.add(line);
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
        return badSenders;
    }
}
