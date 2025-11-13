package org.eclipse.fordiac.ide.gef.dialogs;

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
 *   [Your Name] - initial implementation for drag-to-create functionality
 *******************************************************************************/

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog for quick element creation during drag-to-create workflow. Displays a
 * filtered list of available function block types and allows the user to search
 * and select a type to create.
 */
public class QuickElementCreationDialog extends Dialog {

	private static final int DIALOG_WIDTH = 400;
	private static final int DIALOG_HEIGHT = 500;

	private final List<String> availableTypes;
	private List<String> filteredTypes;
	private String selectedType;

	private Text searchField;
	private TableViewer typeListViewer;

	/**
	 * Create a new element creation dialog.
	 *
	 * @param parentShell    the parent shell
	 * @param availableTypes list of available function block type names
	 */
	public QuickElementCreationDialog(final Shell parentShell, final List<String> availableTypes) {
		super(parentShell);
		this.availableTypes = new ArrayList<>(availableTypes);
		this.filteredTypes = new ArrayList<>(availableTypes);
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	@Override
	protected void configureShell(final Shell shell) {
		super.configureShell(shell);
		shell.setText("Select Function Block Type");
		shell.setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
	}

	@Override
	protected Control createDialogArea(final Composite parent) {
		final Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout(1, false));

		// Description label
		final Label descLabel = new Label(container, SWT.WRAP);
		descLabel.setText("Select a function block type to create and connect:");
		descLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// Search field
		searchField = new Text(container, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
		searchField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		searchField.setMessage("Search types...");

		// Add search functionality
		searchField.addModifyListener(createSearchListener());

		// Type list
		typeListViewer = new TableViewer(container, SWT.BORDER | SWT.V_SCROLL | SWT.SINGLE);
		typeListViewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		typeListViewer.setContentProvider(ArrayContentProvider.getInstance());
		typeListViewer.setLabelProvider(new LabelProvider());
		typeListViewer.setInput(filteredTypes);

		// Double-click to select
		typeListViewer.addDoubleClickListener(event -> okPressed());

		// Select first item by default
		if (!filteredTypes.isEmpty()) {
			typeListViewer.getTable().select(0);
		}

		// Set focus to search field
		searchField.setFocus();

		return container;
	}

	/**
	 * Create the search listener that filters the type list.
	 */
	private ModifyListener createSearchListener() {
		return e -> {
			final String searchText = searchField.getText().toLowerCase().trim();

			if (searchText.isEmpty()) {
				// No search text - show all types
				filteredTypes = new ArrayList<>(availableTypes);
			} else {
				// Filter types by search text (substring match)
				filteredTypes = availableTypes.stream().filter(type -> type.toLowerCase().contains(searchText))
						.toList();
			}

			// Update viewer
			typeListViewer.setInput(filteredTypes);

			// Select first item if available
			if (!filteredTypes.isEmpty()) {
				typeListViewer.getTable().select(0);
			}
		};
	}

	@Override
	protected void okPressed() {
		// Get selected type from table
		final IStructuredSelection selection = typeListViewer.getStructuredSelection();
		if (!selection.isEmpty()) {
			selectedType = (String) selection.getFirstElement();
		}
		super.okPressed();
	}

	@Override
	protected void createButtonsForButtonBar(final Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	/**
	 * Get the selected function block type name.
	 *
	 * @return the selected type name, or null if cancelled
	 */
	public String getSelectedType() {
		return selectedType;
	}
}
