package upem.jarret.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.Set;

public class Server {
	private final ServerSocketChannel ssc;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;

	private final Thread consoleThread = new Thread(() -> {
		try (Scanner scanner = new Scanner(System.in)) {
			while (scanner.hasNextLine()) {
				switch (scanner.nextLine()) {
				case "SHUTDOWN":
					shutdown();
					return;
				case "SHUTDOWN NOW":
					shutdownNow();
					return;
				case "INFO":
					info();
					break;
				default:
					System.out.println("WRONG COMMAND");
					break;
				}
			}
		}
	});

	public Server(int port) throws IOException {
		ssc = ServerSocketChannel.open();
		ssc.bind(new InetSocketAddress(port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	private void info() {
		System.out.println("INFO");
		// TODO
	}

	private void shutdown() {
		System.out.println("SHUTDOWN");
		try {
			ssc.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void shutdownNow() {
		System.out.println("SHUTDOWN NOW");
		try {
			ssc.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		for (SelectionKey key : selectedKeys) {
			try {
				close(key);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void launch() throws IOException {
		consoleThread.start();

		ssc.configureBlocking(false);
		ssc.register(selector, SelectionKey.OP_ACCEPT);
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		while (!Thread.interrupted()) {
			selector.select();
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void processSelectedKeys() {
		for (SelectionKey key : selectedKeys) {
			if (key.isValid() && key.isAcceptable()) {
				try {
					doAccept(key);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (key.isValid() && key.isWritable()) {
				doWrite(key);
			}
			if (key.isValid() && key.isReadable()) {
				doRead(key);
			}
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = ssc.accept();
		if (sc == null) {
			return;
		}
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ);
	}

	private void doRead(SelectionKey key) {
		// TODO Auto-generated method stub

	}

	private void doWrite(SelectionKey key) {
		// TODO Auto-generated method stub

	}

	private void close(SelectionKey key) throws IOException {
		key.channel().close();
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		new Server(Integer.parseInt(args[0])).launch();
	}

}
