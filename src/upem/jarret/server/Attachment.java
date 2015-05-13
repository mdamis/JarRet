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

	/**
	 * Set requestingTask to true
	 */
	public void requestTask() {
		setRequestingTask(true);
	}

	/**
	 * 
	 * @return the value of requestingTask
	 */
	public boolean isRequestingTask() {
		return requestingTask;
	}

	/**
	 * Set the value of requestingTask with the param requestingTask
	 * 
	 * @param requestingTask 
	 */
	void setRequestingTask(boolean requestingTask) {
		this.requestingTask = requestingTask;
	}

	/**
	 * Set answer with the value of answer
	 * 
	 * @param answer
	 */
	public void requestAnswer(String answer) {
		this.setAnswer(answer);
		setSendingPost(true);
	}

	/**
	 * Returns the value of sendingPost
	 * 
	 * @return
	 */
	public boolean isSendingPost() {
		return sendingPost;
	}

	/**
	 * Set the value of sendingPost
	 * 
	 * @param sendingPost
	 */
	private void setSendingPost(boolean sendingPost) {
		this.sendingPost = sendingPost;
	}

	/**
	 * Returns the reader
	 * 
	 * @return
	 */
	public HTTPReader getReader() {
		return reader;
	}

	/**
	 * Returns the answer
	 * 
	 * @return
	 */
	public String getAnswer() {
		return answer;
	}

	/**
	 * Set the value of answer
	 * 
	 * @param answer
	 */
	private void setAnswer(String answer) {
		this.answer = answer;
	}

	/**
	 * Set sendingPost to false and create a new HTTPReader for the next task
	 * 
	 * @param sc the SocketChannel used by the HTTPReader
	 */
	public void clean(SocketChannel sc) {
		setSendingPost(false);
		reader = new HTTPReader(sc, ByteBuffer.allocate(50));
	}

}
