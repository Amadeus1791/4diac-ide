/*******************************************************************************
 * Copyright (c) 2019, 2021 Johannes Kepler University Linz,
 *                          Primetals Technologies Austria GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Alois Zoitl - initial API and implementation and/or initial documentation
 *               - keep connection draging within canvas bounds
 *******************************************************************************/
package org.eclipse.fordiac.ide.gef.tools;

import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.fordiac.ide.gef.figures.HideableConnection;
import org.eclipse.fordiac.ide.gef.router.MoveableRouter;
import org.eclipse.fordiac.ide.model.commands.create.AbstractConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.libraryElement.Application;
import org.eclipse.fordiac.ide.model.libraryElement.AutomationSystem;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.fordiac.ide.model.ui.editors.AdvancedScrollingGraphicalViewer;
import org.eclipse.fordiac.ide.ui.UIPlugin;
import org.eclipse.fordiac.ide.ui.preferences.ConnectionPreferenceValues;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.requests.CreateConnectionRequest;
import org.eclipse.gef.requests.SelectionRequest;
import org.eclipse.gef.tools.ConnectionDragCreationTool;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.widgets.Display;

public class FordiacConnectionDragCreationTool extends ConnectionDragCreationTool {

	// Safety border around the canvas to ensure that during dragging connections
	// the canvas is not growing
	private static final Insets NEW_CONNECTION_CANVAS_BORDER = new Insets(1,
			1 + MoveableRouter.MIN_CONNECTION_FB_DISTANCE_SCREEN + HideableConnection.BEND_POINT_BEVEL_SIZE, 1,
			1 + MoveableRouter.MIN_CONNECTION_FB_DISTANCE_SCREEN + HideableConnection.BEND_POINT_BEVEL_SIZE);

	// Fields to support drag-to-create functionality
	private EditPart sourceEditPart;
	private Object sourceModel;
	private Point canvasDropLocation;

	public FordiacConnectionDragCreationTool() {
		setDefaultCursor(Display.getDefault().getSystemCursor(SWT.CURSOR_CROSS));
		setDisabledCursor(Display.getDefault().getSystemCursor(SWT.CURSOR_NO));
	}

	@Override
	public void deactivate() {
		stopHover();
		// Clean up drag-to-create state
		sourceEditPart = null;
		sourceModel = null;
		canvasDropLocation = null;
		super.deactivate();
	}

	@Override
	public void mouseDrag(final MouseEvent me, final EditPartViewer viewer) {
		if (isActive() && viewer instanceof final AdvancedScrollingGraphicalViewer advViewer) {
			advViewer.checkScrollPositionDuringDragBounded(me,
					new Point(MoveableRouter.MIN_CONNECTION_FB_DISTANCE_SCREEN
							+ HideableConnection.BEND_POINT_BEVEL_SIZE + ConnectionPreferenceValues.HANDLE_SIZE,
							ConnectionPreferenceValues.HANDLE_SIZE));
			CanvasHelper.bindToContentPane(me, advViewer, NEW_CONNECTION_CANVAS_BORDER);
		}
		super.mouseDrag(me, viewer);
	}

	@Override
	protected boolean handleButtonUp(final int button) {
		if (button == 1 && isDroppedOnCanvas()) {
			handleCanvasDrop();
			eraseTargetFeedback();
			setState(STATE_INITIAL);
			return true;
		}
		return super.handleButtonUp(button);
	}

	@Override
	protected boolean handleDragStarted() {
		super.handleDragStarted();
		// Capture source after drag has started - at this point GEF has set up the
		// connection request
		captureSourceFromRequest();
		return true;
	}

	/**
	 * Capture the source edit part from the connection request. This is called
	 * after GEF has initialized the connection creation.
	 */
	private void captureSourceFromRequest() {
		if (getTargetRequest() instanceof final CreateConnectionRequest request) {
			sourceEditPart = request.getSourceEditPart();
			if (sourceEditPart != null) {
				sourceModel = sourceEditPart.getModel();
			}
		}
	}

	@Override
	public void mouseUp(final MouseEvent me, final EditPartViewer viewer) {
		if (((me.stateMask & SWT.MOD2) != 0)) {
			checkCurrentCommandforShiftMask();
		}
		super.mouseUp(me, viewer);
	}

	private void checkCurrentCommandforShiftMask() {
		final Command curCmd = getCurrentCommand();
		if (curCmd instanceof final AbstractConnectionCreateCommand conCreateCmd) {
			conCreateCmd.setVisible(false);
		}
	}

	@Override
	protected void setCurrentCommand(final Command c) {
		if (null == getCurrentCommand() && null != c) {
			// Hover started
			startHover();
		} else if (null != getCurrentCommand() && null != c && c != getCurrentCommand()) {
			// Hover changed
			stopHover();
			startHover();
		} else if (null != getCurrentCommand() && null == c) {
			// Hover stopped
			stopHover();
		}
		super.setCurrentCommand(c);
	}

	/**
	 * Check if the connection was dropped on empty canvas (not on a valid port).
	 * This indicates a drag-to-create scenario.
	 *
	 * @return true if dropped on canvas, false if dropped on valid connection
	 *         target
	 */
	private boolean isDroppedOnCanvas() {
		final EditPart target = getTargetEditPart();
		return sourceEditPart != null && (target == null || !isValidConnectionTarget(target));
	}

	/**
	 * Check if the target edit part represents a valid connection endpoint (port).
	 *
	 * @param target the target edit part to check
	 * @return true if target is a valid port for connection
	 */
	private boolean isValidConnectionTarget(final EditPart target) {
		if (target == null || target.getModel() == sourceModel) {
			return false;
		}
		return target.getModel() instanceof IInterfaceElement;
	}

	/**
	 * Handle the canvas drop scenario for drag-to-create functionality. Currently
	 * stores the drop location for future implementation. TODO: Will be enhanced in
	 * future commits to show element creation dialog.
	 */
	private void handleCanvasDrop() {
		canvasDropLocation = getLocation().getCopy();
		System.out.println("Canvas drop detected - source: " + sourceModel);

		// Get the viewer
		final GraphicalViewer viewer = (GraphicalViewer) getCurrentViewer();
		if (viewer == null) {
			return;
		}

		// Get the FBNetwork edit part (the canvas)
		final EditPart rootEditPart = viewer.getRootEditPart().getContents();
		if (rootEditPart == null || !(rootEditPart instanceof final GraphicalEditPart graphicalEditPart)) {
			return;
		}

		// Get type library
		TypeLibrary typeLibrary = getTypeLibraryFromEditPart(graphicalEditPart);
		if (typeLibrary == null) {
			System.out.println("ERROR: Could not get type library");
			return;
		}

		// Create and show the direct edit manager (same as double-click does)
		showTypeSelectionUI(graphicalEditPart, typeLibrary);
	}

	private TypeLibrary getTypeLibraryFromEditPart(final GraphicalEditPart editPart) {
		final Object model = editPart.getModel();
		if (model instanceof FBNetwork) {
			final FBNetwork network = (FBNetwork) model;
			final AutomationSystem automationSystem = network.getAutomationSystem();
			if (automationSystem != null) {
				return automationSystem.getTypeLibrary();
			}
			final Application application = network.getApplication();
			if (application != null && application.getAutomationSystem() != null) {
				return application.getAutomationSystem().getTypeLibrary();
			}
		}
		return null;
	}

	private void showTypeSelectionUI(final GraphicalEditPart editPart, final TypeLibrary typeLibrary) {
		// This is tricky - NewInstanceDirectEditManager is in application plugin
		// We need to trigger it via the existing EditPolicy mechanism

		// Try to get the policy by role
		final Object policy = editPart.getEditPolicy(EditPolicy.DIRECT_EDIT_ROLE);

		if (policy != null) {
			try {
				// Use reflection to call performDirectEdit
				final java.lang.reflect.Method method = policy.getClass().getMethod("performDirectEdit",
						org.eclipse.gef.requests.SelectionRequest.class);

				final SelectionRequest request = new SelectionRequest();
				request.setLocation(canvasDropLocation.getCopy());

				method.invoke(policy, request);
				System.out.println("Triggered FORDIAC type selection");

			} catch (Exception e) {
				System.out.println("ERROR: Could not trigger direct edit: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			System.out.println("ERROR: No DIRECT_EDIT_ROLE policy found");
		}
	}

	private static void startHover() {
		UIPlugin.getDefault().getEMH().setHover(true);
	}

	private static void stopHover() {
		UIPlugin.getDefault().getEMH().setHover(false);
	}

}