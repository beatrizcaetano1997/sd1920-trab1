package sd1920.trab1.core.servers;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.api.rest.UserService;
import sd1920.trab1.core.resources.MessageResource;
import sd1920.trab1.core.resources.UsersResource;
import sd1920.trab1.core.servers.discovery.Discovery;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class DomainServer {

	private static Logger Log = Logger.getLogger(DomainServer.class.getName());

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}
	
	public static final int PORT = 8082;
	public static final String MESSAGE_SERVICE = "MessageService";
	public static final String USER_SERVICE = "UserService";


	public static void main(String[] args) throws UnknownHostException {
		ExecutorService messagePool = Executors.newFixedThreadPool(15);
		ExecutorService usersPool = Executors.newFixedThreadPool(15);

		String ip = InetAddress.getLocalHost().getHostAddress();
		String domain = InetAddress.getLocalHost().getHostName();

		ResourceConfig config = new ResourceConfig();

		String serverURI = String.format("http://%s:%s/rest", ip, PORT);

		Discovery messageDiscovery = new Discovery(new InetSocketAddress("226.226.226.226", 2266), domain, serverURI+ MessageService.PATH);
		messageDiscovery.start();

		Discovery userDiscovery = new Discovery(new InetSocketAddress("226.226.226.226", 2266), domain, serverURI+ UserService.PATH);
		userDiscovery.start();

		messagePool.execute(new Thread( () -> {
			config.register(new MessageResource(messageDiscovery, domain));
		}));

		usersPool.execute(new Thread( () -> {
			config.register(new UsersResource(domain, userDiscovery));
		}));

		JdkHttpServerFactory.createHttpServer( URI.create(serverURI), config);
	
		Log.info(String.format("%s Server ready @ %s\n", MESSAGE_SERVICE, serverURI));
		Log.info(String.format("%s Server ready @ %s\n", USER_SERVICE, serverURI));


	}

	
}
