package sd1920.trab1.api.servers;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd1920.trab1.api.resources.MessageResource;
import sd1920.trab1.api.resources.UsersResource;
import sd1920.trab1.api.rest.UserService;
import sd1920.trab1.api.servers.discovery.Discovery;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class UserServer {
    private static Logger Log = Logger.getLogger(MessageServer.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8081;
    public static final String SERVICE = "MessageService";
    public static final String DOMAIN = "fct";

    public static void main(String[] args) throws UnknownHostException {
        ExecutorService pool = Executors.newFixedThreadPool(15);
        String ip = InetAddress.getLocalHost().getHostAddress();

        ResourceConfig config = new ResourceConfig();

        String serverURI = String.format("http://%s:%s/rest", ip, PORT);

        pool.execute(new Thread( () -> {
            config.register(new UsersResource(DOMAIN));
        }));

        Discovery discovery = new Discovery(new InetSocketAddress("226.226.226.226", 2266), DOMAIN, serverURI + UserService.PATH);
        discovery.start();

        JdkHttpServerFactory.createHttpServer( URI.create(serverURI), config);

        Log.info(String.format("%s Server ready @ %s\n",  SERVICE, serverURI));

    }
}
