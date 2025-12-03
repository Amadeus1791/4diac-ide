/*******************************************************************************
 * Copyright (c) 2019 Johannes Kepler University Linz
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Alois Zoitl - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.fordiac.ide.application.editors;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.draw2d.AbstractBorder;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.emf.common.util.EList;
import org.eclipse.fordiac.ide.gef.dialogs.PortSelectionDialog;
import org.eclipse.fordiac.ide.gef.editors.NewInstanceCellEditor;
import org.eclipse.fordiac.ide.gef.editparts.TextDirectEditManager;
import org.eclipse.fordiac.ide.gef.tools.ChainModeManager;
import org.eclipse.fordiac.ide.gef.tools.PendingConnectionManager;
import org.eclipse.fordiac.ide.model.commands.create.AbstractConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.commands.create.DataConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.commands.create.EventConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.libraryElement.Event;
import org.eclipse.fordiac.ide.model.libraryElement.FB;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.requests.DirectEditRequest;
import org.eclipse.gef.tools.CellEditorLocator;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class NewInstanceDirectEditManager extends TextDirectEditManager {

	private static final Insets BORDER_INSETS = new Insets(0, 0, 0, 0);
	private static final AbstractBorder BORDER = new AbstractBorder() {

		@Override
		public Insets getInsets(final IFigure figure) {
			return BORDER_INSETS;
		}

		@Override
		public void paint(final IFigure figure, final Graphics graphics, final Insets insets) {
			// don't draw any border to make the direct editor smaller
		}
	};

	public static class NewInstanceCellEditorLocator implements CellEditorLocator {
		private Point refPoint = new Point(0, 0);

		@Override
		public void relocate(final CellEditor celleditor) {
			if (null != celleditor) {
				final Control control = celleditor.getControl();
				final Point pref = control.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				control.setBounds(refPoint.x - 1, refPoint.y - 1, pref.x * 2 + 1, pref.y + 1);
			}
		}

		public void setRefPoint(final Point refPoint) {
			this.refPoint = refPoint;
		}

		public Point getRefPoint() {
			return refPoint;
		}
	}

	private final TypeLibrary typeLib;
	private final boolean useChangeFBType;
	private String initialValue;

	public NewInstanceDirectEditManager(final GraphicalEditPart source, final TypeLibrary typeLib,
			final boolean useChangeFBType) {
		super(source, new NewInstanceCellEditorLocator());
		this.typeLib = typeLib;
		this.useChangeFBType = useChangeFBType;
	}

	@Override
	protected CellEditor createCellEditorOn(final Composite composite) {
		return new NewInstanceCellEditor(composite);
	}

	@Override
	public void show() {
		initialValue = null;

		// If in chain mode and triggered programmatically, ignore the first focus lost
		final ChainModeManager chainManager = ChainModeManager.getInstance();
		if (chainManager.isChainModeActive()) {
			System.out.println("[DirectEditManager] Chain mode active - will ignore first focus lost");
		}

		super.show();

		// Set the flag after show() to prevent immediate close
		if (chainManager.isChainModeActive()) {
			getCellEditor().setIgnoreNextFocusLost(true);
		}
	}

	public void show(final String initialValue) {
		this.initialValue = initialValue;
		super.show();
		if (initialValue != null) {
			final Text text = getCellEditor().getText();
			text.setSelection(text.getText().length());
			setDirty(true);
		}
	}

	@Override
	protected void initCellEditor() {
		getCellEditor().getMenuButton().addListener(SWT.Selection, event -> showFBInsertPopUpMenu());
		getCellEditor().setTypeLibrary(typeLib);
		super.initCellEditor();
		if (null != initialValue) {
			getCellEditor().setValue(initialValue);
		}
	}

	@Override
	public NewInstanceCellEditorLocator getLocator() {
		return (NewInstanceCellEditorLocator) super.getLocator();
	}

	@Override
	protected NewInstanceCellEditor getCellEditor() {
		return (NewInstanceCellEditor) super.getCellEditor();
	}

	@Override
	protected DirectEditRequest createDirectEditRequest() {
		final DirectEditRequest directEditRequest = super.createDirectEditRequest();
		directEditRequest.setLocation(new org.eclipse.draw2d.geometry.Point(getLocator().getRefPoint()));
		return directEditRequest;
	}

	public void updateRefPosition(final Point refPoint) {
		getLocator().setRefPoint(refPoint);
	}

	@Override
	protected IFigure getCellEditorFrame() {
		final IFigure cellEditorFrame = super.getCellEditorFrame();
		cellEditorFrame.setBorder(BORDER);
		return cellEditorFrame;
	}

	private void showFBInsertPopUpMenu() {
		final EditPartViewer viewer = getEditPart().getViewer();
		final MenuManager mgr = new MenuManager();
		((FBNetworkContextMenuProvider) viewer.getContextMenu()).buildFBInsertMenu(mgr, getLocator().getRefPoint(),
				useChangeFBType);
		final Menu menu = mgr.createContextMenu(viewer.getControl());
		menu.setVisible(true);
		// put the menu on top of the editor
		menu.setLocation(viewer.getControl().toDisplay(getLocator().getRefPoint()));
		// get rid of the editor
		getCellEditor().fireCancelEditor();
	}

	@Override
	protected void commit() {
		System.out.println("[DirectEditManager] commit() called");
		final ChainModeManager chainManager = ChainModeManager.getInstance();
		System.out.println("[DirectEditManager] Chain mode active? " + chainManager.isChainModeActive());

		if (chainManager.isChainModeActive()) {
			System.out.println("[DirectEditManager] Setting up pending connection");
			final IInterfaceElement sourcePin = chainManager.getChainSourcePin();
			final FBNetwork network = (FBNetwork) getEditPart().getModel();

			PendingConnectionManager.getInstance().setPending(sourcePin, network);
			System.out.println("[DirectEditManager] Pending connection set");

			setupPendingConnectionPolling();
		} else {
			System.out.println("[DirectEditManager] Chain mode NOT active - skipping auto-connection");
		}

		super.commit();
	}

	private void setupPendingConnectionPolling() {
		final PendingConnectionManager manager = PendingConnectionManager.getInstance();
		if (!manager.hasPending()) {
			return;
		}

		final int elementCountBefore = manager.getTargetNetwork().getNetworkElements().size();
		final GraphicalViewer viewer = (GraphicalViewer) getEditPart().getViewer();

		Display.getDefault().timerExec(200, new Runnable() {
			private int attempts = 0;
			private static final int MAX_ATTEMPTS = 50;

			@Override
			public void run() {
				if (!manager.hasPending()) {
					return;
				}
				attempts++;

				final int elementCountAfter = manager.getTargetNetwork().getNetworkElements().size();

				if (elementCountAfter > elementCountBefore) {
					// New FB created - create connection
					createPendingConnection(viewer); // ← Changed this line
				} else if (attempts < MAX_ATTEMPTS) {
					Display.getDefault().timerExec(100, this);
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

			// ✓✓✓ UPDATE CHAIN STATE HERE ✓✓✓
			System.out.println("[DirectEditManager] Connection created successfully!");

			// Update chain for next element
			final ChainModeManager chainManager = ChainModeManager.getInstance();
			chainManager.addToChain(newFB);
			System.out.println("[DirectEditManager] Added to chain: " + newFB.getName());

			// Find output pin on new FB for next connection
			final IInterfaceElement newOutputPin = findFirstEventOutput(newFB);
			if (newOutputPin != null) {
				chainManager.updateSourcePin(newOutputPin);
				System.out.println("[DirectEditManager] Next source pin: " + newOutputPin.getName());
			}

			// ⭐ NEW: Auto-reopen popup for next element in chain mode
			if (chainManager.isChainModeActive()) {
				System.out.println("[DirectEditManager] Chain mode still active - reopening popup");
				reopenPopupForNextElement();
			}
		}

		// Clear state after success or failure
		manager.clear();
	}

	private IInterfaceElement findFirstEventOutput(final FB fb) {
		final EList<Event> eventOutputs = fb.getInterface().getEventOutputs();
		if (!eventOutputs.isEmpty()) {
			return eventOutputs.get(0);
		}
		return null;
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

	/**
	 * Reopen the popup for the next element in chain mode. Called after
	 * successfully creating and connecting a new FB in chain mode.
	 */
	private void reopenPopupForNextElement() {
		System.out.println("[DirectEditManager] reopenPopupForNextElement called");

		final ChainModeManager chainManager = ChainModeManager.getInstance();
		if (!chainManager.isChainModeActive()) {
			System.out.println("[DirectEditManager] Chain mode not active, not reopening");
			return;
		}

		// Get the last FB added to the chain
		final FBNetworkElement lastFB = chainManager.getChainSourceFB();
		if (lastFB == null) {
			System.out.println("[DirectEditManager] No source FB, cannot reopen");
			return;
		}

		// IMPORTANT: We need to wait for the figure to be created and laid out
		// before we can get accurate bounds
		Display.getDefault().timerExec(50, () -> {
			// Get fresh EditPart for the last FB
			final GraphicalViewer viewer = (GraphicalViewer) getEditPart().getViewer();
			final EditPart lastFBEditPart = viewer.getEditPartRegistry().get(lastFB);

			if (lastFBEditPart instanceof final GraphicalEditPart graphicalEP) {
				// Get FRESH bounds from the figure (this reflects where it was actually placed)
				final org.eclipse.draw2d.geometry.Rectangle bounds = graphicalEP.getFigure().getBounds().getCopy();

				System.out.println("[DirectEditManager] Last FB bounds from figure: " + bounds);

				// Calculate next position based on chain direction
				final org.eclipse.draw2d.geometry.Point nextPosition;

				if (chainManager.getChainDirection() == ChainModeManager.ChainDirection.VERTICAL) {
					// Vertical: same X, add spacing to Y
					nextPosition = new org.eclipse.draw2d.geometry.Point(bounds.x, bounds.y + bounds.height + 100);
					System.out.println("[DirectEditManager] Calculating VERTICAL position");
				} else {
					// Horizontal: add spacing to X, same Y
					nextPosition = new org.eclipse.draw2d.geometry.Point(bounds.x + bounds.width + 150, bounds.y);
					System.out.println("[DirectEditManager] Calculating HORIZONTAL position");
				}

				// Convert to absolute coordinates
				graphicalEP.getFigure().translateToAbsolute(nextPosition);

				System.out.println("[DirectEditManager] Next position (" + chainManager.getChainDirection() + "): "
						+ nextPosition);

				// Convert to SWT Point
				final org.eclipse.swt.graphics.Point swtPoint = new org.eclipse.swt.graphics.Point(nextPosition.x,
						nextPosition.y);

				// Update the popup position (this also determines where FB will be placed)
				updateRefPosition(swtPoint);

				// Reopen the popup after a short delay to allow the UI to update
				Display.getDefault().timerExec(100, () -> {
					System.out.println("[DirectEditManager] Showing popup at: " + swtPoint);
					show(); // This will automatically set ignoreNextFocusLost because chain mode is active
				});
			} else {
				System.out.println("[DirectEditManager] Could not find EditPart for last FB");
			}
		});
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

}