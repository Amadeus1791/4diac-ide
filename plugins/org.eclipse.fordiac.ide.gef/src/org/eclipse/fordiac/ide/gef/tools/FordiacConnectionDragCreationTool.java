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
import org.eclipse.emf.common.util.EList;
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
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
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
	private org.eclipse.gef.commands.CommandStackEventListener pendingConnectionListener;

	public FordiacConnectionDragCreationTool() {
		setDefaultCursor(Display.getDefault().getSystemCursor(SWT.CURSOR_CROSS));
		setDisabledCursor(Display.getDefault().getSystemCursor(SWT.CURSOR_NO));
	}

	@Override
	public void deactivate() {
		System.out.println("=== DEACTIVATE CALLED ===");

		stopHover();

		// Clean up drag-to-create state
		sourceEditPart = null;
		sourceModel = null;
		canvasDropLocation = null;

		// Don't clear PendingConnectionManager here - polling handles cleanup
		// The manager state needs to survive tool deactivation

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
		System.out.println("=== handleButtonUp START ===");
		System.out.println("sourceModel: " + sourceModel);
		System.out.println("canvasDropLocation: " + canvasDropLocation);

		// Check if this is a canvas drop (drag from pin to empty canvas)
		if (sourceModel != null && canvasDropLocation != null) {
			System.out.println("Canvas drop detected!");

			// Get viewer and cast to GraphicalViewer
			final EditPartViewer viewer = getCurrentViewer();
			if (!(viewer instanceof GraphicalViewer)) {
				System.out.println("ERROR: Viewer is not GraphicalViewer");
				// Clean up before returning
				sourceEditPart = null;
				sourceModel = null;
				canvasDropLocation = null;
				return false;
			}

			// Handle the canvas drop with drag-to-create
			handleCanvasDrop((GraphicalViewer) viewer, sourceEditPart, sourceModel, canvasDropLocation);

			// Clean up
			sourceEditPart = null;
			sourceModel = null;
			canvasDropLocation = null;

			return true;
		}

		System.out.println("Normal connection handling");

		// Clean up even if not canvas drop
		sourceEditPart = null;
		sourceModel = null;
		canvasDropLocation = null;

		return super.handleButtonUp(button);
	}

	@Override
	protected boolean handleButtonDown(final int button) {
		// Capture the source of the drag
		if (getTargetEditPart() != null) {
			sourceEditPart = getTargetEditPart();
			sourceModel = sourceEditPart.getModel();
			System.out.println("=== handleButtonDown ===");
			System.out.println("Captured source: " + sourceModel);
		}

		return super.handleButtonDown(button);
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
		// Track the current drag location
		final Point currentLocation = getLocation();

		if (sourceModel != null) {
			// Check if we're over empty canvas (no target edit part with a valid model)
			final EditPart targetEP = getTargetEditPart();

			if (targetEP == null || targetEP.getModel() instanceof FBNetwork) {
				// Dragging over empty canvas or network background
				canvasDropLocation = currentLocation.getCopy();
				System.out.println("Dragging over canvas at: " + canvasDropLocation);
			} else {
				// Dragging over an element - normal connection
				canvasDropLocation = null;
			}
		}

		return super.handleDrag();
	}

	@Override
	protected boolean handleMove() {
		// Also track during move (similar to drag)
		final Point currentLocation = getLocation();

		if (sourceModel != null) {
			final EditPart targetEP = getTargetEditPart();

			if (targetEP == null || targetEP.getModel() instanceof FBNetwork) {
				canvasDropLocation = currentLocation.getCopy();
			} else {
				canvasDropLocation = null;
			}
		}

		return super.handleMove();
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
	private void handleCanvasDrop(final GraphicalViewer viewer, final EditPart sourceEditPart, final Object sourceModel,
			final Point dropLocation) {
		System.out.println("=== handleCanvasDrop START ===");
		System.out.println("Canvas drop detected - source: " + sourceModel);

// Validate source is an interface element (pin)
		if (!(sourceModel instanceof IInterfaceElement)) {
			System.out.println("ERROR: Source is not an IInterfaceElement");
			return;
		}

// Find target edit part at drop location
		final EditPart targetEditPart = viewer.findObjectAt(dropLocation);
		if (targetEditPart == null) {
			System.out.println("ERROR: No edit part at drop location");
			return;
		}

// Navigate up to find FBNetworkEditPart
		EditPart graphicalEditPart = targetEditPart;
		while (graphicalEditPart != null && !(graphicalEditPart.getModel() instanceof FBNetwork)) {
			graphicalEditPart = graphicalEditPart.getParent();
		}

		if (graphicalEditPart == null || !(graphicalEditPart.getModel() instanceof FBNetwork)) {
			System.out.println("ERROR: Root model is not FBNetwork");
			return;
		}

		final FBNetwork network = (FBNetwork) graphicalEditPart.getModel();

// Store state in manager (survives tool deactivation)
		PendingConnectionManager.getInstance().setPending((IInterfaceElement) sourceModel, network);

		System.out.println("State stored in PendingConnectionManager");

// Start polling for new FB
		setupPendingConnectionListener(viewer);

// Trigger type selection (which will create the FB)

		// NEW:
		if (graphicalEditPart instanceof GraphicalEditPart) {
			showTypeSelectionUI((GraphicalEditPart) graphicalEditPart);
		} else {
			System.out.println("ERROR: Edit part is not GraphicalEditPart");
		}

		System.out.println("=== handleCanvasDrop END ===");
	}

	private void setupPendingConnectionListener(final GraphicalViewer viewer) {
		System.out.println("=== setupPendingConnectionListener START ===");

		final PendingConnectionManager manager = PendingConnectionManager.getInstance();

		if (!manager.hasPending()) {
			System.out.println("ERROR: No pending connection to monitor");
			return;
		}

		// Store the current number of elements
		final int elementCountBefore = manager.getTargetNetwork().getNetworkElements().size();
		System.out.println("elementCountBefore: " + elementCountBefore);

		// Wait 200ms before starting to poll (let dialog appear first)
		Display.getDefault().timerExec(200, new Runnable() {
			private int attempts = 0;
			private static final int MAX_ATTEMPTS = 50; // 50 * 100ms = 5 seconds max

			@Override
			public void run() {
				System.out.println("--- Polling attempt " + (attempts + 1) + " ---");

				// Check if state is still valid
				if (!manager.hasPending()) {
					System.out.println("Polling stopped - state cleared (cancelled or completed)");
					return;
				}

				attempts++;

				final int elementCountAfter = manager.getTargetNetwork().getNetworkElements().size();

				if (elementCountAfter > elementCountBefore) {
					// New FB was created!
					System.out.println("Detected new FB after " + attempts + " attempts (count: " + elementCountBefore
							+ " -> " + elementCountAfter + ")");
					createPendingConnection(viewer);
				} else if (attempts < MAX_ATTEMPTS) {
					// Not created yet, check again
					Display.getDefault().timerExec(100, this);
				} else {
					// Timeout - user probably cancelled
					System.out.println("Timeout waiting for FB creation - cleaning up");
					manager.clear();
				}
			}
		});
	}

	private void createPendingConnection(final GraphicalViewer viewer) {
		System.out.println("=== createPendingConnection START ===");

		final PendingConnectionManager manager = PendingConnectionManager.getInstance();

		if (!manager.hasPending()) {
			System.out.println("ERROR: No pending connection state");
			return;
		}

		final IInterfaceElement sourcePin = manager.getSourcePin();
		final FBNetwork network = manager.getTargetNetwork();

		System.out.println("Source pin: " + sourcePin.getName());
		System.out.println("Network elements: " + network.getNetworkElements().size());

		// Find the newly created FB (last element in network)
		final EList<FBNetworkElement> elements = network.getNetworkElements();
		if (elements.isEmpty()) {
			System.out.println("ERROR: No elements in network");
			manager.clear();
			return;
		}

		final FBNetworkElement newElement = elements.get(elements.size() - 1);
		System.out.println("New element found: " + newElement.getName() + " (type: " + newElement.getTypeName() + ")");

		// Check if it's an FB (has interface) - skip if it's a subapp or other element
		if (!(newElement instanceof final FB newFB)) {
			System.out.println("ERROR: New element is not an FB, it's: " + newElement.getClass().getSimpleName());
			manager.clear();
			return;
		}

		// Find compatible input pin on new FB
		IInterfaceElement targetPin = null;

		if (sourcePin.isIsInput()) {
			System.out.println("ERROR: Source is input pin, expected output pin");
			manager.clear();
			return;
		}

		// Source is output, find compatible input on new FB
		if (sourcePin instanceof Event) {
			// Look for event input
			for (final Event eventInput : newFB.getInterface().getEventInputs()) {
				if (isCompatible(sourcePin, eventInput)) {
					targetPin = eventInput;
					System.out.println("Found compatible event input: " + eventInput.getName());
					break;
				}
			}
		} else if (sourcePin instanceof VarDeclaration) {
			// Look for data input
			for (final VarDeclaration dataInput : newFB.getInterface().getInputVars()) {
				if (isCompatible(sourcePin, dataInput)) {
					targetPin = dataInput;
					System.out.println("Found compatible data input: " + dataInput.getName());
					break;
				}
			}
		}

		if (targetPin == null) {
			System.out.println("ERROR: No compatible input pin found on new FB");
			manager.clear();
			return;
		}

		// Create the connection command
		System.out.println("Creating connection: " + sourcePin.getName() + " -> " + targetPin.getName());

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
			System.out
					.println("SUCCESS: Auto-connection created: " + sourcePin.getName() + " -> " + targetPin.getName());
		} else {
			System.out.println("ERROR: CommandStack is null");
		}

		// Clear state after success or failure
		manager.clear();

		System.out.println("=== createPendingConnection END ===");
	}

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

	private void showTypeSelectionUI(final GraphicalEditPart editPart) {
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

			} catch (final Exception e) {
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

	/**
	 * Check if source and target pins are compatible for connection
	 */
	private boolean isCompatible(final IInterfaceElement source, final IInterfaceElement target) {
		// Basic type compatibility check
		if (source instanceof Event && target instanceof Event) {
			return true; // All events are compatible
		}

		if (source instanceof final VarDeclaration sourceVar && target instanceof final VarDeclaration targetVar) {
			// For data connections, check type compatibility
			// Simple check - in real implementation, would need proper type checking
			if (sourceVar.getType() != null && targetVar.getType() != null) {
				return sourceVar.getType().getName().equals(targetVar.getType().getName());
			}

			// If types are not yet defined, allow connection (will be validated later)
			return true;
		}

		return false;
	}

}