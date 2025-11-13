/*******************************************************************************
 * Copyright (c) 2025 Primetals Technologies Austria GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Wolfgang Schedl - initial API and implementation
 *******************************************************************************/
package org.eclipse.fordiac.ide.systemmanagement.ui.handlers;

import org.eclipse.core.resources.IFile;
import org.eclipse.fordiac.ide.gef.utilities.FavoritesManager;
import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * Dynamic menu contribution that shows "Add to Favorites" or "Remove from
 * Favorites" based on the current selection's favorite status.
 */
public class FavoritesMenuContribution extends ContributionItem {

	private final FavoritesManager favoritesManager = new FavoritesManager();

	@Override
	public void fill(final Menu menu, final int index) {
		final String typeName = getSelectedTypeName();

		if (typeName == null) {
			return; // No valid selection
		}

		final boolean isFavorite = favoritesManager.isFavorite(typeName);
		final String label = isFavorite ? "Remove from Favorites" : "Add to Favorites"; //$NON-NLS-1$ //$NON-NLS-2$

		final MenuItem menuItem = new MenuItem(menu, SWT.PUSH, index);
		menuItem.setText(label);

		// Use different icons for Add vs Remove
		final ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
		if (isFavorite) {
			// Remove from Favorites → use remove icon
			final Image removeIcon = sharedImages.getImage(ISharedImages.IMG_ELCL_REMOVE);
			if (removeIcon != null) {
				menuItem.setImage(removeIcon);
			}
		} else {
			// Add to Favorites → use bookmark icon
			final Image bookmarkIcon = sharedImages.getImage(ISharedImages.IMG_OBJS_BKMRK_TSK);
			if (bookmarkIcon != null) {
				menuItem.setImage(bookmarkIcon);
			}
		}

		menuItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				toggleFavorite(typeName);
			}
		});
	}

	/**
	 * Get the type name from the current selection.
	 */
	private String getSelectedTypeName() {
		final ISelectionService selectionService = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getSelectionService();
		final ISelection selection = selectionService.getSelection();

		if (!(selection instanceof final IStructuredSelection structuredSelection)) {
			return null;
		}

		final Object firstElement = structuredSelection.getFirstElement();

		// Handle TypeEntry
		if (firstElement instanceof TypeEntry) {
			return ((TypeEntry) firstElement).getFullTypeName();
		}

		// Handle IFile (.fbt, .fct, .adp, .sub)
		if (firstElement instanceof final IFile file) {
			final String extension = file.getFileExtension();

			if (isFBTypeFile(extension)) {
				return TypeEntry.getTypeNameFromFile(file);
			}
		}

		return null;
	}

	/**
	 * Toggle favorite status.
	 */
	private void toggleFavorite(final String typeName) {
		if (typeName == null || typeName.isEmpty()) {
			return;
		}

		if (favoritesManager.isFavorite(typeName)) {
			favoritesManager.removeFavorite(typeName);
		} else {
			favoritesManager.addFavorite(typeName);
		}
	}

	/**
	 * Check if file is an FB type.
	 */
	private static boolean isFBTypeFile(final String extension) {
		if (extension == null) {
			return false;
		}
		return "fbt".equalsIgnoreCase(extension) || //$NON-NLS-1$
				"fct".equalsIgnoreCase(extension) || //$NON-NLS-1$
				"adp".equalsIgnoreCase(extension) || //$NON-NLS-1$
				"sub".equalsIgnoreCase(extension); //$NON-NLS-1$
	}

	@Override
	public boolean isDynamic() {
		return true; // Menu item label changes based on state
	}
}