package krum.jtx.demo;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import krum.jtx.ScrollbackBuffer;
import krum.jtx.VGABufferElement;
import krum.jtx.demo.lexer.DemoEventListener;

/**
 * Parser for a simple demo emulation.  This parser processes events from the
 * emulation lexer that was generated from DemoLexer.jplex in the root of
 * the source tree.  Everything in the <tt>demo.lexer</tt> package was
 * generated by <a href="https://github.com/kjkrum/JPlex/">JPlex</a>.
 */
public class DemoEmulation implements DemoEventListener {
	/** The buffer on which this emulation will operate. */
	protected final ScrollbackBuffer buffer;
	/** The cursor position. */
	protected final Point cursor;
	/** Width of the buffer. */
	protected final int columns;
	/** The maximum <tt>y</tt> value the cursor has ever had. */
	protected int maxLine;
	/** For manipulating character attributes. */
	protected int attributes = VGABufferElement.DEFAULT_VALUE;
	/**
	 * The cursor <tt>y</tt> value after the last page clear.  The cursor up
	 * command cannot move the cursor above this mark.  This is because the
	 * clear page command does not actually clear anything; it just moves the
	 * cursor to a new line.
	 */
	protected int pageMark;
	/** The cursor position at the last save cursor command. */
	protected final Point cursorMark;
	
	public DemoEmulation(ScrollbackBuffer buffer) {
		this.buffer = buffer;
		Rectangle extents = buffer.getExtents();		
		columns = extents.width;
		// position cursor to new row at bottom of buffer
		cursor = new Point(0, extents.x + extents.height);
		maxLine = cursor.y;
		buffer.advance(maxLine);
		pageMark = cursor.y;
		cursorMark = new Point(cursor);
	}
	
	@Override
	public void cursorPosition(CharSequence seq, int off, int len) {
		List<Integer> params = scanParams(seq, off, len);
		switch(params.size()) {
		// note: ansi params are row, col; setLocation params are col, row 
		case 0:
			cursor.setLocation(0, pageMark);
			break;
		case 1:
			cursor.setLocation(0, pageMark + params.get(0) - 1);
			break;
		case 2:
			cursor.setLocation(params.get(1) - 1, pageMark + params.get(0) - 1);
			break;
		}
		if(cursor.x < 0) cursor.x = 0;
		if(cursor.x >= columns) cursor.x = columns - 1;
		if(cursor.y < pageMark) cursor.y = pageMark;
		if(cursor.y > maxLine) {
			buffer.advance(cursor.y);
			maxLine = cursor.y;
		}		
	}

	@Override
	public void cursorUp(CharSequence seq, int off, int len) {
		List<Integer> params = scanParams(seq, off, len);
		if(params.size() == 0) --cursor.y;
		else if(params.size() == 1) cursor.y -= params.get(0);
		if(cursor.y < pageMark) cursor.y = pageMark;
	}

	@Override
	public void cursorDown(CharSequence seq, int off, int len) {
		List<Integer> params = scanParams(seq, off, len);
		if(params.size() == 0) ++cursor.y;
		else if(params.size() == 1) cursor.y += params.get(0);
		if(cursor.y > maxLine) {
			buffer.advance(cursor.y);
			maxLine = cursor.y;
		}
	}

	@Override
	public void cursorRight(CharSequence seq, int off, int len) {
		List<Integer> params = scanParams(seq, off, len);
		if(params.size() == 0) ++cursor.x;
		else if(params.size() == 1) cursor.x += params.get(0);
		if(cursor.x >= columns) cursor.x = columns - 1;	
	}

	@Override
	public void cursorLeft(CharSequence seq, int off, int len) {
		List<Integer> params = scanParams(seq, off, len);
		if(params.size() == 0) --cursor.x;
		else if(params.size() == 1) cursor.x -= params.get(0);
		if(cursor.x < 0) cursor.x = 0;
	}

	@Override
	public void saveCursor(CharSequence seq, int off, int len) {
		cursorMark.setLocation(cursor);		
	}

	@Override
	public void restoreCursor(CharSequence seq, int off, int len) {
		// TODO: check buffer extents?
		if(cursorMark.y < pageMark) return;
		cursor.setLocation(cursorMark);
	}

	@Override
	public void clearScreen(CharSequence seq, int off, int len) {
		++maxLine;
		buffer.advance(maxLine);
		pageMark = maxLine;
		cursor.setLocation(0, maxLine);	
	}

	@Override
	public void clearLine(CharSequence seq, int off, int len) {
		int[] values = new int[columns - cursor.x];
		Arrays.fill(values, VGABufferElement.DEFAULT_VALUE);
		buffer.setContent(cursor.x, cursor.y, values, 0, values.length);		
	}

	@Override
	public void setAttributes(CharSequence seq, int off, int len) {
		List<Integer> params = scanParams(seq, off, len);
		for(int param : params) {
			switch(param) {
			case 0:
				attributes = VGABufferElement.DEFAULT_VALUE;
				break;
			case 1:
				attributes = VGABufferElement.setBright(attributes, true);
				break;
			case 2:
				attributes = VGABufferElement.setBright(attributes, false);
				break;
			case 4:
				attributes = VGABufferElement.setUnderlined(attributes, true);
				break;
			case 5:
				attributes = VGABufferElement.setBlinking(attributes, true);
				break;
			case 7:
				attributes = VGABufferElement.setInverted(attributes, true);
				break;
			case 30:
			case 31:
			case 32:
			case 33:
			case 34:
			case 35:
			case 36:
			case 37:
				attributes = VGABufferElement.setForegroundColor(attributes, param - 30);
				break;
			case 40:
			case 41:
			case 42:
			case 43:
			case 44:
			case 45:
			case 46:
			case 47:
				attributes = VGABufferElement.setBackgroundColor(attributes, param - 40);
				break;
			default:
				System.err.printf("unrecognized attribute: %d\n", param);
			}
		}
		
	}

	@Override
	public void unknownEscape(CharSequence seq, int off, int len) {
		System.err.printf("unrecognized escape sequence: ESC%s\n", seq.subSequence(off + 1, off + len));
	}

	@Override
	public void lineFeed(CharSequence seq, int off, int len) {
		++cursor.y;
		if(cursor.y > maxLine) {
			++maxLine;
			buffer.advance(maxLine);
		}
	}

	@Override
	public void carriageReturn(CharSequence seq, int off, int len) {
		cursor.x = 0;		
	}

	@Override
	public void backspace(CharSequence seq, int off, int len) {
		--cursor.x;
		if(cursor.x < 0) cursor.x = 0;		
	}

	@Override
	public void tab(CharSequence seq, int off, int len) {
		// arbitrarily using a tab width of 4
		cursor.x += 4 - cursor.x % 4;
		if(cursor.x >= columns) cursor.x = columns - 1;		
	}

	@Override
	public void bell(CharSequence seq, int off, int len) {
		// could make a bell sound
	}

	@Override
	public void literalText(CharSequence seq, int off, int len) {
		// TODO: line wrap?
		buffer.write(cursor.x, cursor.y, seq, off, len, attributes);
		// advance the cursor
		cursor.x += len;
		if(cursor.x == columns) {
			cursor.x = 0;
			++cursor.y;
			if(cursor.y > maxLine) {
				++maxLine;
				buffer.advance(maxLine);
			}
		}
	}
	
	protected List<Integer> scanParams(CharSequence seq, int off, int len) {
		List<Integer> list = new ArrayList<Integer>();
		
		int begin = -1;
		boolean inNumber = false;
		for(int i = off; i < off + len; ++i) {
			if(inNumber) {
				inNumber = Character.isDigit(seq.charAt(i));
				if(!inNumber) {
					list.add(Integer.parseInt(seq.subSequence(begin, i).toString()));
				}
			}
			else if(seq.charAt(i) == ';') { // blank param
				list.add(1); // ANSI screen coordinates are 1-based
			}
			else if (Character.isDigit(seq.charAt(i))) {
				inNumber = true;
				begin = i;
			}
		}		
		
		return list;
	}

}