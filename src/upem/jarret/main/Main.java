package upem.jarret.main;

import java.io.IOException;

import upem.jarret.client.Client;
import upem.jarret.client.Task;
import upem.jarret.worker.Worker;
import upem.jarret.worker.WorkerFactory;

public class Main {

	private static void usage() {
		System.out.println("Usage: Client clientId serverAdress port");
	}
	
	public static void main(String[] args) throws NumberFormatException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
		if(args.length != 3) {
			usage();
			return;
		}
		Client client = new Client(args[0], args[1], Integer.parseInt(args[2]));
		Task task = client.requestTask();
		Worker worker = WorkerFactory.getWorker(task.getWorkerURL(), task.getWorkerClassName());
		String answer = worker.compute(task.getTask());
		client.sendAnswer(answer);
	}
}
