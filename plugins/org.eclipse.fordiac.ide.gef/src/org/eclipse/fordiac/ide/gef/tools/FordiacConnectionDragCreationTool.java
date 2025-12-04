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
 *   [Your Name] - Added Ctrl+Drag requirement and visual feedback for drag-to-create
 *******************************************************************************/
package org.eclipse.fordiac.ide.gef.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.RectangleFigure;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.emf.common.util.EList;
import org.eclipse.fordiac.ide.gef.dialogs.PortSelectionDialog;
import org.eclipse.fordiac.ide.gef.figures.HideableConnection;
import org.eclipse.fordiac.ide.gef.router.MoveableRouter;
import org.eclipse.fordiac.ide.model.commands.create.AbstractConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.commands.create.DataConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.commands.create.EventConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.data.DataType;
import org.eclipse.fordiac.ide.model.libraryElement.Application;
import org.eclipse.fordiac.ide.model.libraryElement.AutomationSystem;
import org.eclipse.fordiac.ide.model.libraryElement.Event;
import org.eclipse.fordiac.ide.model.libraryElement.FB;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.libraryElement.InterfaceList;
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.fordiac.ide.model.ui.editors.AdvancedScrollingGraphicalViewer;
import org.eclipse.fordiac.ide.ui.UIPlugin;
import org.eclipse.fordiac.ide.ui.preferences.ConnectionPreferenceValues;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.editparts.ScalableFreeformRootEditPart;
import org.eclipse.gef.requests.CreateConnectionRequest;
import org.eclipse.gef.requests.SelectionRequest;
import org.eclipse.gef.tools.ConnectionDragCreationTool;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

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
	private org.eclipse.gef.commands.CommandStackEventListener pendingConnectionListener;

	/** Track whether we're currently over empty canvas for cursor feedback */
	private boolean isOverEmptyCanvas = false;

	// ============================================================================
	// NEW: Ctrl+Drag State Tracking
	// ============================================================================

	/** Track whether Ctrl key is currently pressed during drag */
	private boolean ctrlPressed = false;

	/** Starting location of drag operation (for distance calculation) */
	private Point dragStartLocation = null;

	/** Whether the drag has exceeded the minimum distance threshold */
	private boolean hasExceededMinDistance = false;

	/** Minimum drag distance (in pixels) before drag-to-create can activate */
	private static final int MIN_DRAG_DISTANCE = 20;

	/**
	 * Cursor to show when drag-to-create mode is active (Ctrl+Drag over empty
	 * canvas)
	 */
	private static Cursor PLUS_CURSOR = null;

	/** Ghost block figure shown during drag-to-create */
	private IFigure ghostBlockFigure = null;
	/** Font for ghost block label (reused to avoid memory leak) */
	private Font ghostLabelFont = null;

	static {
		try {
			// Use a distinctive cursor for drag-to-create mode
			// Option A: Use system hand cursor (most distinctive)
			PLUS_CURSOR = Display.getDefault().getSystemCursor(SWT.CURSOR_HAND);

			// Option B: Use cross cursor (similar to state machine Ctrl+Drag)
			// PLUS_CURSOR = Display.getDefault().getSystemCursor(SWT.CURSOR_CROSS);

			// Option C: Create custom cursor from image (requires FordiacImage.ICON_ADD or
			// similar)
			// PLUS_CURSOR = new Cursor(Display.getDefault(),
			// FordiacImage.ICON_ADD.getImageDescriptor().getImageData(), 8, 8);
		} catch (final Exception e) {
			System.err.println("Failed to create PLUS_CURSOR: " + e.getMessage());
			// Fallback to arrow cursor
			PLUS_CURSOR = Display.getDefault().getSystemCursor(SWT.CURSOR_ARROW);
		}
	}

	// ============================================================================
	// Constructor
	// ============================================================================

	public FordiacConnectionDragCreationTool() {
		setDefaultCursor(Display.getDefault().getSystemCursor(SWT.CURSOR_CROSS));
		setDisabledCursor(Display.getDefault().getSystemCursor(SWT.CURSOR_NO));
	}

	// ============================================================================
	// Tool Lifecycle Methods
	// ============================================================================

	@Override
	public void deactivate() {
		stopHover();

		// Reset cursor feedback state
		isOverEmptyCanvas = false;

		// Reset drag-to-create state
		resetDragToCreateState();

		// Don't clear PendingConnectionManager here - it needs to survive tool
		// deactivation

		super.deactivate();
	}

	// ============================================================================
	// Mouse Event Handlers
	// ============================================================================

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
	protected boolean handleButtonDown(final int button) {
		// Capture the source of the drag operation
		if (getTargetEditPart() != null) {
			sourceEditPart = getTargetEditPart();
			sourceModel = sourceEditPart.getModel();
		}

		// Initialize drag tracking for Ctrl+Drag detection
		dragStartLocation = getLocation().getCopy();
		hasExceededMinDistance = false;

		return super.handleButtonDown(button);
	}

	@Override
	protected boolean handleButtonUp(final int button) {
		// Hide ghost immediately when mouse released
		hideGhostBlock();
		// MODIFIED: Only trigger drag-to-create if ALL conditions met:
		// 1. Valid source model (pin)
		// 2. Canvas drop location detected
		// 3. Ctrl was pressed during drag
		if (sourceModel != null && canvasDropLocation != null && ctrlPressed) {
			// Get viewer and cast to GraphicalViewer
			final EditPartViewer viewer = getCurrentViewer();
			if (!(viewer instanceof GraphicalViewer)) {
				// Clean up and fall back to normal behavior
				resetDragToCreateState();
				return false;
			}

			// Handle the canvas drop with drag-to-create
			handleCanvasDrop((GraphicalViewer) viewer, sourceEditPart, sourceModel, canvasDropLocation);

			// Clean up
			resetDragToCreateState();

			return true;
		}

		// Normal connection handling or cancelled drag-to-create
		resetDragToCreateState();

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

	@Override
	protected boolean handleDrag() {
		// Get current state
		final Point currentLocation = getLocation();

		// Check Ctrl key state
		ctrlPressed = getCurrentInput().isModKeyDown(SWT.MOD1);

		// Calculate drag distance from start point
		if (dragStartLocation != null && !hasExceededMinDistance) {
			final double distance = dragStartLocation.getDistance(currentLocation);
			hasExceededMinDistance = distance >= MIN_DRAG_DISTANCE;
		}

		// Track the current drag location to detect canvas drops
		if (sourceModel != null) {
			final EditPart targetEP = getTargetEditPart();

			if (ctrlPressed && hasExceededMinDistance
					&& (targetEP == null || targetEP.getModel() instanceof FBNetwork)) {
				canvasDropLocation = currentLocation.getCopy();
			} else {
				canvasDropLocation = null;
			}
		}

		// Show/hide ghost block based on drag-to-create state (ADD THIS)
		if (ctrlPressed && hasExceededMinDistance && canvasDropLocation != null) {
			System.out.println("Should show ghost"); // DEBUG

			showGhostBlock(currentLocation);
		} else {
			hideGhostBlock();
		}

		// Call parent implementation
		final boolean result = super.handleDrag();

		// Force cursor update when state changes
		setCursor(calculateCursor());

		return result;
	}

	@Override
	protected boolean handleMove() {
		// Let parent handle normal connection creation behavior
		final boolean result = super.handleMove();

		// If we're dragging from a source pin, track state for cursor feedback
		if (sourceEditPart != null && sourceModel instanceof IInterfaceElement) {
			final Point location = getLocation();
			final EditPart targetEditPart = getCurrentViewer().findObjectAt(location);

			// Check Ctrl state even during move (for future hover previews)
			ctrlPressed = getCurrentInput().isModKeyDown(SWT.MOD1);

			// Update empty canvas state
			final boolean nowOverEmptyCanvas = isOverEmptyCanvas(targetEditPart);

			if (nowOverEmptyCanvas != isOverEmptyCanvas) {
				isOverEmptyCanvas = nowOverEmptyCanvas;
				// Update cursor
				setCursor(calculateCursor());
			}
		}

		return result;
	}

	// ============================================================================
	// NEW: Helper Method to Reset Drag-to-Create State
	// ============================================================================

	/**
	 * Reset all drag-to-create state variables. Called on deactivation, button up,
	 * or cancellation.
	 */
	private void resetDragToCreateState() {
		sourceEditPart = null;
		sourceModel = null;
		canvasDropLocation = null;
		ctrlPressed = false;
		dragStartLocation = null;
		hasExceededMinDistance = false;
		hideGhostBlock(); // Clean up ghost
	}

	/**
	 * Show ghost block at cursor position to indicate new element will be created.
	 */

	/**
	 * Show ghost block at cursor position to indicate new element will be created.
	 */

	private void showGhostBlock(final Point location) {
		if (ghostBlockFigure == null) {
			// Create font once
			if (ghostLabelFont == null) {
				ghostLabelFont = new Font(Display.getDefault(), "Arial", 24, SWT.BOLD);
			}

			// Create rectangle figure
			final RectangleFigure rect = new RectangleFigure();
			rect.setFill(true);
			rect.setBackgroundColor(ColorConstants.lightGray);
			rect.setForegroundColor(ColorConstants.darkGray);
			rect.setAlpha(100);
			rect.setLineWidth(2);
			rect.setLineStyle(SWT.LINE_DASH);

			// Add "?" label
			final Label label = new Label("?");
			label.setFont(ghostLabelFont);
			label.setForegroundColor(ColorConstants.black);
			rect.add(label);

			// Position label in center using BOUNDS
			final Dimension labelSize = label.getPreferredSize();
			label.setBounds(new Rectangle(70 - labelSize.width / 2, 45 - labelSize.height / 2, labelSize.width,
					labelSize.height));

			ghostBlockFigure = rect;

			try {
				final EditPartViewer viewer = getCurrentViewer();
				if (viewer instanceof final GraphicalViewer gv) {
					final ScalableFreeformRootEditPart root = (ScalableFreeformRootEditPart) gv.getRootEditPart();
					final IFigure feedbackLayer = root.getLayer(LayerConstants.FEEDBACK_LAYER);

					final Point feedbackLocation = location.getCopy();
					feedbackLayer.translateToRelative(feedbackLocation);
					feedbackLayer.translateFromParent(feedbackLocation);

					rect.setBounds(new Rectangle(feedbackLocation.x - 70, feedbackLocation.y - 45, 140, 90));

					feedbackLayer.add(ghostBlockFigure);
				}
			} catch (final Exception e) {
				System.out.println("Failed to add ghost: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			// Update position
			try {
				final EditPartViewer viewer = getCurrentViewer();
				if (viewer instanceof final GraphicalViewer gv) {
					final ScalableFreeformRootEditPart root = (ScalableFreeformRootEditPart) gv.getRootEditPart();
					final IFigure feedbackLayer = root.getLayer(LayerConstants.FEEDBACK_LAYER);

					final Point feedbackLocation = location.getCopy();
					feedbackLayer.translateToRelative(feedbackLocation);
					feedbackLayer.translateFromParent(feedbackLocation);

					ghostBlockFigure.setLocation(new Point(feedbackLocation.x - 70, feedbackLocation.y - 45));
				}
			} catch (final Exception e) {
				// Ignore
			}
		}
	}

	private void hideGhostBlock() {
		if (ghostBlockFigure != null) {
			System.out.println("Removing ghost block");

			try {
				final EditPartViewer viewer = getCurrentViewer();
				if (viewer instanceof final GraphicalViewer gv) {
					final ScalableFreeformRootEditPart root = (ScalableFreeformRootEditPart) gv.getRootEditPart();
					final IFigure feedbackLayer = root.getLayer(LayerConstants.FEEDBACK_LAYER);
					feedbackLayer.remove(ghostBlockFigure);
				}
			} catch (final Exception e) {
				System.out.println("Failed to remove ghost: " + e.getMessage());
			}

			ghostBlockFigure = null;
		}

		// Dispose font when cleaning up
		if (ghostLabelFont != null && !ghostLabelFont.isDisposed()) {
			ghostLabelFont.dispose();
			ghostLabelFont = null;
		}
	}
	// ============================================================================
	// MODIFIED: Cursor Calculation for Visual Feedback
	// ============================================================================

	/**
	 * Override cursor calculation to show different cursor based on drag-to-create
	 * state. This is called by GEF's drag tracker and properly updates the cursor
	 * during drag operations.
	 *
	 * MODIFIED: Now checks for Ctrl+Drag conditions before showing special cursor.
	 *
	 * Cursor Mapping: - PLUS_CURSOR (HAND): Ctrl+Drag over empty canvas (create new
	 * element mode) - CROSS: Normal drag over compatible port (connect mode) - NO:
	 * Over invalid target (helpful feedback)
	 */
	@Override
	protected Cursor calculateCursor() {
		// Check if we're in drag-to-create mode FIRST (before parent validation)
		if (ctrlPressed && hasExceededMinDistance && canvasDropLocation != null) {
			return PLUS_CURSOR;
		}

		// Otherwise use parent's cursor logic
		return super.calculateCursor();
	}

	/**
	 * Check if the given EditPart represents empty canvas (i.e., the network
	 * itself, not a child element like a function block).
	 */
	private boolean isOverEmptyCanvas(final EditPart targetEditPart) {
		if (targetEditPart == null) {
			return false;
		}

		// Empty canvas = we hit the FBNetworkEditPart itself
		final Object model = targetEditPart.getModel();
		return model instanceof FBNetwork;
	}

	// ============================================================================
	// Connection Request Helpers
	// ============================================================================

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

	// ============================================================================
	// Canvas Drop Detection (Unused but kept for reference)
	// ============================================================================

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

	// ============================================================================
	// Drag-to-Create Workflow
	// ============================================================================

	/**
	 * Handle dropping a connection on empty canvas to trigger drag-to-create.
	 *
	 * This initiates the workflow: 1. Store the connection intent 2. Show type
	 * selection UI 3. Poll for new FB creation 4. Auto-create connection to new FB
	 *
	 * @param viewer         the graphical viewer
	 * @param sourceEditPart the edit part of the source element
	 * @param sourceModel    the source model (should be IInterfaceElement)
	 * @param dropLocation   the location where the drop occurred
	 */
	private void handleCanvasDrop(final GraphicalViewer viewer, final EditPart sourceEditPart, final Object sourceModel,
			final Point dropLocation) {

		// Validate source is an interface element (pin)
		if (!(sourceModel instanceof IInterfaceElement)) {
			return;
		}

		// Find target edit part at drop location
		final EditPart targetEditPart = viewer.findObjectAt(dropLocation);
		if (targetEditPart == null) {
			return;
		}

		// Navigate up to find FBNetworkEditPart
		EditPart graphicalEditPart = targetEditPart;
		while (graphicalEditPart != null && !(graphicalEditPart.getModel() instanceof FBNetwork)) {
			graphicalEditPart = graphicalEditPart.getParent();
		}

		if (graphicalEditPart == null || !(graphicalEditPart.getModel() instanceof FBNetwork)) {
			return;
		}

		final FBNetwork network = (FBNetwork) graphicalEditPart.getModel();

		// Store state in manager
		PendingConnectionManager.getInstance().setPending((IInterfaceElement) sourceModel, network);

		final Point finalDropLocation = dropLocation.getCopy();

		// Show dialog IMMEDIATELY without any delay
		if (graphicalEditPart instanceof GraphicalEditPart) {
			showTypeSelectionUI((GraphicalEditPart) graphicalEditPart, finalDropLocation);
		}

		// Set up listener AFTER dialog is triggered (no initial delay)
		setupPendingConnectionListener(viewer);
	}

	/**
	 * Set up polling to detect when a new FB is created, then auto-create the
	 * connection.
	 *
	 * Polls every 100ms for up to 5 seconds to detect element count increase in the
	 * network.
	 *
	 * @param viewer the graphical viewer
	 */
	private void setupPendingConnectionListener(final GraphicalViewer viewer) {
		final PendingConnectionManager manager = PendingConnectionManager.getInstance();

		if (!manager.hasPending()) {
			return;
		}

		final int elementCountBefore = manager.getTargetNetwork().getNetworkElements().size();

		// Poll immediately, no delays
		Display.getDefault().asyncExec(new Runnable() {
			private int attempts = 0;
			private static final int MAX_ATTEMPTS = 200;

			@Override
			public void run() {
				if (!manager.hasPending()) {
					return;
				}

				attempts++;

				final int elementCountAfter = manager.getTargetNetwork().getNetworkElements().size();

				if (elementCountAfter > elementCountBefore) {
					createPendingConnection(viewer);
				} else if (attempts < MAX_ATTEMPTS) {
					Display.getDefault().timerExec(20, this); // Fast polling
				} else {
					manager.clear();
				}
			}
		});
	}

	/**
	 * Create the auto-connection from the source pin to a compatible input on the
	 * newly created FB. Handles multiple compatible ports by showing a selection
	 * dialog.
	 *
	 * @param viewer the graphical viewer
	 */
	private void createPendingConnection(final GraphicalViewer viewer) {
		final PendingConnectionManager manager = PendingConnectionManager.getInstance();

		if (!manager.hasPending()) {
			return;
		}

		final IInterfaceElement sourcePin = manager.getSourcePin();
		final FBNetwork network = manager.getTargetNetwork();

		// Find the newly created FB (last element in network)
		final EList<FBNetworkElement> elements = network.getNetworkElements();
		if (elements.isEmpty()) {
			manager.clear();
			return;
		}

		final FBNetworkElement newElement = elements.get(elements.size() - 1);

		// Check if it's an FB (has interface)
		if (!(newElement instanceof final FB newFB)) {
			manager.clear();
			return;
		}

		// Validate source pin direction
		if (sourcePin.isIsInput()) {
			// Source is input pin - we expect output pins for drag-to-create
			manager.clear();
			return;
		}

		// Find all compatible input ports on the new FB
		final List<IInterfaceElement> compatiblePorts = findCompatiblePorts(newFB, sourcePin);

		// Determine target pin based on number of compatible ports
		final IInterfaceElement targetPin;

		if (compatiblePorts.isEmpty()) {
			// E3: No compatible ports found
			// TODO: Show user-friendly error dialog
			manager.clear();
			return;

		}
		if (compatiblePorts.size() == 1) {
			// Single compatible port - auto-connect without asking
			targetPin = compatiblePorts.get(0);

		} else {
			// E4: Multiple compatible ports - let user choose
			targetPin = showPortSelectionDialog(viewer, compatiblePorts, newFB.getName());

			if (targetPin == null) {
				// User cancelled the dialog
				manager.clear();
				return;
			}
		}

		// Create the connection command
		final AbstractConnectionCreateCommand connectionCmd;
		if (sourcePin instanceof Event) {
			connectionCmd = new EventConnectionCreateCommand(network);
		} else {
			connectionCmd = new DataConnectionCreateCommand(network);
		}

		connectionCmd.setSource(sourcePin);
		connectionCmd.setDestination(targetPin);

		// Execute the command
		final CommandStack commandStack = viewer.getEditDomain().getCommandStack();
		if (commandStack != null) {
			commandStack.execute(connectionCmd);
		}

		// Clear state after success or failure
		manager.clear();
	}

	// ============================================================================
	// Port Compatibility Checking
	// ============================================================================

	private IInterfaceElement findCompatibleInputPin(final FBNetworkElement fb, final IInterfaceElement outputPin) {
		if (fb == null || outputPin == null) {
			return null;
		}

		// FBNetworkElement doesn't have getInterface() - need to check type
		if (!(fb instanceof final FB functionBlock)) {
			return null; // Only FBs have interfaces we can query
		}

		final InterfaceList interfaceList = functionBlock.getInterface();
		if (interfaceList == null) {
			return null;
		}

		// Check if output is event or data
		if (outputPin instanceof Event) {
			// Find first event input
			final EList<Event> eventInputs = interfaceList.getEventInputs();
			return eventInputs.isEmpty() ? null : eventInputs.get(0);
		}
		if (outputPin instanceof final VarDeclaration outputVar) {
			// Find compatible data input
			for (final VarDeclaration inputVar : interfaceList.getInputVars()) {
				if (isDataTypeCompatible(outputVar, inputVar)) {
					return inputVar;
				}
			}
		}

		return null;
	}

	private boolean isDataTypeCompatible(final VarDeclaration outputVar, final VarDeclaration inputVar) {
		if (outputVar == null || inputVar == null) {
			return false;
		}

		final DataType outputType = outputVar.getType();
		final DataType inputType = inputVar.getType();

		if (outputType == null || inputType == null) {
			return false;
		}

		final String outputTypeName = outputType.getName();
		final String inputTypeName = inputType.getName();

		if (outputTypeName == null || inputTypeName == null) {
			return false;
		}

		// Check if types match or input accepts ANY
		return outputTypeName.equalsIgnoreCase(inputTypeName) || "ANY".equalsIgnoreCase(inputTypeName);
	}

	private Command createConnectionCommand(final IInterfaceElement source, final IInterfaceElement target,
			final FBNetwork network) {
		// Use FORDIAC's static factory method
		final AbstractConnectionCreateCommand cmd = AbstractConnectionCreateCommand.createCommand(network, source,
				target);

		if (cmd != null) {
			cmd.setSource(source);
			cmd.setDestination(target);
			cmd.setParent(network);
		}

		return cmd;
	}

	/**
	 * Check if source and target pins are compatible for connection.
	 *
	 * @param source the source interface element
	 * @param target the target interface element
	 * @return true if compatible
	 */
	private boolean isCompatible(final IInterfaceElement source, final IInterfaceElement target) {
		// Basic type compatibility check
		if (source instanceof Event && target instanceof Event) {
			return true; // All events are compatible
		}

		if (source instanceof final VarDeclaration sourceVar && target instanceof final VarDeclaration targetVar) {
			// For data connections, check type compatibility
			// Simple check - proper implementation would need full IEC 61499 type checking
			if (sourceVar.getType() != null && targetVar.getType() != null) {
				return sourceVar.getType().getName().equals(targetVar.getType().getName());
			}

			// If types are not yet defined, allow connection (will be validated later)
			return true;
		}

		return false;
	}

	/**
	 * Find all compatible input ports on the target FB for the given source pin.
	 *
	 * @param targetFB  the target function block
	 * @param sourcePin the source interface element (output pin)
	 * @return list of compatible input ports
	 */
	private List<IInterfaceElement> findCompatiblePorts(final FB targetFB, final IInterfaceElement sourcePin) {
		final List<IInterfaceElement> compatible = new ArrayList<>();

		if (sourcePin instanceof Event) {
			// Collect all compatible event inputs
			for (final Event eventInput : targetFB.getInterface().getEventInputs()) {
				if (isCompatible(sourcePin, eventInput)) {
					compatible.add(eventInput);
				}
			}
		} else if (sourcePin instanceof VarDeclaration) {
			// Collect all compatible data inputs
			for (final VarDeclaration dataInput : targetFB.getInterface().getInputVars()) {
				if (isCompatible(sourcePin, dataInput)) {
					compatible.add(dataInput);
				}
			}
		}

		return compatible;
	}

	// ============================================================================
	// UI Helpers
	// ============================================================================

	private TypeLibrary getTypeLibraryFromEditPart(final GraphicalEditPart editPart) {
		final Object model = editPart.getModel();
		if (model instanceof final FBNetwork network) {
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

	private void showTypeSelectionUI(final GraphicalEditPart editPart, final Point dropLocation) {
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
				request.setLocation(dropLocation.getCopy()); // Use parameter instead of field

				method.invoke(policy, request);
				System.out.println("Triggered FORDIAC type selection");

			} catch (final Exception e) {
				System.out.println("ERROR: Could not trigger direct edit: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			System.out.println("ERROR: No DIRECT_EDIT_ROLE policy found");
		}
	}

	/**
	 * Show a dialog to let the user select which port to connect to when multiple
	 * compatible ports are available.
	 *
	 * @param viewer          the graphical viewer
	 * @param compatiblePorts list of compatible ports
	 * @param targetFBName    name of the target FB
	 * @return the selected port, or null if cancelled
	 */
	private IInterfaceElement showPortSelectionDialog(final GraphicalViewer viewer,
			final List<IInterfaceElement> compatiblePorts, final String targetFBName) {

		final Shell shell = viewer.getControl().getShell();
		final PortSelectionDialog dialog = new PortSelectionDialog(shell, compatiblePorts, targetFBName);

		if (dialog.open() == Window.OK) {
			return dialog.getSelectedPort();
		}

		return null; // User cancelled
	}

	private static void startHover() {
		UIPlugin.getDefault().getEMH().setHover(true);
	}

	private static void stopHover() {
		UIPlugin.getDefault().getEMH().setHover(false);
	}

}