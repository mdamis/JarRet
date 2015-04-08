package upem.jarret.main;

import java.io.IOException;

import upem.jarret.client.Client;

public class Main {

	private static void usage() {
		System.out.println("Usage: Client clientId serverAdress port");
	}

	public static void main(String[] args) throws NumberFormatException, IOException, ClassNotFoundException,
			IllegalAccessException, InstantiationException, InterruptedException {
		if (args.length != 3) {
			usage();
			return;
		}
		Client client = new Client(args[0], args[1], Integer.parseInt(args[2]));
		client.interact();
	}
}
