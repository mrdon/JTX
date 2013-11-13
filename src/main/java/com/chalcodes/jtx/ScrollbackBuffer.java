package com.chalcodes.jtx;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Similar to {@link XXXScrollbackBuffer}, but without any synchronization.  Use
 * this with a {@link Display} when the buffer will be modified only in
 * the UI thread.
 *
 * @author <a href="mailto:kjkrum@gmail.com">Kevin Krumwiede</a>
 */
public class ScrollbackBuffer implements Buffer {
	protected final int[][] values;
	protected final Rectangle extents;
	protected final List<BufferObserver> observers = new ArrayList<BufferObserver>();
	
	/**
	 * Creates a new <tt>ScrollbackBuffer</tt>.  The number of columns is
	 * fixed.  The number of rows is initially zero, and may increase to the
	 * specified number of rows before the content begins scrolling.
	 * 
	 * @param columns 
	 * @param rows
	 */
	public ScrollbackBuffer(int columns, int rows) {
		values = new int[rows][columns];
		extents = new Rectangle(0, 0, columns, 0);
		// extents.y is the head...
	}
	
	@Override
	public int getContent(int column, int row) {
		if(row < extents.y || row > extents.y + extents.height) throw new IndexOutOfBoundsException();
		return values[row % values.length][column];
	}

	@Override
	public void getContent(int column, int row, int len, int[] result) {
		if(row < extents.y || row > extents.y + extents.height) throw new IndexOutOfBoundsException();
		System.arraycopy(values[row % values.length], column, result, 0, len);
	}

	@Override
	public int[] getContent(int column, int row, int len) {
		int[] result = new int[len];
		getContent(column, row, len, result);
		return result;
	}

	@Override
	public void getContent(int column, int row, int width, int height, int[][] result) {
		for(int r = 0; r < height; ++r) {
			System.arraycopy(this.values[row + r], column, result[r], 0, width);
		}
	}

	@Override
	public int[][] getContent(int column, int row, int width, int height) {
		int[][] result = new int[height][width];
		getContent(column, row, width, height, result);
		return result;
	}

	@Override
	public void setContent(int column, int row, int value) {
		if(row < extents.y) throw new IndexOutOfBoundsException();
		advance(row);
		values[row % values.length][column] = value;
		fireContentChanged(column, row, 1, 1);	
		
	}

	@Override
	public void setContent(int column, int row, int[] values, int off, int len) {
		if(off < 0 || len < 0 || off + len > values.length) throw new IllegalArgumentException();
		advance(row);
		if(column < 0) {
			len += column;
			off -= column;
			column = 0;
		}
		if(column + len > this.values[0].length) {
			len -= column + len - this.values[0].length;
		}
		if(len <= 0) return;
		System.arraycopy(values, off, this.values[row % this.values.length], column, len);
		fireContentChanged(column, row, len, 1);
	}

	@Override
	public void setContent(int column, int row, int[][] values, int width, int height) {
		if(values.length < height || !extents.contains(column, row, width, height)) {
			throw new IndexOutOfBoundsException();
		}
		for(int i = 0; i < values.length; ++i) {
			if(values[i].length < width) {
				throw new IndexOutOfBoundsException();
			}
		}
		for(int r = 0; r < height; ++r) {
			System.arraycopy(values[r], 0, this.values[row + r], column, width);
		}
		fireContentChanged(column, row, width, height);
	}

	/**
	 * A convenience method for writing a character sequence to the buffer.
	 * This method truncates any part of the sequence that falls outside the
	 * buffer extents.
	 * 
	 * @param column
	 * @param row
	 * @param seq
	 * @param off
	 * @param len
	 * @param attributes
	 */
	public void write(int column, int row, CharSequence seq, int off, int len, int attributes) {
		if(off < 0 || len < 0 || off + len > seq.length()) throw new IllegalArgumentException();
		advance(row);
		if(column < 0) {
			len += column;
			off -= column;
			column = 0;
		}
		if(column + len > values[0].length) {
			len -= column + len - values[0].length;
		}
		if(len <= 0) return;
		// set attributes
		Arrays.fill(values[row % values.length], column, column + len, attributes & 0xFFFF0000);
		// set characters
		for(int i = 0; i < len; ++i) {
			values[row % values.length][column + i] += seq.charAt(off + i);
		}
		fireContentChanged(column, row, len, 1);
	}
	
	/**
	 * A convenience method for writing a character sequence to the buffer.
	 * This method truncates any part of the sequence that falls outside the
	 * buffer extents.
	 * 
	 * @param column
	 * @param row
	 * @param seq
	 * @param attributes
	 */
	public void write(int column, int row, CharSequence seq, int attributes) {
		write(column, row, seq, 0, seq.length(), attributes);
	}	

	@Override
	public Rectangle getExtents() {
		return new Rectangle(extents);
	}

	@Override
	public boolean contains(int column, int row) {
		return extents.contains(column, row);
	}

	/**
	 * Adds the specified observer to the end of the observer list.
	 */
	@Override
	public void addBufferObserver(BufferObserver observer) {
		if(observer == null) throw new NullPointerException();		
		observers.add(observer);
	}

	/**
	 * Removes the first occurrence of the specified observer from the
	 * observer list.
	 */
	@Override
	public boolean removeBufferObserver(BufferObserver observer) {
		return observers.remove(observer);
	}
	
	/**
	 * Scrolls the buffer extents, if necessary, to include the specified row.
	 * Rows added to the bottom of the buffer are filled with a value
	 * representing the null/zero character and the default VGA attributes.
	 * <p>
	 * The buffer can only be scrolled forward.  Attempting to scroll backward
	 * (i.e., to a row less than <tt>getExtents().y</tt>) will result in a
	 * <tt>IndexOutOfBoundsException</tt>.
	 */
	public void advance(int row) {
		if(row < extents.y) throw new IndexOutOfBoundsException();
		int tail = extents.y + extents.height;		
		if(row < tail) return;
		int newRows = row + 1 - tail; // +1 because row is inclusive, tail is exclusive
		
		if(tail >= values.length) { // buffer is full and scrolling
			int clear = Math.min(newRows, values.length);
			for(int i = tail; i < tail + clear; ++i) {
				Arrays.fill(values[i % values.length], VgaBufferElement.DEFAULT_VALUE);				
			}
			extents.y += newRows;
			fireExtentsChanged(extents.x, extents.y, extents.width, extents.height);
		}
		else if(row < values.length) { // buffer is not full, and this will not make it scroll
			extents.height += newRows;
			fireExtentsChanged(extents.x, extents.y, extents.width, extents.height);
		}
		else {
			// split into two recursive calls that match the above conditions
			advance(values.length - 1);
			advance(row);
		}
	}

	void fireContentChanged(int column, int row, int width, int height) {
		int size = observers.size();
		for(int i = 0; i < size; ++i) {
			observers.get(i).contentChanged(this, column, row, width, height);
		}
	}	
	
	void fireExtentsChanged(int column, int row, int width, int height) {
		int size = observers.size();
		for(int i = 0; i < size; ++i) {
			observers.get(i).extentsChanged(this, column, row, width, height);
		}
	}	

}