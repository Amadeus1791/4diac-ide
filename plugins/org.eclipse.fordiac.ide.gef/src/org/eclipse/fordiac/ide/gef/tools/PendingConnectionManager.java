package org.eclipse.fordiac.ide.gef.tools;

import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;

/**
 * Manages pending auto-connection state that survives tool deactivation
 */
public class PendingConnectionManager {
	private static PendingConnectionManager instance;

	private IInterfaceElement sourcePin;
	private FBNetwork targetNetwork;
	private boolean isActive;

	private PendingConnectionManager() {
		// Singleton
	}

	public static PendingConnectionManager getInstance() {
		if (instance == null) {
			instance = new PendingConnectionManager();
		}
		return instance;
	}

	public void setPending(final IInterfaceElement sourcePin, final FBNetwork targetNetwork) {
		this.sourcePin = sourcePin;
		this.targetNetwork = targetNetwork;
		this.isActive = true;
		System.out.println("PendingConnectionManager: State stored");
	}

	public boolean hasPending() {
		return isActive && sourcePin != null && targetNetwork != null;
	}

	public IInterfaceElement getSourcePin() {
		return sourcePin;
	}

	public FBNetwork getTargetNetwork() {
		return targetNetwork;
	}

	public void clear() {
		System.out.println("PendingConnectionManager: State cleared");
		this.sourcePin = null;
		this.targetNetwork = null;
		this.isActive = false;
	}
}