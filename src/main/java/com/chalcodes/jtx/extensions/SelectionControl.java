package com.chalcodes.jtx.extensions;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JViewport;

import com.chalcodes.jtx.Buffer;
import com.chalcodes.jtx.Display;
import com.chalcodes.jtx.VgaBufferElement;

/**
 * A selection control for a {@link Display} attached to a {@link Buffer}
 * containing elements encoded with {@link VgaBufferElement}.
 *
 * @author <a href="mailto:kjkrum@gmail.com">Kevin Krumwiede</a>
 */
public class SelectionControl {
	private final Display display;
	private final Buffer buffer;
	private final JViewport viewport;
	/** Buffer coordinates where button was pressed; may be outside buffer extents */
	private final Point anchor = new Point();
	/** Buffer coordinates where pointer was dragged; may be outside buffer extents */
	private final Point handle = new Point();
	/** The previously calculated selection */
	private Rectangle activeSelection;

	/**
	 * Sets up a new selection control.  The control adds listeners to the
	 * viewport and its client, which must be a {link Display}.  This is
	 * because the client may be smaller than the viewport, and we want to
	 * respond to click-drags into the client from anywhere in the viewport.
	 *  
	 * @param viewport the viewport
	 * @throws IllegalArgumentException if the viewport's client is not a
	 * {@link Display}
	 */
	public SelectionControl(JViewport viewport) {
		if(viewport.getView() instanceof Display) {
			display = (Display) viewport.getView();
			buffer = display.getBuffer();				
		}
		else {
			throw new IllegalArgumentException("viewport's client is not a Display");
		}
		this.viewport = viewport;
		final MouseAdapter mouseAdapter = new SelectionMouseAdapter();
		viewport.addMouseListener(mouseAdapter);
		viewport.addMouseMotionListener(mouseAdapter);
		display.addMouseListener(mouseAdapter);
		display.addMouseMotionListener(mouseAdapter);
	}
	
	private class SelectionMouseAdapter extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent e) {
			if(e.getButton() == MouseEvent.BUTTON1) {
				if(activeSelection != null) {
					setSelection(activeSelection, false);
					activeSelection = null;
				}
				Point p = e.getPoint();
				if(e.getComponent() == viewport) {
					transformToClientCoords(p);
				}
				display.getBufferCoordinates(p, anchor);
//				System.out.println("pressed at " + anchor);
			}
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			// TODO check which button is down?
			Point p = e.getPoint();
			if(e.getComponent() == viewport) {
				transformToClientCoords(p);
			}
			display.getBufferCoordinates(p, handle);
//			System.out.println("anchor: " + anchor + " handle: " + handle);
			if(activeSelection == null) {
				activeSelection = createRectangle(anchor, handle);
				setSelection(activeSelection, true);
			}
			else {
				Rectangle newSelection = createRectangle(anchor, handle);
				if(!newSelection.equals(activeSelection)) {
					setSelection(activeSelection, false);	
					setSelection(newSelection, true);
					activeSelection = newSelection;
				}
			}		
		}
	}
	
	/**
	 * Transforms a point from the viewport's coordinate space into the
	 * client's coordinate space.
	 * 
	 * @param point
	 * @param result
	 */
	private void transformToClientCoords(Point point) {
		Point p = viewport.getViewPosition();
		point.x += p.x;
		point.y += p.y;
	}
	
	/**
	 * Use this to clear old selection or create new one.
	 * 
	 * @param selection
	 * @param selected
	 */
	private void setSelection(Rectangle selection, boolean selected) {
//		System.out.println((selected ? "selecting " : "clearing ") + selection);
		Rectangle union = selection.intersection(buffer.getExtents());
//		System.out.println("selection ∩ extents: " + union);
		if(!union.isEmpty()) {
			int[][] copied = buffer.getContent(union.x, union.y, union.width, union.height);
			for(int r = 0; r < copied.length; ++r) {
				for(int c = 0; c < copied[r].length; ++c) {
					copied[r][c] = VgaBufferElement.setSelected(copied[r][c], selected);
//					System.out.println("copied[" + r + "][" + c + "] == " + Integer.toHexString(copied[r][c]));
				}
			}
			buffer.setContent(union.x, union.y, copied, union.width, union.height);
		}
	}
	
//	/**
//	 * Use this when selection already exists during drag.
//	 * 
//	 * @param oldSelection
//	 * @param newSelection
//	 */
//	private void replaceSelection(Rectangle oldSelection, Rectangle newSelection) {
//		System.out.printf("replacing %s with %s\n", oldSelection, newSelection);
//		if(!oldSelection.equals(newSelection)) {
//			Rectangle union = oldSelection.union(newSelection).intersection(buffer.getExtents());
//			System.out.println("(oldSelection ∪ newSelection) ∩ extents: " + union);
//			if(!union.isEmpty()) {
//				int[][] copied = buffer.getContent(union.x, union.y, union.width, union.height);
//				for(int r = 0; r < copied.length; ++r) {
//					for(int c = 0; c < copied[r].length; ++c) {
//						if(oldSelection.contains(union.x + c, union.y + r) && !newSelection.contains(union.x + c, union.y + r)) {
//							copied[r][c] = VgaBufferElement.setSelected(copied[r][c], false);
//							System.out.println("union[" + r + "][" + c + "] == " + Integer.toHexString(copied[r][c]));
//						}
//						else if(newSelection.contains(union.x + c, union.y + r) && !oldSelection.contains(union.x + c, union.y + r)) {
//							copied[r][c] = VgaBufferElement.setSelected(copied[r][c], true);
//							System.out.println("union[" + r + "][" + c + "] == " + Integer.toHexString(copied[r][c]));
//						}
//					}
//				}
//				buffer.setContent(union.x, union.y, copied, union.width, union.height);
//			}
//		}
//	}
	
	/**
	 * Creates the smallest rectangle containing both of the specified points.
	 * The rectangle will be arranged so its width and height are positive.
	 * 
	 * @param p0 the first point
	 * @param p1 the second point
	 * @return the rectangle
	 */
	private Rectangle createRectangle(Point p0, Point p1) {
		Rectangle rect = new Rectangle(p0.x, p0.y, p1.x - p0.x, p1.y - p0.y);
		if(rect.width < 0) {
			rect.x += rect.width;
			rect.width = -rect.width;			
		}
		if(rect.height < 0) {
			rect.y += rect.height;
			rect.height = -rect.height;
		}
		++rect.width;
		++rect.height;
		return rect;
	}
}
