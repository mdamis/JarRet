package upem.jarret.server;

import java.nio.ByteBuffer;

public class Attachment {
	private ByteBuffer bb = ByteBuffer.allocate(50);
	private boolean requestingTask = false;
	private boolean sendingPost = false;

	public ByteBuffer getBb() {
		return bb;
	}

	public void requestTask() {
		setRequestingTask(true);
	}

	public boolean isRequestingTask() {
		return requestingTask;
	}

	private void setRequestingTask(boolean requestingTask) {
		this.requestingTask = requestingTask;
	}

	public void requestAnswer() {
		setSendingPost(true);
	}

	public boolean isSendingPost() {
		return sendingPost;
	}

	private void setSendingPost(boolean sendingPost) {
		this.sendingPost = sendingPost;
	}

	public void sendCheckCode() {
		setSendingPost(false);
		// TODO Auto-generated method stub
		
	}

	public void sendTask() {
		setRequestingTask(false);
		// TODO Auto-generated method stub
		
	}
	
}
