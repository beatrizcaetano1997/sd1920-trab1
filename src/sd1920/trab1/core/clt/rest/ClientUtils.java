package sd1920.trab1.core.clt.rest;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

public class ClientUtils implements ClientUtilsInterface
{
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int REPLY_TIMOUT = 600;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_PERIOD = 1000;

    private final Client client;
    private final ClientConfig config;
    private final WebTarget target;

    public ClientUtils(String uri)
    {
        config = new ClientConfig();
        config.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
        config.property(ClientProperties.READ_TIMEOUT, REPLY_TIMOUT);
        client = ClientBuilder.newClient(config);
        target = client.target(uri);  
    }

    //In every method to verify the user
    @Override
    public User checkUser(String user, String pwd)
    {

        boolean success = false;
        short retries = 0;
        User receivedUser = null;

        Response r;
        while (!success && retries < MAX_RETRIES) {
            try {
                r = target.path(user).queryParam("pwd", pwd)
                          .request(MediaType.APPLICATION_JSON)
                          .accept(MediaType.APPLICATION_JSON_TYPE).get();

                if (r.getStatus() == Response.Status.OK.getStatusCode()) {
                    success = true;
                    receivedUser = r.readEntity(new GenericType<User>() {
                    });
                } else if (r.getStatus() == Response.Status.FORBIDDEN.getStatusCode()) {
                    success = true;
                } else if (r.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                    success = true;
                }

            } catch (ProcessingException e) {
                //timeout happened
                retries++;

                try {
                    Thread.sleep(RETRY_PERIOD);
                } catch (InterruptedException ignored) {
                    //nothing to be done here, if it happens, it will retry sooner
                }
            }
        }

        return receivedUser;
    }

    //When posting a message in other domain server ex -> postMessage
    @Override
    public Long postOtherDomainMessage(Message message, String user)
    {

        short retries = 0;
        while (retries < MAX_RETRIES) {
            try {

                //PostMessage RMI to other server
                Response r = target.path("postMessageFromDomain").path(user)
                        		   .queryParam("pwd", (Object) null)
                        		   .request().accept(MediaType.APPLICATION_JSON)
                        		   .post(Entity.entity(message, MediaType.APPLICATION_JSON));
                
                if (r.getStatus() == Response.Status.OK.getStatusCode()) {
                    return r.readEntity(new GenericType<Long>() {
                    });
                } else {
                    return (long) -1;
                }

            } catch (ProcessingException pe) {
                retries++;
                try {
                    Thread.sleep(RETRY_PERIOD);
                } catch (InterruptedException ignored) {

                }
            }
        }
        return (long) -1;
    }

    //Used to delete a given message in other domain ex -> DeleteMessage
    @Override
    public void deleteOtherDomainMessage(String user, Message m)
    {
        boolean success = false;
        short retries = 0;

        Response r;
        while (!success && retries < MAX_RETRIES) {
            try {
                r = target.path("otherDomain").path(user)
                          .request().accept(MediaType.APPLICATION_JSON)
                          .post(Entity.entity(m, MediaType.APPLICATION_JSON));
                if (r.getStatus() == Response.Status.NO_CONTENT.getStatusCode())
                    success = true;

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

    }

    //Used to delete a user inbox ex -> DeleteUser
    @Override
    public void deleteUserInbox(String user)
    {

        boolean success = false;
        short retries = 0;


        while (!success && retries < MAX_RETRIES) {
            try {
                target.path("deleteUserInbox").path(user)
                	  .request().delete();

                success = true;
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

    }

    public String userExists(String user) {


        boolean success = false;
        short retries = 0;
        String receivedUser = null;

        Response r;
        while (!success && retries < MAX_RETRIES) {
            try {
                r = target.path("userExists").path(user)
                          .request()
                          .accept(MediaType.APPLICATION_JSON_TYPE).get();

                if (r.getStatus() == Response.Status.OK.getStatusCode()) {
                    success = true;
                    receivedUser = r.readEntity(new GenericType<String>() {
                    });
                } else if (r.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                    return null;
                }

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

        return receivedUser;

    }
}
