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
package org.eclipse.chronograph.swt.internal.stage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.chronograph.internal.api.Area;
import org.eclipse.chronograph.internal.api.Brick;
import org.eclipse.chronograph.internal.api.Group;
import org.eclipse.chronograph.internal.api.Section;
import org.eclipse.chronograph.internal.api.providers.ContainerProvider;
import org.eclipse.chronograph.internal.api.providers.StageLabelProvider;
import org.eclipse.chronograph.internal.base.AreaImpl;
import org.eclipse.chronograph.internal.swt.BrickStyler;
import org.eclipse.chronograph.internal.swt.GroupStyler;
import org.eclipse.chronograph.internal.swt.RulerStyler;
import org.eclipse.chronograph.internal.swt.SectionStyler;
import org.eclipse.chronograph.internal.swt.StageStyler;
import org.eclipse.chronograph.internal.swt.renderers.api.ChronographStageRulerRenderer;
import org.eclipse.chronograph.internal.swt.renderers.impl.ChronographManagerRenderers;
import org.eclipse.chronograph.internal.swt.stage.ChronographStage;
import org.eclipse.chronograph.swt.stage.listeners.ChronographStageMouseListener;
import org.eclipse.chronograph.swt.stage.listeners.ChronographStageMouseWheelListener;
import org.eclipse.chronograph.swt.stage.listeners.ChronographStagePaintListener;
import org.eclipse.chronograph.swt.stage.listeners.ChronographStageResizeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;

public final class ChronographStageImpl extends Canvas implements ChronographStage {

	private static final int VERTICAL_SCROLLBAR_PAGE_INC = 15;
	private static final int SCALE_DEFAULT = 5;
	private int pX;
	private int pY;
	private int pXMax;
	private int pxlHint = 5;
	private int pXhint;
	private int pYhint;
	private int stageScale = 5;

	private List<Brick> bricks;
	private List<Brick> bricksSelected;

	private Map<String, Area> groupsAreas;
	private Map<String, Area> bricksAreas;
	private ChronographObjectRegistry registry;
	private Rectangle boundsGlobal;
	private Area visiableArea;
	private final Point originalPosition = new Point(0, 0);
	private ScrollBar scrollBarVertical;
	private ScrollBar scrollBarHorizontal;
	private StageLabelProvider labelProvider;
	private ContainerProvider dataProvider;

	private static ChronographManagerRenderers INSTANCE = ChronographManagerRenderers.getInstance();

	public ChronographStageImpl(Composite parent, int style, ContainerProvider provider) {
		super(parent, SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED | SWT.V_SCROLL | SWT.H_SCROLL);
		this.dataProvider = provider;
		this.labelProvider = provider.getLabelProvider();
		bricksSelected = new ArrayList<>();
		bricks = new ArrayList<>();
		groupsAreas = new HashMap<String, Area>();
		bricksAreas = new HashMap<String, Area>();
		setLayout(new FillLayout());
		updateStageScale();
		initListeners();
		initScrollBarHorizontal();
		initScrollBarVertical();
		initRegistry();
		calculateObjectBounds();
	}

	private void initRegistry() {
		registry = new ChronographObjectRegistry(dataProvider);
		registry.createRegistry();
	}

	private void initScrollBarHorizontal() {
		scrollBarHorizontal = getHorizontalBar();
		scrollBarHorizontal.setVisible(true);
		scrollBarHorizontal.setPageIncrement(stageScale);
		scrollBarHorizontal.setMaximum(pXMax);
		scrollBarHorizontal.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				horizontalScroll(event);
			}
		});
	}

	private void initScrollBarVertical() {

		scrollBarVertical = getVerticalBar();
		scrollBarVertical.setPageIncrement(VERTICAL_SCROLLBAR_PAGE_INC);
		scrollBarVertical.setVisible(true);
		scrollBarVertical.setMaximum(0);
		scrollBarVertical.addListener(SWT.Selection, new Listener() {
			public void handleEvent(final Event event) {
				verticalScroll(event);
			}
		});

	}

	private void initListeners() {
		ChronographStageMouseListener<ChronographStage> msListener = new ChronographStageMouseListener<ChronographStage>(
				this);
		ChronographStageMouseWheelListener<ChronographStage> msWheelListener = new ChronographStageMouseWheelListener<ChronographStage>(
				this);
		ChronographStageResizeListener<ChronographStage> resizeListener = new ChronographStageResizeListener<ChronographStage>(
				this);
		addPaintListener(new ChronographStagePaintListener(this));
		addMouseListener(msListener);
		addMouseMoveListener(msListener);
		addMouseTrackListener(msListener);
		addListener(SWT.MouseWheel, msWheelListener);
		addListener(SWT.Resize, resizeListener);
	}

	@Override
	public void verticalScroll(Event event) {

		if (event != null && event.detail == 0) {
			return;
		}
		if (!scrollBarVertical.isVisible()) {
			pYhint = 0;
			return;
		}
		pYhint = scrollBarVertical.getSelection();
		redraw();
	}

	protected void horizontalScroll(Event event) {
		if (event != null && event.detail == 0) {
			return;
		}
		if (!scrollBarHorizontal.isVisible()) {
			return;
		}
		setPositionByX(scrollBarHorizontal.getSelection() * pxlHint * stageScale);
		getHint();
		redraw();
	}

	@Override
	public void updateScrollers() {
		if (bricks == null || bricks.isEmpty()) {
			return;
		}
		Brick brick = bricks.get(bricks.size() - 1);
		scrollBarHorizontal.setMaximum((int) brick.position().end());
		scrollBarHorizontal.setSelection(pXhint);
	}

	public void handleResize() {
		Rectangle rect = getBounds();
		Rectangle client = getClientArea();
		scrollBarVertical.setMaximum(3000);
		scrollBarVertical.setThumb(Math.min(rect.height, client.height));
		int page = rect.height - client.height;
		int selection = scrollBarVertical.getSelection();
		if (selection >= page) {
			if (page <= 0) {
				selection = 0;
			}
			originalPosition.y = -selection;
		}
	}

	public void redraw(Rectangle rectangle) {
		redraw(rectangle.x, rectangle.y, rectangle.width, rectangle.height, false);
	}

	public void repaint(PaintEvent event) {
		GC gc = event.gc;
		// drawing
		Rectangle stageRectangle = ChronographStageUtil.areaToRectangle(visiableArea);
		INSTANCE.getDrawingStagePainter().draw(gc, stageRectangle);
		for (ChronographStageRulerRenderer painter : INSTANCE.getDrawingRulersPainter()) {
			painter.draw(gc, stageRectangle, stageScale, pxlHint, pXhint, pX);
		}
		for (Section section : registry.getSections()) {
			List<Group> groupsBySection = registry.getGroupBySection(section);
			for (Group group : groupsBySection) {
				List<Group> subGroups = registry.getSubGroupByGroupSection(group);
				for (Group subgroup : subGroups) {
					Area area = getDrawingArea(subgroup);
					if (area == null) {
						continue;
					}
					List<Brick> bricks = registry.getBrickBySubgroup(subgroup.id(), group.id(), section.id());
					if (bricks != null) {
						Collection<? extends Brick> selectedBriks = getSelectedObject();
						Collection<? extends Brick> markedBricks = filterBricksBySeleted(bricks, selectedBriks);
						drawSceneObjects(gc, area, bricks);
						if (!markedBricks.isEmpty()) {
							drawSelectedObjects(gc, area, markedBricks);
						}
					}
					Rectangle groupRectangle = ChronographStageUtil.areaToRectangle(area);
					INSTANCE.getDrawingGroupPainter().draw(gc, labelProvider.getText(subgroup), groupRectangle,
							getDisplay(), SectionStyler.getSectionWidth(), pYhint);
				}
				Area area = getDrawingArea(group);
				if (area == null) {
					continue;
				}
				Rectangle groupRectangle = ChronographStageUtil.areaToRectangle(area);
				INSTANCE.getDrawingGroupPainter().draw(gc, labelProvider.getText(group), groupRectangle, getDisplay(),
						SectionStyler.getSectionWidth(), pYhint);
			}
			Area area = groupsAreas.get(section.id());
			if (area == null) {
				continue;
			}
			Rectangle sectionRectangle = ChronographStageUtil.areaToRectangle(area);
			INSTANCE.getDrawingSectionPainter().draw(gc, labelProvider.getText(section), sectionRectangle, getDisplay(),
					SectionStyler.getSectionWidth(), pYhint);
		}
		// status line
		INSTANCE.getDrawingStatusPainter().draw(gc, stageRectangle, registry.getBrickActual().size(),
				registry.getBrickExpired().size(), pYhint);

	}

	private Collection<? extends Brick> filterBricksBySeleted(Collection<? extends Brick> bricks,
			Collection<? extends Brick> selectedBriks) {
		List<Brick> markedBricks = new ArrayList<Brick>();
		for (Brick selectedBrick : selectedBriks) {
			for (Brick brick : bricks) {
				if (brick.id().equals(selectedBrick.id()) && brick.position().start() == brick.position().start()) {
					markedBricks.add(brick);
				}
			}
		}
		return markedBricks;
	}

	private List<Brick> getSelectedObject() {
		return bricksSelected;
	}

	private void addDrawingArea(Group group, Area area) {
		groupsAreas.put(transformKey(group), area);
	}

	private String transformKey(Group group) {
		String key = group.id();
		if (group.container() instanceof Section) {
			Section section = (Section) group.container();
			key = section.id() + group.id();
		} else if (group.container() instanceof Group) {
			Group parent = (Group) group.container();
			key = parent.id() + group.id();
		}
		return key;
	}

	private Area getDrawingArea(Group group) {
		return groupsAreas.get(transformKey(group));
	}

	private void addDrawingArea(Brick brick, Area area) {
		bricksAreas.put(brick.id(), area);
	}

	private Area getDrawingArea(Brick brick) {
		return bricksAreas.get(brick.id());
	}

	@Override
	public Rectangle getBounds() {
		if (boundsGlobal == null) {
			return super.getBounds();
		}
		return new Rectangle(0, 0, super.getBounds().width, pY);
	}

	@Override
	public Rectangle getMainBounds() {
		return boundsGlobal;
	}

	private void calculateSectionBounds(Area area, Collection<Section> sections, int sectionSpace, int hintY) {
		int y = area.y();
		for (Section section : sections) {
			int textLength = section.id().length() * 6;
			int strHeight = textLength + sectionSpace;
			int lenghtOfGroups = 0;
			List<Group> groups = registry.getGroupBySection(section);
			for (Group group : groups) {
				List<Group> subGroups = registry.getSubGroupByGroupSection(group);
				if (subGroups.isEmpty()) {
					String groupLabel = labelProvider.getText(group);
					int textByX = groupLabel.length() * 6;
					if (textByX < GroupStyler.GROUP_HEIGHT_DEFAULT) {
						textByX = GroupStyler.GROUP_HEIGHT_DEFAULT;
					}
					lenghtOfGroups += textByX;
				} else {
					lenghtOfGroups += subGroups.size() * GroupStyler.GROUP_HEIGHT_DEFAULT;
				}
			}
			int height = 10 + Math.max(lenghtOfGroups, strHeight);
			Area sectionArea = new AreaImpl(area.x(), y, area.width(), height);
			groupsAreas.put(section.id(), sectionArea);
			y += height + sectionSpace;
		}
	}

	@Override
	public void calculateObjectBounds() {
		Rectangle clientArea = super.getClientArea();
		visiableArea = new AreaImpl(clientArea.x, clientArea.y, clientArea.width, clientArea.height);
		Area frameArea = new AreaImpl(visiableArea.x(), visiableArea.y() + StageStyler.getStageHeaderHeight() - pYhint,
				visiableArea.width() - 10,
				visiableArea.height() - StageStyler.getStageHeaderHeight() - RulerStyler.RULER_DAY_HEIGHT
						- RulerStyler.RULER_MOUNTH_HEIGHT - RulerStyler.RULER_YEAR_HEIGHT);
		List<Section> sections = registry.getSections();
		calculateSectionBounds(frameArea, sections, SectionStyler.getSectionSeparatorHeight(), pYhint);
		for (Section section : sections) {
			List<Group> groupsBySection = registry.getGroupBySection(section);
			calculateGroupBound(groupsBySection, groupsAreas.get(section.id()), section);
		}

	}

	private void calculateGroupBound(List<? extends Group> groups, Area area, Section section) {
		if (area == null) {
			return;
		}
		int heightDelta = area.height() / groups.size();
		for (Group group : groups) {
			int groupIndex = groups.indexOf(group);
			Area areaGroup = new AreaImpl(area.x() + 30, area.y() + (groupIndex * heightDelta), area.width() + 30,
					heightDelta);
			addDrawingArea(group, areaGroup);
			List<Group> subGroups = registry.getSubGroupByGroupSection(group);
			for (Group subgroup : subGroups) {
				int subGroupIndex = subGroups.indexOf(subgroup);
				Area areaSubGroup = new AreaImpl(areaGroup.x() + 30,
						areaGroup.y() + (subGroupIndex * areaGroup.height() / subGroups.size()), areaGroup.width() + 30,
						areaGroup.height() / subGroups.size());
				addDrawingArea(subgroup, areaSubGroup);
			}
		}
	}

	private Brick calculateObjectPosition(Brick brick, Area area, int hintX, int hintY, int hintWidth) {
		if (area == null) {
			return brick;
		}
		int pixelWitdh = (int) brick.position().duration() * hintWidth;
		int pointX = (int) brick.position().start() * hintWidth - (hintX * hintWidth);
		int pointY = area.y() + BrickStyler.getHeight() - hintY;
		Area brickArea = new AreaImpl(pointX, pointY, pixelWitdh, BrickStyler.getHeight());
		addDrawingArea(brick, brickArea);
		return brick;
	}

	private void drawSceneObjects(final GC gc, Area area, final Collection<? extends Brick> bricks) {
		if (area == null) {
			return;
		}
		bricks.stream().forEach(new  Consumer<Brick>() {
			public void accept(Brick brick) {
				calculateObjectPosition(brick, area, pXhint, pYhint, pxlHint);
				Area brickArea = getDrawingArea(brick);
				if (brickArea != null) {
					Rectangle rectangleArea = ChronographStageUtil.areaToRectangle(brickArea);
					INSTANCE.getContentPainter().draw(brick, gc, rectangleArea, pYhint);
					String label = labelProvider.getText(brick);
					INSTANCE.getLabelPainter().drawLabel(label, brick.position(), gc, rectangleArea, pYhint);
					INSTANCE.getDurationPainter().drawObjectDuration(brick, gc, pYhint);
				}
			};
		});
	}

	private void drawSelectedObjects(final GC gc, Area area, final Collection<? extends Brick> bricks) {
		if (area == null) {
			return;
		}
		for (Brick brick : bricks) {
			calculateObjectPosition(brick, area, pXhint, pYhint, pxlHint);
			Area brickArea = getDrawingArea(brick);
			if (brickArea == null) {
				continue;
			}
			Rectangle rectangleArea = ChronographStageUtil.areaToRectangle(brickArea);
			INSTANCE.getSelectedContentPainter().draw(brick, gc, rectangleArea, pYhint);
			String label = labelProvider.getText(brick);
			INSTANCE.getLabelPainter().drawLabel(label, brick.position(), gc, rectangleArea, pYhint);
			INSTANCE.getDurationPainter().drawObjectDuration(brick, gc, pYhint);
		}
	}

	@Override
	public void navigateToUnit(int hint) {
		pX = hint * pxlHint * stageScale;
		getHint();
		redraw();
	}

	public void clearSceneObjects() {
		checkWidget();
		bricks.clear();
		pYhint = 0;
		scrollBarVertical.setSelection(0);
		redraw();
	}

	public void clearGroups() {
		checkWidget();
		pYhint = 0;
		scrollBarVertical.setSelection(0);
		redraw();
	}

	public void clearSections() {
		checkWidget();
		pYhint = 0;
		scrollBarVertical.setSelection(0);
		redraw();
	}

	@Override
	public int getScale() {
		return stageScale;
	}

	@Override
	public List<? extends Brick> getDrawingObjects() {
		return registry.getBricks();
	}

	private void updateStageScale() {
		if (stageScale <= 0) {
			stageScale = 1;
			pxlHint = stageScale * 2;
		}
		if (stageScale == 2 || stageScale == 3) {
			pxlHint = stageScale * 2;
		}
		if (stageScale == 4) {
			pxlHint = stageScale * 3;
		}
		if (stageScale > 4) {
			pxlHint = stageScale * SCALE_DEFAULT;
		}
	}

	@Override
	public void scaleUp() {
		checkWidget();
		stageScale--;
		updateStageScale();
		redraw();
		updateScrollers();
		navigateToUnit(pXhint);
	}

	@Override
	public void scaleDown() {
		checkWidget();
		stageScale++;
		updateStageScale();
		redraw();
		navigateToUnit(pXhint);
	}

	@Override
	public void redraw() {
		super.redraw();
	}

	@Override
	public int getPositionByX() {
		return pX;
	}

	@Override
	public void setPositionByX(int x) {
		this.pX = x;
	}

	public Rectangle getClientArea() {
		return super.getClientArea();
	}

	@Override
	public void getHint() {
		pXhint = (int) (pX / (pxlHint * stageScale));
	}

	@Override
	public void setLayoutData(GridData gridData) {
		super.setLayoutData(gridData);

	}

	@Override
	public void setInput(List<?> objects) {
	}

	@Override
	public Area getBrickArea(Brick brick) {
		return getDrawingArea(brick);
	}

	public void addSelected(Brick brick) {
		bricksSelected.add(brick);
	}

	public void removeSelected(Brick brick) {
		bricksSelected.remove(brick);
	}

	@Override
	public void addRemoveSelected(Brick brick) {
		List<Brick> markedBriks = new ArrayList<Brick>();
		for (Brick selectedBrick : bricksSelected) {
			if (selectedBrick.id().equals(brick.id())) {
				markedBriks.add(selectedBrick);
			}
		}
		if (!markedBriks.isEmpty()) {
			for (Brick marked : markedBriks) {
				bricksSelected.remove(marked);
			}
		} else {
			bricksSelected.add(brick);
		}
	}

	@Override
	public void setProvider(ContainerProvider provider) {
		this.dataProvider = provider;
		this.labelProvider = provider.getLabelProvider();
		this.registry = new ChronographObjectRegistry(dataProvider);
		this.registry.createRegistry();
		this.calculateObjectBounds();
		this.redraw();
	}
}