package sd1920.trab1.api.resources;

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
import sd1920.trab1.api.Message;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.api.servers.discovery.Discovery;

@Singleton
public class MessageResource implements MessageService {

    private Random randomNumberGenerator;

    private final HashMap<Long, Message> allMessages = new HashMap<>();
    private final HashMap<String, Set<Long>> userInboxs = new HashMap<>();

    private static Logger Log = Logger.getLogger(MessageResource.class.getName());
    private Discovery discovery;
    private String domain;


    public MessageResource(Discovery discovery, String domain) {
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
        if (pwd != null) {
            Response response = getResponse(msg.getSender(), pwd);

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
        }
        Log.info("Created new message with id: " + newID);


        synchronized (this) {
            //Add the message (identifier) to the inbox of each recipient
            for (String recipient : msg.getDestination()) {
                if (!recipient.split("@")[1].equals(domain)) {
                    //Makes sure that the destination is only 1 and not all, to avoid server loops
                    Message toSend = getMessage(msg, recipient);
                    //PostMessage RMI to other server
                    webTarget(recipient.split("@")[1], "messages")
                            .queryParam("pwd", (Object) null)
                            .request().accept(MediaType.APPLICATION_JSON)
                            .post(Entity.entity(toSend, MediaType.APPLICATION_JSON));

                } else {
                    if (!userInboxs.containsKey(recipient)) {
                        userInboxs.put(recipient, new HashSet<Long>());
                    }
                    userInboxs.get(recipient).add(newID);
                }
            }
        }
        Log.info("Recorded message with identifier: " + newID);
        return newID;
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

    @Override
    public Message getMessage(String user, long mid, String pwd) {
        Log.info("Received request for message with id: " + mid + ".");
        Response response = getResponse(user, pwd);


        if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }
        Message m = null;

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

        Response response = getResponse(user, pwd);


        if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }


        Log.info("Collecting all messages in server for user " + user);
        synchronized (this) {
            Set<Long> mids = userInboxs.getOrDefault(user, Collections.emptySet());
            for (Long l : mids) {
                Log.info("Adding message with id: " + l + ".");
                messages.add(l);
            }
        }

        Log.info("Returning message list to user with " + messages.size() + " messages.");
        return messages;
    }

    @Override
    public void removeFromUserInbox(String user, long mid, String pwd) {

        Response response = getResponse(user, pwd);


        if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }

        synchronized (this) {
            userInboxs.get(user).remove(mid);
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

        Response response = getResponse(user, pwd);


        if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            throw new WebApplicationException((Status.FORBIDDEN));
        }


        synchronized (this) {
            Message msg = allMessages.remove(mid);
            Set<String> msgDestination = msg.getDestination();

            for (String user_dest : msgDestination) {
                if (!user_dest.split("@")[1].equals(domain)) {
                    Message m = new Message();
                    m.setCreationTime(msg.getCreationTime());
                    webTarget(user_dest.split("@")[1], "messages").path("/otherDomain/"+ user_dest)
                            .request().accept(MediaType.APPLICATION_JSON)
                            .post(Entity.entity(m, MediaType.APPLICATION_JSON));
                } else {
                    userInboxs.get(user_dest).remove(mid);
                }
            }
        }

    }

   @Override
   public void deleteMessageFromOtherDomain(String user, Message m){
        synchronized (this) {
            for (long s : allMessages.keySet()) {
                if (allMessages.get(s).getCreationTime() == m.getCreationTime()) {
                    Message removed = allMessages.remove(s);
                    userInboxs.get(user).remove(removed.getId());
                }
            }
        }
    }

    private Response getResponse(String user, String pwd) {
        return webTarget(domain, "users").path(user.split("@")[0]).queryParam("pwd", pwd)
                .request(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON_TYPE).get();
    }

    private WebTarget webTarget(String domain, String serviceType) {

        ClientConfig config = new ClientConfig();
        Client client = ClientBuilder.newClient(config);
        URI[] l = discovery.knownUrisOf(domain);
        for (URI uri : l) {
            if (uri.toString().contains(serviceType)) {
                return client.target(uri);
            }
        }
        return null;
    }
}
