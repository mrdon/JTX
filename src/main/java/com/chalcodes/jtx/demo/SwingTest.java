package com.chalcodes.jtx.demo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.chalcodes.jtx.VgaBufferElement;

/**
 * A demo that spawns a thread to write to the Buffer, queueing tasks in the
 * Swing thread to perform the writes.  Includes a small delay between writes.
 * Comment out the delay to test the maximum write speed.  On my system,
 * writing with no delay makes it hard for Swing to squeeze in a repaint.
 */
public class SwingTest extends BasicDemo {
	private static final long serialVersionUID = 1L;
	private static final int BUFFER_LINES = 1000;

	public SwingTest() throws IOException {
		super(BUFFER_LINES, false);
		setTitle("JTX Swing Scrolling Test");
	}
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					final SwingTest speedTest = new SwingTest();
					speedTest.setVisible(true);
					
					JOptionPane.showMessageDialog(
							speedTest,
							"Click to begin.",
							"Swing Test",
							JOptionPane.PLAIN_MESSAGE
						);
					
					new Thread(new Runnable() {
						@Override
						public void run() {
							// set up
							final int lines = 1000;
							final Random rand = new Random();
							final String msg = "All work and no play makes Jack a dull boy.";
							final int[] content = new int[msg.length()];
							for(int i = 0; i < content.length; ++i) {
								content[i] = VgaBufferElement.setCharacter(0, msg.charAt(i));
							}
							
							// main loop
							final long begin = System.currentTimeMillis();
							for(int i = 0; i < lines; ++i) {
//								if(i == BUFFER_LINES) {
//									System.out.println("buffer scrolling");
//								}
								// get a random color attribute
								int attr = rand.nextInt(0x7F) << 16;
								// ensure foreground and background aren't the same color
								if(!VgaBufferElement.isBright(attr) && VgaBufferElement.getForegroundColor(attr) == VgaBufferElement.getBackgroundColor(attr)) {
									// invert the foreground color if they are
									attr ^= 0x70000;
								}
								for(int j = 0; j < content.length; ++j) {
									content[j] = VgaBufferElement.setColor(content[j], attr);
								}
								// fire off the update in the Swing thread
								final int _i = i;
								final int[] _content = Arrays.copyOf(content, content.length);
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										speedTest.buffer.setContent(0, _i, _content, 0, _content.length);
									}
								});
								
								// uncomment to experiment with scrolling behavior
								try {
									Thread.sleep(1);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
							final long end = System.currentTimeMillis();
							
							// show report
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									JOptionPane.showMessageDialog(
											speedTest,
											"Wrote " + lines + " lines in " + (end - begin) + " ms.",
											"Swing Test",
											JOptionPane.INFORMATION_MESSAGE
											);
								}
							});
						}						
					}).start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
}
