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
 *   [Your Name] - initial implementation for drag-to-create port selection
 *******************************************************************************/
package org.eclipse.fordiac.ide.gef.dialogs;

import java.util.List;

import org.eclipse.fordiac.ide.model.libraryElement.Event;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Dialog for selecting a target port when multiple compatible ports are
 * available during drag-to-create auto-connection.
 */
public class PortSelectionDialog extends Dialog {

	private final List<IInterfaceElement> compatiblePorts;
	private final String targetFBName;
	private IInterfaceElement selectedPort;
	private Button[] radioButtons;

	/**
	 * Create a new port selection dialog.
	 *
	 * @param parentShell     the parent shell
	 * @param compatiblePorts list of compatible ports to choose from
	 * @param targetFBName    name of the target FB for display
	 */
	public PortSelectionDialog(final Shell parentShell, final List<IInterfaceElement> compatiblePorts,
			final String targetFBName) {
		super(parentShell);
		this.compatiblePorts = compatiblePorts;
		this.targetFBName = targetFBName;
		this.selectedPort = compatiblePorts.isEmpty() ? null : compatiblePorts.get(0); // Default to first
	}

	@Override
	protected void configureShell(final Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Select Target Port"); //$NON-NLS-1$
	}

	@Override
	protected Control createDialogArea(final Composite parent) {
		final Composite container = (Composite) super.createDialogArea(parent);
		final GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 10;
		layout.marginWidth = 10;
		layout.verticalSpacing = 10;
		container.setLayout(layout);

		// Title label
		final Label titleLabel = new Label(container, SWT.WRAP);
		titleLabel.setText(String.format("Multiple compatible ports found on '%s'.%nSelect target port:", //$NON-NLS-1$
				targetFBName));
		titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// Radio button group
		radioButtons = new Button[compatiblePorts.size()];
		for (int i = 0; i < compatiblePorts.size(); i++) {
			final IInterfaceElement port = compatiblePorts.get(i);
			final Button radio = new Button(container, SWT.RADIO);
			radio.setText(formatPortLabel(port));
			radio.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			// Select first radio by default
			if (i == 0) {
				radio.setSelection(true);
			}

			// Store reference
			radioButtons[i] = radio;
			final int index = i;
			radio.addListener(SWT.Selection, event -> {
				if (radio.getSelection()) {
					selectedPort = compatiblePorts.get(index);
				}
			});
		}

		return container;
	}

	/**
	 * Format a port label with name and type information.
	 *
	 * @param port the interface element
	 * @return formatted label string
	 */
	private String formatPortLabel(final IInterfaceElement port) {
		final StringBuilder label = new StringBuilder();
		label.append(port.getName());

		if (port instanceof Event) {
			label.append(" (Event)"); //$NON-NLS-1$
		} else if (port instanceof final VarDeclaration var) {
			if (var.getType() != null) {
				label.append(" (").append(var.getType().getName()).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				label.append(" (Data)"); //$NON-NLS-1$
			}
		}

		return label.toString();
	}

	@Override
	protected void createButtonsForButtonBar(final Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	@Override
	protected void okPressed() {
		// selectedPort is already updated by radio button listeners
		super.okPressed();
	}

	@Override
	protected void cancelPressed() {
		selectedPort = null;
		super.cancelPressed();
	}

	/**
	 * Get the port selected by the user.
	 *
	 * @return the selected port, or null if cancelled
	 */
	public IInterfaceElement getSelectedPort() {
		return selectedPort;
	}
}