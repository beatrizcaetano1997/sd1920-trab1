package sd1920.trab1.core.resources;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.*;

import javax.ws.rs.core.Response.Status;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.core.resources.utils.ClientUtils;
import sd1920.trab1.core.resources.utils.DeleteMessageQueue;
import sd1920.trab1.core.resources.utils.PostMessageQueue;
import sd1920.trab1.core.servers.discovery.Discovery;

@Singleton
public class MessageResource implements MessageService {

    private Random randomNumberGenerator;

    private HashMap<Long, Message> allMessages = new HashMap<>();
    private HashMap<String, Set<Long>> userInboxs = new HashMap<>();

    private static Logger Log = Logger.getLogger(MessageResource.class.getName());
    private Discovery discovery;
    private String domain;
    public ClientUtils clientUtils;
    private PostMessageQueue mq;
    private DeleteMessageQueue dq;


    public MessageResource(Discovery discovery, String domain) {
        this.randomNumberGenerator = new Random(System.currentTimeMillis());
        this.discovery = discovery;
        this.domain = domain;
        clientUtils = new ClientUtils();
        //Creates the handler for delivering failed messages
        mq = new PostMessageQueue(this);
        Thread postMessagesHandler = new Thread(mq);
        postMessagesHandler.start();

        //creates the handler for deleting messages
        dq = new DeleteMessageQueue(this);
        Thread deleteMessageHandler = new Thread(dq);
        deleteMessageHandler.start();

//        deserializeMessages();
//        deserializeUserBoxes();
    }

    @Override
    public long postMessage(String pwd, Message msg) {

        //Check if message is valid, if not return HTTP CONFLICT (409)
        if (msg.getSender() == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
            throw new WebApplicationException(Status.CONFLICT);
        }

        boolean userExists = clientUtils.checkUser(getURI(domain, "users"), msg.getSender(), pwd);

        //Check if a user is valid
        if (!userExists) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }

        long newID = Math.abs(randomNumberGenerator.nextLong());
        synchronized (this) {
            while (allMessages.containsKey(newID)) {
                newID = Math.abs(randomNumberGenerator.nextLong());
            }

            msg.setId(newID);
//            Add the message to the global list of messages
            allMessages.put(newID, msg);
//            updateOnWriteMessageBox();
        }
        Log.info("Created new message with id: " + newID);

        Map<String, Long> otherDomains = new HashMap<>();
        //Add the message (identifier) to the inbox of each recipient
        for (String recipient : msg.getDestination()) {
            String userDomain = recipient.split("@")[1];
            if (!userDomain.equals(domain)) {
                //Makes sure that the destination is only 1 and not all, to avoid server loops
                //Message toSend = getMessage(msg, recipient);
                long midFromOtherDomain = -1;

                if (!otherDomains.containsKey(userDomain)) {
                    Message toSend = getMessage(msg);
                    midFromOtherDomain = clientUtils.postOtherDomainMessage(getURI(recipient.split("@")[1], "messages"), toSend, recipient);

                    if (midFromOtherDomain != -1)
                        otherDomains.put(userDomain, midFromOtherDomain);

                } else {
                    Long mid = otherDomains.get(userDomain);
                    Message m = getMessage(msg);
                    m.setId(mid);
                    midFromOtherDomain = clientUtils.postOtherDomainMessage(getURI(recipient.split("@")[1], "messages"), m, recipient);

                }

                //verificação se a mensagem foi realmente enviada
                if (midFromOtherDomain == -1) {

                    mq.addMessage(msg, newID, recipient);

                    //FALHA NO ENVIO DE mid PARA user
                    userFailedMessage(msg, newID, recipient);
                }

            } else {
                synchronized (this) {
                    if (!userInboxs.containsKey(recipient)) {
                        userInboxs.put(recipient, new HashSet<>());
                    }
                    userInboxs.get(recipient).add(newID);
//                    updateOnWriteMessageBox();
                }
            }
        }

        Log.info("Recorded message with identifier: " + newID);
        userInboxs.get(msg.getSender()).add(newID);
        return newID;
    }


    @Override
    public Message getMessage(String user, long mid, String pwd) {
        Log.info("Received request for message with id: " + mid + ".");

        boolean userExists = clientUtils.checkUser(getURI(domain, "users"), user, pwd);

        if (!userExists) {
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

        boolean userExists = clientUtils.checkUser(getURI(domain, "users"), user, pwd);

        if (!userExists) {
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

        boolean userExists = clientUtils.checkUser(getURI(domain, "users"), user, pwd);

        if (!userExists) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }

        synchronized (this) {
            userInboxs.get(user).remove(mid);
//            updateOnWriteMessageBox();
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

        boolean userExists = clientUtils.checkUser(getURI(domain, "users"), user, pwd);


        if (!userExists) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }


        synchronized (this) {
            Message msg;
            synchronized (this) {
                msg = allMessages.remove(mid);
//                updateOnWriteMessageBox();
            }

            Set<String> msgDestination = msg.getDestination();

            for (String user_dest : msgDestination) {
                if (!user_dest.split("@")[1].equals(domain)) {
                    Message m = new Message();
                    m.setCreationTime(msg.getCreationTime());
                    boolean success = clientUtils.deleteOtherDomainMessage(getURI(user_dest.split("@")[1], "messages"), user_dest, m);
                    if (!success) {
                        dq.addMessage(msg, user_dest);
                    }
                } else {
                    synchronized (this) {
                        userInboxs.get(user_dest).remove(mid);
//                        updateOnWriteUserInbox();

                    }
                }
            }
        }

    }

    @Override
    public long postOtherMessageDomain(Message m, String user) {
        long newID = m.getId();
        if (newID == -1) {
            //checks if a message that came from the message queu exists by checking the timestamp
            long check = checkTimestamp(m.getCreationTime());
            if (check == -1) {

                //creates new message in the domain and stores it
                newID = Math.abs(randomNumberGenerator.nextLong());
                synchronized (this) {
                    while (allMessages.containsKey(newID)) {
                        newID = Math.abs(randomNumberGenerator.nextLong());
                    }

                    m.setId(newID);
//            Add the message to the global list of messages
                    allMessages.put(newID, m);
                    Log.info("Created new message with id: " + newID);

//            updateOnWriteMessageBox();
                }
            } else
                newID = check;
        }

        synchronized (this) {
            if (!userInboxs.containsKey(user)) {
                userInboxs.put(user, new HashSet<>());
            }
            userInboxs.get(user).add(newID);
//                    updateOnWriteMessageBox();
        }

        //places

        return 0;
    }

    private long checkTimestamp(long creationTime) {
        for (long m : allMessages.keySet()) {
            if (allMessages.get(m).getCreationTime() == creationTime)
                return m;
        }
        return -1;
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
//                updateOnWriteMessageBox();
                userInboxs.get(user).remove(removed.getId());
//                updateOnWriteUserInbox();

            }
        }

    }


    public void deleteUserInbox(String user) {
        synchronized (this) {
            userInboxs.remove(user);
//            updateOnWriteUserInbox();
        }
    }

    //PRIVATE UTIL METHODS
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
//            updateOnWriteMessageBox();
//            updateOnWriteUserInbox();
        }
    }

    public URI getURI(String domain, String serviceType) {


        URI[] l = discovery.knownUrisOf(domain);
        for (URI uri : l) {
            if (uri.toString().contains(serviceType)) {
                return uri;
            }
        }
        return null;
    }

    private Message getMessage(Message msg) {
        Message toSend = new Message();
        toSend.setCreationTime(msg.getCreationTime());
        toSend.setDestination(msg.getDestination());
        toSend.setContents(msg.getContents());
        toSend.setSender(msg.getSender());
        toSend.setSubject(msg.getSubject());
        toSend.setId(-1);
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

    public void deleteFailedMessage(long mid, String user, String recipient) {
        synchronized (this) {
            for (long mids : userInboxs.get(user)) {
                if (allMessages.get(mids).getSubject().equals("FALHA NO ENVIO DE " + mid + " " + recipient)) {
                    allMessages.remove(mids);
                    userInboxs.get(user).remove(mids);
                }
            }
        }
    }
}
