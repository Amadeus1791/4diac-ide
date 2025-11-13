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
 *   [Your Name] - initial implementation for drag-to-create auto-connection
 *******************************************************************************/
package org.eclipse.fordiac.ide.gef.tools;

import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;

/**
 * Manages pending auto-connection state that survives tool deactivation.
 *
 * When a user drags from an output pin to empty canvas to create a new FB, the
 * connection tool gets deactivated when the type selection dialog appears. This
 * manager preserves the connection intent across that deactivation so the
 * connection can be automatically created after the new FB is placed.
 */
public class PendingConnectionManager {
	private static PendingConnectionManager instance;

	private IInterfaceElement sourcePin;
	private FBNetwork targetNetwork;
	private boolean isActive;

	private PendingConnectionManager() {
		// Singleton pattern
	}

	/**
	 * Get the singleton instance of the manager.
	 *
	 * @return the manager instance
	 */
	public static PendingConnectionManager getInstance() {
		if (instance == null) {
			instance = new PendingConnectionManager();
		}
		return instance;
	}

	/**
	 * Store a pending connection that should be created after FB placement.
	 *
	 * @param sourcePin     the output pin to connect from
	 * @param targetNetwork the network where the connection will be created
	 */
	public void setPending(final IInterfaceElement sourcePin, final FBNetwork targetNetwork) {
		this.sourcePin = sourcePin;
		this.targetNetwork = targetNetwork;
		this.isActive = true;
	}

	/**
	 * Check if there is a pending connection waiting to be created.
	 *
	 * @return true if pending connection exists
	 */
	public boolean hasPending() {
		return isActive && sourcePin != null && targetNetwork != null;
	}

	/**
	 * Get the source pin for the pending connection.
	 *
	 * @return the source interface element
	 */
	public IInterfaceElement getSourcePin() {
		return sourcePin;
	}

	/**
	 * Get the target network for the pending connection.
	 *
	 * @return the FB network
	 */
	public FBNetwork getTargetNetwork() {
		return targetNetwork;
	}

	/**
	 * Clear the pending connection state. Should be called after successful
	 * connection or on cancellation.
	 */
	public void clear() {
		this.sourcePin = null;
		this.targetNetwork = null;
		this.isActive = false;
	}
}