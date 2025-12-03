/*******************************************************************************
 * Copyright (c) 2008, 2025 Profactor GmbH, TU Wien ACIN, fortiss GmbH,
 *                          Johannes Kepler University Linz
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Gerhard Ebenhofer, Alois Zoitl
 *     - initial API and implementation and/or initial documentation
 *   Alois Zoitl - separated FBNetworkElement from instance name for better
 *                 direct editing of instance names
 *******************************************************************************/
package org.eclipse.fordiac.ide.application.editparts;

import org.eclipse.draw2d.IFigure;
import org.eclipse.fordiac.ide.application.Messages;
import org.eclipse.fordiac.ide.application.figures.FBNetworkElementFigure;
import org.eclipse.fordiac.ide.model.libraryElement.CFBInstance;
import org.eclipse.fordiac.ide.model.libraryElement.FB;
import org.eclipse.fordiac.ide.model.ui.actions.OpenListenerManager;
import org.eclipse.fordiac.ide.model.ui.editors.AdvancedScrollingGraphicalViewer;
import org.eclipse.gef.Request;
import org.eclipse.gef.RequestConstants;

/**
 * This class implements an EditPart for a FunctionBlock.
 */
public class FBEditPart extends AbstractBlockFBNElementEditPart {

	/**
	 * Creates the figure (for the specified model) to be used as this parts
	 * visuals.
	 *
	 * @return IFigure The figure for the model
	 */
	@Override
	protected IFigure createFigureForModel() {
		// extend this if FunctionBlock gets extended!
		FBNetworkElementFigure f = null;
		if (getModel() == null) {
			throw new IllegalArgumentException(Messages.FBEditPart_ERROR_UnsupportedFBType);
		}
		f = new FBNetworkElementFigure(getModel(),
				((AdvancedScrollingGraphicalViewer) getViewer()).getPreferencesCache().getMaxTypeLabelSize());
		return f;
	}

	@Override
	public FB getModel() {
		return (FB) super.getModel();
	}

	@Override
	public void performRequest(final Request request) {
		// Check for Shift+Click to activate chain mode
		if ((request instanceof final org.eclipse.gef.requests.SelectionRequest selRequest)
				&& selRequest.isShiftKeyPressed()) {
			System.out.println("[FBEditPart] Shift+Click detected on: " + getModel().getName());
			handleShiftClickChainMode();
			return; // Don't call super - we handled it
		}

		if (request.getType().equals(RequestConstants.REQ_OPEN) && getModel() != null
				&& getModel() instanceof CFBInstance) {
			OpenListenerManager.openEditor(getModel());
		} else {
			super.performRequest(request);
		}
	}

	private void handleShiftClickChainMode() {
		System.out.println("[FBEditPart] handleShiftClickChainMode called");

		final FB fb = getModel();
		System.out.println("[FBEditPart] FB: " + fb.getName());

		// Find first event output
		final org.eclipse.fordiac.ide.model.libraryElement.InterfaceList interfaceList = fb.getInterface();
		if (interfaceList == null) {
			System.out.println("[FBEditPart] ERROR: No interface list");
			return;
		}

		final org.eclipse.emf.common.util.EList<org.eclipse.fordiac.ide.model.libraryElement.Event> eventOutputs = interfaceList
				.getEventOutputs();
		if (eventOutputs.isEmpty()) {
			System.out.println("[FBEditPart] WARNING: No event outputs found");
			return;
		}

		final org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement outputPin = eventOutputs.get(0);
		System.out.println("[FBEditPart] Found output pin: " + outputPin.getName());

		// Activate chain mode
		final org.eclipse.fordiac.ide.gef.tools.ChainModeManager chainManager = org.eclipse.fordiac.ide.gef.tools.ChainModeManager
				.getInstance();
		chainManager.enterChainMode(fb, outputPin);

		// Refresh visual
		refresh();

		System.out.println("[FBEditPart] Chain mode activated!");

		// ⭐ NEW: Automatically open the popup at the calculated position
		triggerChainPopup();
	}

	/**
	 * Trigger the direct edit popup at the calculated chain position. This opens
	 * the type selection dialog where the next FB should be placed.
	 */
	private void triggerChainPopup() {
		System.out.println("[FBEditPart] triggerChainPopup called");

		// Get the bounds of the current FB's figure (screen coordinates)
		final org.eclipse.draw2d.geometry.Rectangle bounds = getFigure().getBounds().getCopy();

		// Calculate next position based on figure bounds (150px spacing to the right)
		final org.eclipse.draw2d.geometry.Point nextPosition = new org.eclipse.draw2d.geometry.Point(
				bounds.x + bounds.width + 150, bounds.y);

		// Convert to absolute coordinates
		getFigure().translateToAbsolute(nextPosition);

		System.out.println("[FBEditPart] Position from figure bounds: " + nextPosition);

		// Get the parent network edit part
		final org.eclipse.gef.EditPart parent = getParent();
		if (!(parent instanceof final org.eclipse.fordiac.ide.application.editparts.FBNetworkEditPart networkEditPart)) {
			System.out.println("[FBEditPart] ERROR: Parent is not FBNetworkEditPart");
			return;
		}

		// Use timerExec with delay to ensure UI is fully ready
		org.eclipse.swt.widgets.Display.getDefault().timerExec(100, () -> {
			System.out.println("[FBEditPart] Timer execution - triggering direct edit");

			// Get the direct edit policy directly
			final org.eclipse.gef.EditPolicy policy = networkEditPart
					.getEditPolicy(org.eclipse.gef.EditPolicy.DIRECT_EDIT_ROLE);

			if (policy instanceof final org.eclipse.fordiac.ide.application.policies.AbstractCreateInstanceDirectEditPolicy createPolicy) {
				// Create a selection request at the calculated position
				final org.eclipse.gef.requests.SelectionRequest request = new org.eclipse.gef.requests.SelectionRequest();
				request.setType(org.eclipse.gef.RequestConstants.REQ_DIRECT_EDIT);
				request.setLocation(nextPosition);

				System.out.println("[FBEditPart] Calling performDirectEdit at position: " + nextPosition);
				createPolicy.performDirectEdit(request);
			} else {
				System.out.println("[FBEditPart] ERROR: Could not get direct edit policy");
			}
		});
	}

}