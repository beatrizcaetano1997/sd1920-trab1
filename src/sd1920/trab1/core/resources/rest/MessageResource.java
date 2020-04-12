package sd1920.trab1.core.resources.rest;

import java.net.URI;
import java.util.*;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.*;

import javax.ws.rs.core.Response.Status;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.core.clt.rest.ClientUtils;
import sd1920.trab1.core.servers.discovery.Discovery;

@Singleton
public class MessageResource implements MessageService {

    private Random randomNumberGenerator;

    private final HashMap<Long, Message> allMessages = new HashMap<>();
    private final HashMap<String, Set<Long>> userInboxs = new HashMap<>();

    private static Logger Log = Logger.getLogger(MessageResource.class.getName());
    private Discovery discovery;
    private String domain;

    public MessageResource(Discovery discovery, String domain)
    {
        this.randomNumberGenerator = new Random(System.currentTimeMillis());
        this.discovery = discovery;
        this.domain = domain;
    }

    @Override
    public long postMessage(String pwd, Message msg) {

        //Check if message is valid, if not return HTTP CONFLICT (409)
        if (msg.getSender() == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
            throw new WebApplicationException(Status.CONFLICT);
        }


        User userExists = new ClientUtils(getURI(domain, "users")).checkUser(msg.getSender().split("@")[0], pwd);

        //Check if a user is valid
        if (userExists == null) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }

        long newID = Math.abs(randomNumberGenerator.nextLong());
        synchronized (this) {
            while (allMessages.containsKey(newID)) {
                newID = Math.abs(randomNumberGenerator.nextLong());
            }
        }

        msg.setId(newID);
//            Add the message to the global list of messages
        String senderFormated = userExists.getDisplayName() + " <" + userExists.getName() + "@" + domain + ">";
        //I just created this string to not having to split again when processing the failed message
        String notformatedSender = msg.getSender();
        msg.setSender(senderFormated);
        synchronized (this) {
            allMessages.put(newID, msg);
        }

        Log.info("Created new message with id: " + newID);

        //Add the message (identifier) to the inbox of each recipient
        for (String recipient : msg.getDestination()) {
            String userDomain = recipient.split("@")[1];
            if (!userDomain.equals(domain)) {
                //Makes sure that the destination is only 1 and not all, to avoid server loops

                long midFromOtherDomain;

                Message toSend = getMessage(msg);
                midFromOtherDomain = new ClientUtils(getURI(recipient.split("@")[1], "messages"))
                						 .postOtherDomainMessage(toSend, recipient);

                //verificação se a mensagem foi realmente enviada
                if (midFromOtherDomain == -1) {

                    //Puts failed sent message in sender inbox
                    userFailedMessage(msg, recipient, notformatedSender);
                }

            } else {

                String chk = new ClientUtils(getURI(domain, "users"))
                				 .userExists(recipient.split("@")[0]);
                
                if (chk != null)
                {
                    Set<Long> msgSet;
                    synchronized (userInboxs) {
                        msgSet = userInboxs.get(recipient);
                    }

                    if (msgSet == null) {
                        synchronized (userInboxs) {
                            userInboxs.put(recipient, new HashSet<>());
                        }
                    }

                    synchronized (userInboxs) {
                        userInboxs.get(recipient).add(newID);
                    }

                } else {
                    //Puts failed sent message in sender inbox
                    userFailedMessage(msg, recipient, notformatedSender);
                }
            }
        }

        Log.info("Recorded message with identifier: " + newID);
        return newID;
    }


    @Override
    public Message getMessage(String user, long mid, String pwd)
    {
        Log.info("Received request for message with id: " + mid + ".");

        User userExists = new ClientUtils(getURI(domain, "users")).checkUser(user, pwd);

        //Check if a user is valid
        if (userExists == null) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }

        Message m;
        String key = user + "@" + domain;
        Set<Long> userSet;
        synchronized (userInboxs) {
            userSet = userInboxs.get(key);
        }

        if (!userSet.contains(mid)) {
            throw new WebApplicationException(Status.NOT_FOUND); //if not send HTTP 404 back to client
        }

        synchronized (this) {
            m = allMessages.get(mid);
        }

        Log.info("Returning requested message to user.");

        return m; //Return message to the client with code HTTP 200

    }


    @Override
    public List<Long> getMessages(String user, String pwd) {
        Log.info("Received request for messages with optional user parameter set to: '" + user + "'");

        List<Long> messages = new LinkedList<>();

        User userExists = new ClientUtils(getURI(domain, "users")).checkUser(user, pwd);

        if (userExists == null) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }

        Log.info("Collecting all messages in server for user " + user);

        Set<Long> mids;
        String key = user + "@" + domain;

        synchronized (userInboxs) {
            mids = userInboxs.getOrDefault(key, Collections.emptySet());
        }

        for (Long l : mids) {
            Log.info("Adding message with id: " + l + ".");
            messages.add(l);
        }


        Log.info("Returning message list to user with " + messages.size() + " messages.");
        return messages;
    }

    @Override
    public void removeFromUserInbox(String user, long mid, String pwd)
    {
        User userExists = new ClientUtils(getURI(domain, "users")).checkUser(user, pwd);

        //Check if a user is valid
        if (userExists == null) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }

        String key = user + "@" + domain;
        Set<Long> userSet;
        synchronized (userInboxs) {
            userSet = userInboxs.get(key);
        }

        if (!userSet.contains(mid)) {
            throw new WebApplicationException(Status.NOT_FOUND); //if not send HTTP 404 back to client
        }

        synchronized (userInboxs) {
            userInboxs.get(key).remove(mid);
        }
    }

    @Override
    public void deleteMessage(String user, long mid, String pwd) {


        User userExists = new ClientUtils(getURI(domain, "users")).checkUser(user, pwd);


        //Check if a user is valid
        if (userExists == null) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }


        Message msg;
        synchronized (this) {
            msg = allMessages.get(mid);
        }
        if (msg != null && msg.getSender().contains(user)) {
            synchronized (this) {
                msg = allMessages.remove(mid);
            }
        } else {
            return;
        }

        Set<String> msgDestination = msg.getDestination();

        for (String user_dest : msgDestination)
        {
            if (!user_dest.split("@")[1].equals(domain))
            {
                new ClientUtils(getURI(user_dest.split("@")[1], "messages"))
                	.deleteOtherDomainMessage(user_dest, msg);
            }
            else {
                synchronized (userInboxs) {
                    userInboxs.get(user_dest).remove(mid);
                }
            }
        }
    }

    @Override
    public long postOtherMessageDomain(Message m, String user)
    {
        String chk = new ClientUtils(getURI(domain, "users")).userExists(user.split("@")[0]);
        if (chk != null) {
            long newID = m.getId();

            Message tocheck;
            synchronized (this) {
                tocheck = allMessages.get(m.getId());
            }

            if (tocheck == null) {
                synchronized (this) {
                    allMessages.put(m.getId(), m);
                }
            }

            Set<Long> userSet;
            synchronized (userInboxs) {
                userSet = userInboxs.get(user);
            }

            if (userSet == null) {
                synchronized (userInboxs) {
                    userInboxs.put(user, new HashSet<>());
                }
            }


            synchronized (userInboxs) {
                userInboxs.get(user).add(newID);
            }

            return newID;
        } else {
            throw new WebApplicationException((Status.NOT_FOUND));
        }
    }


    //method to delete messages from other domains
    @Override
    public void deleteMessageFromOtherDomain(String user, Message m) {
        synchronized (this) {
            allMessages.remove(m.getId());
        }
        synchronized (userInboxs) {
            userInboxs.get(user).remove(m.getId());
        }
    }

    @Override
    public void deleteUserInbox(String user) {
        synchronized (userInboxs) {
            userInboxs.remove(user);
        }
    }

    //PRIVATE UTIL METHODS
    private void userFailedMessage(Message msg, String recipient, String notFormatedSender) {
        Message toUserMessage = new Message();

        long newFailedMessageID = Math.abs(randomNumberGenerator.nextLong());
        synchronized (this) {
            while (allMessages.containsKey(newFailedMessageID)) {
                newFailedMessageID = Math.abs(randomNumberGenerator.nextLong());
            }
        }


        toUserMessage.setId(newFailedMessageID);
        toUserMessage.setSender(msg.getSender());
        toUserMessage.setDestination(msg.getDestination());
        toUserMessage.setContents(msg.getContents());
        toUserMessage.setSubject("FALHA NO ENVIO DE " + msg.getId() + " PARA " + recipient);

        synchronized (this) {
            allMessages.put(newFailedMessageID, toUserMessage);
        }
        synchronized (this) {
            userInboxs.get(notFormatedSender).add(newFailedMessageID);
        }
    }

    private Message getMessage(Message msg) {
        Message toSend = new Message(msg.getSender(), msg.getDestination(), msg.getSubject(), msg.getContents());
        toSend.setCreationTime(msg.getCreationTime());
        toSend.setId(msg.getId());
        return toSend;
    }
    
    private String getURI(String domain, String serviceType)
    {
    	return discovery.getURI(domain, serviceType).toString();
    }

}
