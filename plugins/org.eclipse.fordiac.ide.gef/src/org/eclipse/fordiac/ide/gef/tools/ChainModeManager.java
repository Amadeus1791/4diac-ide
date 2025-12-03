/*******************************************************************************
 * Copyright (c) 2025 [Your Institution/Name]
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   [Your Name] - initial implementation for chain mode functionality
 *******************************************************************************/
package org.eclipse.fordiac.ide.gef.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.fordiac.ide.model.libraryElement.FBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;

/**
 * Manages the chain creation mode state where users can quickly create and
 * connect multiple function blocks in sequence.
 *
 * Chain mode is activated by Shift+Click on a placed FB and allows rapid
 * sequential element creation without repeated modal dialogs.
 */
public class ChainModeManager {
	private static ChainModeManager instance;

	// Chain mode state
	private boolean chainModeActive = false;
	private FBNetworkElement chainSourceFB;
	private IInterfaceElement chainSourcePin;
	private final List<FBNetworkElement> chainElements = new ArrayList<>();

	// Position tracking for auto-placement
	private org.eclipse.draw2d.geometry.Point nextElementPosition;
	private static final int HORIZONTAL_SPACING = 150; // Spacing between FBs in pixels

	private ChainModeManager() {
		System.out.println("[ChainModeManager] Instance created");
	}

	/**
	 * Get the singleton instance of the manager.
	 *
	 * @return the manager instance
	 */
	public static ChainModeManager getInstance() {
		if (instance == null) {
			instance = new ChainModeManager();
		}
		return instance;
	}

	/**
	 * Enter chain mode starting from the given FB and output pin.
	 *
	 * @param sourceFB  the function block to start chaining from
	 * @param sourcePin the output pin to connect from
	 */
	public void enterChainMode(final FBNetworkElement sourceFB, final IInterfaceElement sourcePin) {
		System.out.println("[ChainModeManager] Entering chain mode");
		System.out.println("  Source FB: " + (sourceFB != null ? sourceFB.getName() : "null"));
		System.out.println("  Source Pin: " + (sourcePin != null ? sourcePin.getName() : "null"));

		this.chainModeActive = true;
		this.chainSourceFB = sourceFB;
		this.chainSourcePin = sourcePin;
		this.chainElements.clear();
		this.chainElements.add(sourceFB);

		// Calculate position for the first chained element
		calculateNextPosition(sourceFB);

		System.out.println("[ChainModeManager] Chain mode activated");
	}

	/**
	 * Exit chain mode and clear all state.
	 */
	public void exitChainMode() {
		System.out.println("[ChainModeManager] Exiting chain mode");
		System.out.println("  Chain had " + chainElements.size() + " elements");

		this.chainModeActive = false;
		this.chainSourceFB = null;
		this.chainSourcePin = null;
		this.chainElements.clear();

		System.out.println("[ChainModeManager] Chain mode deactivated");
	}

	/**
	 * Add a new element to the chain.
	 *
	 * @param newFB the newly created function block
	 */
	public void addToChain(final FBNetworkElement newFB) {
		if (!chainModeActive) {
			System.out.println("[ChainModeManager] WARNING: Trying to add to chain when mode is not active");
			return;
		}

		System.out.println("[ChainModeManager] Adding element to chain: " + (newFB != null ? newFB.getName() : "null"));
		chainElements.add(newFB);

		// Update source for next iteration (chain from the newly added FB's output)
		this.chainSourceFB = newFB;
		// Note: chainSourcePin will be updated by the caller based on the new FB's
		// interface

		// Recalculate position for the next element
		calculateNextPosition(newFB);

		System.out.println("[ChainModeManager] Chain now has " + chainElements.size() + " elements");
	}

	/**
	 * Update the source pin for the next chaining operation. This is called after
	 * adding a new FB to update which output pin should be used for the next
	 * connection.
	 *
	 * @param newSourcePin the new output pin to chain from
	 */
	public void updateSourcePin(final IInterfaceElement newSourcePin) {
		System.out.println("[ChainModeManager] Updating source pin to: "
				+ (newSourcePin != null ? newSourcePin.getName() : "null"));
		this.chainSourcePin = newSourcePin;
	}

	/**
	 * Check if chain mode is currently active.
	 *
	 * @return true if in chain mode
	 */
	public boolean isChainModeActive() {
		return chainModeActive;
	}

	/**
	 * Get the current source FB for chaining.
	 *
	 * @return the source function block
	 */
	public FBNetworkElement getChainSourceFB() {
		return chainSourceFB;
	}

	/**
	 * Get the current source pin for chaining.
	 *
	 * @return the source interface element
	 */
	public IInterfaceElement getChainSourcePin() {
		return chainSourcePin;
	}

	/**
	 * Get the list of elements in the current chain.
	 *
	 * @return list of function blocks in the chain
	 */
	public List<FBNetworkElement> getChainElements() {
		return new ArrayList<>(chainElements);
	}

	/**
	 * Get a breadcrumb representation of the current chain.
	 *
	 * @return breadcrumb string like "E_CYCLE > Counter > Timer > ?"
	 */
	public String getChainBreadcrumb() {
		if (!chainModeActive || chainElements.isEmpty()) {
			return "";
		}

		final StringBuilder breadcrumb = new StringBuilder();
		for (int i = 0; i < chainElements.size(); i++) {
			if (i > 0) {
				breadcrumb.append(" > ");
			}
			breadcrumb.append(chainElements.get(i).getName());
		}
		breadcrumb.append(" > ?");

		return breadcrumb.toString();
	}

	/**
	 * Calculate the position for the next element in the chain based on the last
	 * FB's position. Uses horizontal chaining (elements placed to the right).
	 *
	 * @param lastFB the last function block in the chain
	 */
	private void calculateNextPosition(final FBNetworkElement lastFB) {
		if (lastFB == null) {
			// Fallback position if no source FB
			nextElementPosition = new org.eclipse.draw2d.geometry.Point(100, 100);
			System.out.println("[ChainModeManager] No source FB, using fallback position");
			return;
		}

		// Get the position of the last FB in the chain
		// FBNetworkElement implements IPositionableElement which has getPosition()
		final org.eclipse.fordiac.ide.model.libraryElement.Position position = lastFB.getPosition();

		if (position == null) {
			// Fallback if position is not set
			nextElementPosition = new org.eclipse.draw2d.geometry.Point(100, 100);
			System.out.println("[ChainModeManager] No position found, using fallback");
			return;
		}

		final double lastX = position.getX();
		final double lastY = position.getY();

		System.out.println("[ChainModeManager] Last FB position: x=" + lastX + ", y=" + lastY);

		// Estimate FB width (typical FB is around 120-150px wide)
		// In a real implementation, you might want to get the actual width from the
		// figure
		final int estimatedFBWidth = 120;

		// Calculate next position: place to the right with spacing
		// Note: 4diac uses a different coordinate system, positions might be in IEC
		// units
		// We need to use the same coordinate system
		nextElementPosition = new org.eclipse.draw2d.geometry.Point(
				(int) (lastX + estimatedFBWidth + HORIZONTAL_SPACING), (int) lastY);

		System.out.println("[ChainModeManager] Calculated next position: x=" + nextElementPosition.x + ", y="
				+ nextElementPosition.y + " (using lastX=" + lastX + " + " + (estimatedFBWidth + HORIZONTAL_SPACING)
				+ ")");
	}

	/**
	 * Get the calculated position where the next element should be placed.
	 *
	 * @return the position as a Point, or (0,0) if not calculated
	 */
	public org.eclipse.draw2d.geometry.Point getNextElementPosition() {
		if (nextElementPosition != null) {
			return nextElementPosition.getCopy();
		}
		return new org.eclipse.draw2d.geometry.Point(0, 0);
	}
}