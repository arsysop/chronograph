/*******************************************************************************
 *	Copyright (c) 2020 ArSysOp
 *
 *	This program and the accompanying materials are made available under the
 *	terms of the Eclipse Public License 2.0 which is available at
 *	http://www.eclipse.org/legal/epl-2.0.
 *
 *	SPDX-License-Identifier: EPL-2.0
 *
 *	Contributors:
 *	Sergei Kovalchuk <sergei.kovalchuk@arsysop.ru> - 
 *												initial API and implementation
 *******************************************************************************/
package org.eclipse.chronograph.internal.swt.stage;

import java.util.Optional;

import org.eclipse.chronograph.internal.api.graphics.Brick;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tracker;

/**
 * 
 * Grouping of implementations for {@link MouseListener}'s
 *
 * @param <T>
 */
final class StageMouse<D> implements MouseListener, MouseMoveListener, MouseTrackListener {

	private Stage<D> stage;
	private static final Cursor CURSOR_NONE = new Cursor(Display.getDefault(), SWT.NONE);
	private static final Cursor CURSOR_HAND = new Cursor(Display.getDefault(), SWT.CURSOR_HAND);
	private static final Cursor CURSOR_NAVIGATION = new Cursor(Display.getDefault(), SWT.CURSOR_SIZEWE);
	private Point startPoint;
	private Tracker tracker;
	private boolean isMouseDown;
	private int xPosition = 0;

	public StageMouse(Stage<D> stage) {
		this.stage = stage;
	}

	@Override
	public void mouseMove(MouseEvent me) {
		try {
			if (isMouseDown) {
				if (startPoint == null) {
					startPoint = new Point(me.x, me.y);
				}
				int xDiff = me.x - startPoint.x;
				int deltaXPosition = xPosition - (xDiff * stage.getScale());
				if (deltaXPosition < 0) {
					deltaXPosition = 0;
				}
				stage.setPositionByX(deltaXPosition);
				stage.applyHint();
				stage.redraw();
//				stage.updateScrollers();

			}

		} catch (final Exception error) {
			SWT.error(SWT.ERROR_UNSPECIFIED, error);
		}
	}

	@Override
	public void mouseEnter(MouseEvent e) {
	}

	@Override
	public void mouseExit(MouseEvent e) {
	}

	@Override
	public void mouseHover(MouseEvent me) {
		if (me.stateMask != 0) {
			return;
		}
		Rectangle mainBounds = stage.getMainBounds();
		if (mainBounds == null || me.x >= mainBounds.x) {
			stage.brickAt(me.x, me.y).ifPresent(b -> stage.setCursor(CURSOR_HAND));
		}
	}

	@Override
	public void mouseDoubleClick(final MouseEvent me) {
		isMouseDown = false;
	}

	@Override
	public void mouseDown(final MouseEvent me) {
		if (me.button == 1) {
			isMouseDown = true;
			xPosition = stage.getPositionByX();
		}
		Optional<Brick<D>> underBrick = stage.brickAt(me.x, me.y);
		if (underBrick.isPresent()) {
			stage.setCursor(CURSOR_HAND);
			stage.select(underBrick.get());
		} else {
			stage.setCursor(CURSOR_NAVIGATION);
		}

	}

	@Override
	public void mouseUp(final MouseEvent event) {
		isMouseDown = false;
		if (tracker != null && !tracker.isDisposed()) {
			tracker.dispose();
		}
		endEverything();
	}

	public void endEverything() {
		if (tracker != null && !tracker.isDisposed()) {
			tracker.dispose();
			isMouseDown = false;
		}
		if (isMouseDown) {
			return;
		}
		isMouseDown = false;
		startPoint = null;
		stage.setCursor(CURSOR_NONE);

	}

}
