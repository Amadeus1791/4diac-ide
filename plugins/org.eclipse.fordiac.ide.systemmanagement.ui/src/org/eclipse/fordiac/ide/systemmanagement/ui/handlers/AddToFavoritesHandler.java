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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.fordiac.ide.gef.utilities.FavoritesManager;
import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Handler for adding/removing FB types to/from favorites via System Explorer
 * context menu.
 */
public class AddToFavoritesHandler extends AbstractHandler {

	private final FavoritesManager favoritesManager = new FavoritesManager();

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final ISelection selection = HandlerUtil.getCurrentSelection(event);

		if (selection instanceof final IStructuredSelection structuredSelection) {
			final Object firstElement = structuredSelection.getFirstElement();

			// Handle TypeEntry (from Type Library view)
			if (firstElement instanceof final TypeEntry typeEntry) {
				toggleFavorite(typeEntry.getFullTypeName());
			}
			// Handle IFile (from System Explorer - .fbt, .fct, .adp, .sub files)
			else if (firstElement instanceof final IFile file) {
				final String extension = file.getFileExtension();

				// Only process FB type files
				if (isFBTypeFile(extension)) {
					final String typeName = TypeEntry.getTypeNameFromFile(file);
					toggleFavorite(typeName);
				}
			}
		}

		return null;
	}

	/**
	 * Toggle favorite status for a given type name.
	 */
	private void toggleFavorite(final String typeName) {
		if (typeName == null || typeName.isEmpty()) {
			return;
		}

		if (favoritesManager.isFavorite(typeName)) {
			favoritesManager.removeFavorite(typeName);
			System.out.println("[AddToFavoritesHandler] Removed from favorites: " + typeName); //$NON-NLS-1$
		} else {
			favoritesManager.addFavorite(typeName);
			System.out.println("[AddToFavoritesHandler] Added to favorites: " + typeName); //$NON-NLS-1$
		}
	}

	/**
	 * Check if the file extension represents an FB type.
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
}