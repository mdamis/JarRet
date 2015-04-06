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

	public static void main(String[] args) throws NumberFormatException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException {
		if(args.length != 3) {
			usage();
			return;
		}
		Client client = new Client(args[0], args[1], Integer.parseInt(args[2]));
		Task task;
		Worker worker = null;
		do {
			while(true) {
				task = client.requestTask();
				try {
					Thread.sleep(task.getComeBackInSeconds());
				} catch(IllegalArgumentException e) {
					break;
				}
			} 
			if(worker == null || task.getWorkerVersion() != worker.getVersion() || task.getJobId() != worker.getJobId()) {
				worker = WorkerFactory.getWorker(task.getWorkerURL(), task.getWorkerClassName());
			} 
			String answer;
			try{
				answer = worker.compute(task.getTask());
			} catch(Exception e) {
				answer = null;
			}
			client.sendAnswer(answer);
		} while(true);
	}
}
