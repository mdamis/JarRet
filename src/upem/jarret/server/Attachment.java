package upem.jarret.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import upem.jarret.client.Client;
import upem.jarret.http.HTTPReader;

public class Attachment {
	private HTTPReader reader;
	private boolean requestingTask = false;
	private boolean sendingPost = false;
	private String answer = null;
	
	public Attachment(SocketChannel sc) {
		reader = new HTTPReader(sc, ByteBuffer.allocate(50));
	}

	public void requestTask() {
		System.out.println("Client requesting task");
		setRequestingTask(true);
	}

	public boolean isRequestingTask() {
		return requestingTask;
	}

	private void setRequestingTask(boolean requestingTask) {
		this.requestingTask = requestingTask;
	}

	public void requestAnswer(String answer) {
		//System.out.println("Client sending an answer: "+answer);
		this.answer = answer;
		setSendingPost(true);
	}

	public boolean isSendingPost() {
		return sendingPost;
	}

	private void setSendingPost(boolean sendingPost) {
		this.sendingPost = sendingPost;
	}

	public void sendCheckCode(SocketChannel sc) throws IOException {
		setSendingPost(false);
		if(answer == null) {
			throw new IllegalArgumentException("No answer");
		}
		if(Client.isJSON(answer)) {
			sc.write(Server.charsetUTF8.encode("HTTP/1.1 200 OK\r\n\r\n"));
		} else {
			sc.write(Server.charsetUTF8.encode(Server.badRequest));
		}
		reader = new HTTPReader(sc, ByteBuffer.allocate(50));
	}

	public void sendTask(SocketChannel sc) throws IOException {
		setRequestingTask(false);
		String worker = "{"
				+ "\"JobId\": \"23571113\","
				+ "\"WorkerVersion\": \"1.0\","
				+"\"WorkerURL\": \"http://igm.univ-mlv.fr/~carayol/WorkerPrimeV1.jar\","
				+"\"WorkerClassName\": \"upem.workerprime.WorkerPrime\","
				+"\"Task\":\"9329\"}";
		ByteBuffer bb = Server.charsetUTF8.encode(worker);
		String task = "HTTP/1.1 200 OK\r\n"
				+ "Content-Type: application/json; charset=utf-8\r\n"
				+ "Content-Length: "+bb.remaining()+"\r\n\r\n"+worker;
		bb = Server.charsetUTF8.encode(task);
		//System.out.println("Task: "+task);
		while(bb.hasRemaining()) {
			sc.write(bb);
		}
	}

	public HTTPReader getReader() {
		return reader;
	}
	
}
