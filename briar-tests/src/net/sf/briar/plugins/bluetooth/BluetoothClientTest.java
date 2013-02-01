package net.sf.briar.plugins.bluetooth;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.plugins.DuplexClientTest;

// This is not a JUnit test - it has to be run manually while the server test
// is running on another machine
public class BluetoothClientTest extends DuplexClientTest {

	private BluetoothClientTest(Executor executor, String serverAddress) {
		// Store the server's Bluetooth address and UUID
		TransportProperties p = new TransportProperties();
		p.put("address", serverAddress);
		p.put("uuid", BluetoothTest.getUuid());
		Map<ContactId, TransportProperties> remote =
			Collections.singletonMap(contactId, p);
		// Create the plugin
		callback = new ClientCallback(new TransportConfig(),
				new TransportProperties(), remote);
		plugin = new BluetoothPlugin(executor, new SystemClock(), callback, 0,
				0);
	}

	public static void main(String[] args) throws Exception {
		if(args.length != 1) {
			System.err.println("Please specify the server's Bluetooth address");
			System.exit(1);
		}
		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			new BluetoothClientTest(executor, args[0]).run();
		} finally {
			executor.shutdown();
		}
	}
}
