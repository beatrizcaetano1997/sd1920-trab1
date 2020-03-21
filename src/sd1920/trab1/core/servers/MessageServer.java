package sd1920.trab1.core.servers;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.core.resources.MessageResource;
import sd1920.trab1.core.servers.discovery.Discovery;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class MessageServer {

	private static Logger Log = Logger.getLogger(MessageServer.class.getName());

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}
	
	public static final int PORT = 8082;
	public static final String SERVICE = "MessageService";
	public static final String DOMAIN = "fcsh";

	public static void main(String[] args) throws UnknownHostException {
		ExecutorService pool = Executors.newFixedThreadPool(15);
		String ip = InetAddress.getLocalHost().getHostAddress();
			
		ResourceConfig config = new ResourceConfig();

		String serverURI = String.format("http://%s:%s/rest", ip, PORT);

		Discovery discovery = new Discovery(new InetSocketAddress("226.226.226.226", 2266), DOMAIN, serverURI+ MessageService.PATH);
		discovery.start();

		pool.execute(new Thread( () -> {
			config.register(new MessageResource(discovery, DOMAIN));
		}));

		JdkHttpServerFactory.createHttpServer( URI.create(serverURI), config);
	
		Log.info(String.format("%s Server ready @ %s\n",  SERVICE, serverURI));

	}

	
}
