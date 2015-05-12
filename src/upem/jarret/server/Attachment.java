package upem.jarret.server;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

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

	void setRequestingTask(boolean requestingTask) {
		this.requestingTask = requestingTask;
	}

	public void requestAnswer(String answer) {
		// System.out.println("Client sending an answer: "+answer);
		this.setAnswer(answer);
		setSendingPost(true);
	}

	public boolean isSendingPost() {
		return sendingPost;
	}

	private void setSendingPost(boolean sendingPost) {
		this.sendingPost = sendingPost;
	}

	public HTTPReader getReader() {
		return reader;
	}

	public String getAnswer() {
		return answer;
	}

	private void setAnswer(String answer) {
		this.answer = answer;
	}

	public void clean(SocketChannel sc) {
		setSendingPost(false);
		reader = new HTTPReader(sc, ByteBuffer.allocate(50));
	}

}
