import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.glassfish.jersey.client.ClientConfig;
import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.core.servers.DomainServer;
import sd1920.trab1.core.servers.discovery.Discovery;
import org.apache.http.util.EntityUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


public class ClientTesting {

    private static Logger Log = Logger.getLogger(DomainServer.class.getName());

    public static void main(String[] args) throws IOException, InterruptedException {

        String domain = "fct";
        Discovery discovery = new Discovery(new InetSocketAddress("226.226.226.226", 2266), "test", "test");
        discovery.start();
        Thread.sleep(10000);


        URI[] l = discovery.knownUrisOf(domain);

        URI userFctURI = null;
        URI messageFctURI = null;

        for (URI uri : l) {
            if (uri.toString().contains("users"))
                userFctURI = uri;
            else
                messageFctURI = uri;
        }

        //Creates an fct userClient
        WebTarget fctUserTarget = buildTarget(userFctURI);
        //Creates an fct messageClient
        WebTarget fctMessageTarget = buildTarget(messageFctURI);
        Log.info("--------------------------STARTIN TEST BATTEY-----------------------------------------------\n");
        Log.info("--------------------------BASIC REST API----------------------------------------------------\n");

        //1 - Create a User from fct
        Log.info("Testing Create user at fct" + '\n');
        User user = new User();
        user.setName("rui");
        user.setDisplayName("rui@fct");
        user.setPwd("123asd");
        user.setDomain("fct");
        Response r = fctUserTarget.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).post(Entity.entity(user, MediaType.APPLICATION_JSON));
        String rui = r.readEntity(new GenericType<String>() {
        });
        Log.info("Created rui--------------> " + rui + '\n');
        //creates another user
        Log.info("Testing Create user at fct" + '\n');
        User user2 = new User();
        user2.setName("paulo");
        user2.setDisplayName("paulo@fct");
        user2.setPwd("123asd");
        user2.setDomain("fct");
        r = fctUserTarget.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).post(Entity.entity(user2, MediaType.APPLICATION_JSON));
        String paulo = r.readEntity(new GenericType<String>() {
        });
        Log.info("Created paulo--------------> " + paulo + '\n');

        //get paulo
        Log.info("Testing get User" + '\n');
        r = fctUserTarget.path("paulo").queryParam("pwd", "123asd").request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).get();
        User userrecived = r.readEntity(new GenericType<User>() {
        });
        Log.info("Got user mail obtained --------------> " + userrecived.getDisplayName() + '\n');

        //update paulo
        Log.info("Testing update User with current user password----------> : " + user2.getPwd() + '\n');
        user2.setPwd("123asd2");
        r = fctUserTarget.path("/paulo").queryParam("pwd", "123asd").request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).put(Entity.entity(user2, MediaType.APPLICATION_JSON));
        User userreceived2 = r.readEntity(new GenericType<User>() {
        });
        Log.info("Update user with a new password-----------------> " + userreceived2.getPwd() + '\n');

        //Testing delete
        Log.info("Testing delete User paulo" + '\n');
        r = fctUserTarget.path("/paulo").queryParam("pwd", "123asd2").request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).delete();
        User userrecivedfromDelete = r.readEntity(new GenericType<User>() {
        });
        Log.info("Deleted User ------------------>" + userrecivedfromDelete.getName() + '\n');

        Log.info("Just creating paulo again for testing" + '\n');
        r = fctUserTarget.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).post(Entity.entity(user2, MediaType.APPLICATION_JSON));
        paulo = r.readEntity(new GenericType<String>() {
        });
        Log.info("Done : ->" + paulo + '\n');


        /*#####################################################MESAGES#################################################################*/
        Log.info("#####################################################---Testing Messages now---#################################################################" + '\n');
        Log.info("Sending message from rui to paulo@fct and rute@fcsh" + '\n');
        //rui sends a message to paulo and rute
        Message m = new Message();
        m.setSender(rui);
        m.setSubject("just chillin");
        Set<String> s = new HashSet<>();
        s.add(paulo);
        s.add("rute@fcsh");
        m.setDestination(s);
        r = fctMessageTarget.queryParam("pwd", "123asd").request().accept(MediaType.APPLICATION_JSON).post(Entity.entity(m, MediaType.APPLICATION_JSON));
        Long messageId = r.readEntity(new GenericType<Long>() {
        });
        Log.info("Created message at fct server with ID: --------------> " + messageId + '\n');

        //Testing get messages
        Log.info("Obtaining paulo messages ------------------>: " + '\n');
        r = fctMessageTarget.path("/mbox/paulo@fct").queryParam("pwd", "123asd2").request().accept(MediaType.APPLICATION_JSON).get();
        String listString= r.readEntity(String.class);
        Gson gson=new Gson();
        Type type = new TypeToken<List<Long>>(){}.getType();
        List<Long> messageList = gson.fromJson(listString, type);

//        LinkedList<Long> messageList = r.readEntity(new GenericType<LinkedList<Long>>() {
//        });
        for (Long id : messageList) {
            Log.info("\t + ID------------>: " + id +'\n');
        }
        Log.info("\n");

        //Testing get Message
        Log.info("Testing get message with id ------------------>: " + messageList.get(0).toString() + '\n');
        r = fctMessageTarget.path("mbox/paulo@fct/" + messageList.get(0).toString()).queryParam("pwd", "123asd2").request().accept(MediaType.APPLICATION_JSON).get();
        Message obtainedMessage = r.readEntity(new GenericType<Message>() {
        });
        Log.info("Obtained message with subject:  ------------------>: " + obtainedMessage.getSubject() + '\n');

        //Using discovery to get fcsh Servers
        Log.info("Discovering FCSH server  ------------------>: " + '\n');

        URI[] l2 = discovery.knownUrisOf("fcsh");

        URI userFcshURI = null;
        URI messageFcshURI = null;

        for (URI uri : l2) {
            if (uri.toString().contains("users"))
                userFcshURI = uri;
            else
                messageFcshURI = uri;
        }

        //Creates an fcsh userClient
        WebTarget fcshUserTarget = buildTarget(userFcshURI);
        //Creates an fcsh messageClient
        WebTarget fcshMessageTarget = buildTarget(messageFcshURI);
        assert userFcshURI != null;
        Log.info("Discovered FCSH user server at  ------------------>: " +  userFcshURI.toString() +'\n');
        assert messageFcshURI != null;
        Log.info("Discovered FCSH message server at  ------------------>: " +  messageFcshURI.toString() +'\n');

        //Create a User from fcsh
        Log.info("Testing Create user at fcsh" + '\n');
        User userfcsh = new User();
        userfcsh.setName("rute");
        userfcsh.setDisplayName("rute@fcsh");
        userfcsh.setPwd("123asd");
        userfcsh.setDomain("fcsh");
        r = fcshUserTarget.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).post(Entity.entity(userfcsh, MediaType.APPLICATION_JSON));
        String rute = r.readEntity(new GenericType<String>() {
        });
        Log.info("Created rute--------------> " + rute + '\n');


        //Testing get messages
        Log.info("Obtaining rute messages ------------------>: " + '\n');
        r = fcshMessageTarget.path("/mbox/rute@fcsh").queryParam("pwd", "123asd").request().accept(MediaType.APPLICATION_JSON).get();
        String listString2 = r.readEntity(String.class);
        Gson gson2=new Gson();
        Type type2 = new TypeToken<List<Long>>(){}.getType();
        List<Long> messageList2 = gson2.fromJson(listString2, type2);
        //List<Long> messageList2 = r.readEntity(new GenericType<List<Long>>() {
        //});
        for (Long id : messageList2) {
            Log.info("\t + ID------------>: " + id +'\n');
        }
        Log.info("\n");


        //get rute message from our rui
        Log.info("Testing get message with id ------------------>: " + messageList2.get(0).toString() + '\n');
        r = fcshMessageTarget.path("/mbox/rute@fcsh/" + messageList2.get(0).toString()).queryParam("pwd", "123asd").request().accept(MediaType.APPLICATION_JSON).get();
        Message obtainedMessage2 = r.readEntity(new GenericType<Message>() {
        });
        Log.info("Obtained message with subject:  ------------------>: " + obtainedMessage2.getSubject() + '\n');

        Log.info("Testing remove message from rute inbox with id ------------------>: " + messageList2.get(0).toString() + '\n');
        r = fcshMessageTarget.path("/mbox/rute@fcsh/"+ messageList2.get(0).toString()).queryParam("pwd", "123asd").request().delete();
        Log.info("Response from deleting message :  ------------------>: " + r.getStatus() + '\n');

        Log.info("\n");

        Log.info("--------------------------STARTIN CONCURRENCY-----------------------------------------------\n");

        Thread newT = new Thread();

        Log.info("TEST BATTERY CONCLUDED :D YAY");


    }

    public static WebTarget buildTarget(URI uri) {
        ClientConfig config = new ClientConfig();
        Client client = ClientBuilder.newClient(config);
        return client.target(uri).path("/");
    }
}
