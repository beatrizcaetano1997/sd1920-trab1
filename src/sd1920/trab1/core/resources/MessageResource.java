package sd1920.trab1.core.resources;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import sd1920.trab1.api.Message;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.core.servers.discovery.Discovery;

@Singleton
public class MessageResource implements MessageService {

    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int REPLY_TIMOUT = 600;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_PERIOD = 10000;
    private Random randomNumberGenerator;

    private HashMap<Long, Message> allMessages;
    private HashMap<String, Set<Long>> userInboxs;

    private static Logger Log = Logger.getLogger(MessageResource.class.getName());
    private Discovery discovery;
    private String domain;


    public MessageResource(Discovery discovery, String domain) {
        this.randomNumberGenerator = new Random(System.currentTimeMillis());
        this.discovery = discovery;
        this.domain = domain;
        deserializeMessages();
        deserializeUserBoxes();
    }

    @Override
    public long postMessage(String pwd, Message msg) {

        //Check if message is valid, if not return HTTP CONFLICT (409)
        if (msg.getSender() == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
            throw new WebApplicationException(Status.CONFLICT);
        }
        //If password is null, we are assuming the message comes from otherDomain so there is no need to verify the password
        //only place the message in the message box
        if (pwd != null) {
            Response response = verifyUser(msg.getSender(), pwd);

            if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
                throw new WebApplicationException((Status.FORBIDDEN));
            }
        }

        long newID = Math.abs(randomNumberGenerator.nextLong());
        synchronized (this) {
            while (allMessages.containsKey(newID)) {
                newID = Math.abs(randomNumberGenerator.nextLong());
            }

            msg.setId(newID);
            //Add the message to the global list of messages
            allMessages.put(newID, msg);
            updateOnWriteMessageBox();
        }
        Log.info("Created new message with id: " + newID);

        //Add the message (identifier) to the inbox of each recipient
        for (String recipient : msg.getDestination()) {
            if (!recipient.split("@")[1].equals(domain)) {
                //Makes sure that the destination is only 1 and not all, to avoid server loops
                Message toSend = getMessage(msg, recipient);
                short retries = 0;
                boolean sucess = false;
                while (!sucess && retries < MAX_RETRIES) {
                    try {
                        //PostMessage RMI to other server
                        //FIXME: cuidado com o erro 500
                        Response r = webTarget(recipient.split("@")[1], "messages")
                                .queryParam("pwd", (Object) null)
                                .request().accept(MediaType.APPLICATION_JSON)
                                .post(Entity.entity(toSend, MediaType.APPLICATION_JSON));

                        if (r.getStatus() == Status.OK.getStatusCode()) {
                            sucess = true;
                        }

                    } catch (ProcessingException pe) {
                        retries++;
                        try {
                            Thread.sleep(RETRY_PERIOD);
                        } catch (InterruptedException ignored) {

                        }
                    }
                }

                if (!sucess) {
                    //GERA MENSAGEM DE ERRO QUE  -> MENSAGEM NAO FOI ENVIADA ->
                    //FALHA NO ENVIO DE mid PARA user
                    userFailedMessage(msg, newID, recipient);
                }

            } else {
                synchronized (this) {
                    if (!userInboxs.containsKey(recipient)) {
                        userInboxs.put(recipient, new HashSet<>());
                    }
                    userInboxs.get(recipient).add(newID);
                    updateOnWriteMessageBox();
                }
            }
        }

        Log.info("Recorded message with identifier: " + newID);
        return newID;
    }


    @Override
    public Message getMessage(String user, long mid, String pwd) {
        Log.info("Received request for message with id: " + mid + ".");
        Response response = verifyUser(user, pwd);


        if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }
        Message m;

        synchronized (this) {
            m = allMessages.get(mid);
        }

        if (m == null) { //check if message exists
            Log.info("Requested message does not exists.");
            throw new WebApplicationException(Status.NOT_FOUND); //if not send HTTP 404 back to client
        }

        Log.info("Returning requested message to user.");
        return m; //Return message to the client with code HTTP 200

    }


    @Override
    public List<Long> getMessages(String user, String pwd) {
        Log.info("Received request for messages with optional user parameter set to: '" + user + "'");
        List<Long> messages = new LinkedList<>();

        Response response = verifyUser(user, pwd);


        if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }


        Log.info("Collecting all messages in server for user " + user);
        Set<Long> mids;
        synchronized (this) {
            mids = userInboxs.getOrDefault(user, Collections.emptySet());
        }

        for (Long l : mids) {
            Log.info("Adding message with id: " + l + ".");
            messages.add(l);
        }


        Log.info("Returning message list to user with " + messages.size() + " messages.");
        return messages;
    }

    @Override
    public void removeFromUserInbox(String user, long mid, String pwd) {

        Response response = verifyUser(user, pwd);


        if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }

        synchronized (this) {
            userInboxs.get(user).remove(mid);
            updateOnWriteMessageBox();
        }

    }

    @Override
    public void deleteMessage(String user, long mid, String pwd) {

        synchronized (this) {
            if (!allMessages.containsKey(mid)) {
                Log.info("Requested message does not exists.");
                throw new WebApplicationException(Status.NOT_FOUND);
            }
        }

        Response response = verifyUser(user, pwd);


        if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }


        synchronized (this) {
            Message msg;
            synchronized (this) {
                msg = allMessages.remove(mid);
                updateOnWriteMessageBox();
            }

            Set<String> msgDestination = msg.getDestination();

            for (String user_dest : msgDestination) {
                if (!user_dest.split("@")[1].equals(domain)) {
                    Message m = new Message();
                    m.setCreationTime(msg.getCreationTime());
                    webTarget(user_dest.split("@")[1], "messages").path("/otherDomain/" + user_dest)
                            .request().accept(MediaType.APPLICATION_JSON)
                            .post(Entity.entity(m, MediaType.APPLICATION_JSON));
                } else {
                    synchronized (this) {
                        userInboxs.get(user_dest).remove(mid);
                        updateOnWriteUserInbox();

                    }
                }
            }
        }

    }

    //method to delete messages from other domains
    @Override
    public void deleteMessageFromOtherDomain(String user, Message m) {
        Set<Long> set;
        synchronized (this) {
            set = allMessages.keySet();
        }
        for (long s : set) {
            if (allMessages.get(s).getCreationTime() == m.getCreationTime()) {
                Message removed = allMessages.remove(s);
                updateOnWriteMessageBox();
                userInboxs.get(user).remove(removed.getId());
                updateOnWriteUserInbox();

            }
        }

    }


    public void deleteUserInbox(String user) {
        synchronized (this) {
            userInboxs.remove(user);
            updateOnWriteUserInbox();
        }
    }

    private Response verifyUser(String user, String pwd) {
        boolean sucess = false;
        short retries = 0;
        Response r = null;
        while (!sucess && retries < MAX_RETRIES) {
            try {
                r = webTarget(domain, "users").path(user.split("@")[0]).queryParam("pwd", pwd)
                        .request(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_TYPE).get();
                sucess = true;

            } catch (ProcessingException pe) {
                //timeout happened
                retries++;

                try {
                    Thread.sleep(RETRY_PERIOD);
                } catch (InterruptedException ignored) {
                    //nothing to be done here, if it happens, it will retry sooner
                }
            }
        }

        return r;
    }

    private void userFailedMessage(Message msg, long newID, String recipient) {
        Message toUserMessage = new Message();

        long newFailedMessageID = Math.abs(randomNumberGenerator.nextLong());
        synchronized (this) {
            while (allMessages.containsKey(newID)) {
                newFailedMessageID = Math.abs(randomNumberGenerator.nextLong());
            }
        }
        toUserMessage.setId(newFailedMessageID);
        toUserMessage.setSender(msg.getSender());
        toUserMessage.setDestination(msg.getDestination());
        toUserMessage.setContents(msg.getContents());
        toUserMessage.setSubject("FALHA NO ENVIO DE " + newID + " " + recipient);
        synchronized (this) {
            allMessages.put(newFailedMessageID, toUserMessage);
            userInboxs.get(msg.getSender()).add(newFailedMessageID);
            updateOnWriteMessageBox();
            updateOnWriteUserInbox();
        }
    }

    private WebTarget webTarget(String domain, String serviceType) {

        ClientConfig config = new ClientConfig();
        config.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
        config.property(ClientProperties.READ_TIMEOUT, REPLY_TIMOUT);
        Client client = ClientBuilder.newClient(config);
        URI[] l = discovery.knownUrisOf(domain);
        for (URI uri : l) {
            if (uri.toString().contains(serviceType)) {
                return client.target(uri);
            }
        }
        return null;
    }

    private Message getMessage(Message msg, String recipient) {
        Message toSend = new Message();
        toSend.setCreationTime(msg.getCreationTime());
        Set<String> destin = new HashSet<>();
        destin.add(recipient);
        toSend.setDestination(destin);
        toSend.setContents(msg.getContents());
        toSend.setSender(msg.getSender());
        toSend.setSubject(msg.getSubject());
        return toSend;
    }
    //Serialize and Deserialize methods

    private void updateOnWriteUserInbox() {
        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream("usersInbox.ser");
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(userInboxs);
            out.close();
            fileOut.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }

    }

    private void updateOnWriteMessageBox() {
        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream("messages.ser");
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(allMessages);
            out.close();
            fileOut.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }

    }

    private void deserializeMessages() {
        try {
            FileInputStream fileIn = new FileInputStream("messages.ser");
            ObjectInputStream objIn = new ObjectInputStream(fileIn);
            allMessages = (HashMap<Long, Message>) (objIn.readObject());
            objIn.close();
            fileIn.close();
        } catch (FileNotFoundException fnf) {
            allMessages = new HashMap<>();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void deserializeUserBoxes() {
        try {
            FileInputStream fileIn = new FileInputStream("usersInbox.ser");
            ObjectInputStream objIn = new ObjectInputStream(fileIn);
            userInboxs = (HashMap<String, Set<Long>>) (objIn.readObject());
            objIn.close();
            fileIn.close();
        } catch (FileNotFoundException fnf) {
            userInboxs = new HashMap<>();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
