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
 *   [Your Name] - initial API and implementation
 *******************************************************************************/
package org.eclipse.fordiac.ide.gef.utilities;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Manages user-favorite function block types across all projects. Favorites are
 * instance-scoped (per workspace/user) and persist across Eclipse sessions.
 */
public class FavoritesManager {

	/** Preference node ID for storing favorites data */
	private static final String PREF_NODE = "org.eclipse.fordiac.ide.gef";

	/** Preference key for favorites list */
	private static final String PREF_KEY = "favorite_fb_types"; //$NON-NLS-1$

	/** Delimiter for serializing favorites list */
	private static final String DELIMITER = ";"; //$NON-NLS-1$

	/** Eclipse instance-scoped preferences */
	private final IEclipsePreferences preferences;

	/** In-memory favorites set (insertion order preserved) */
	private final LinkedHashSet<String> favoritesList;

	/**
	 * Create a favorites manager (instance-scoped for the current user/workspace).
	 */
	public FavoritesManager() {
		this.preferences = ConfigurationScope.INSTANCE.getNode(PREF_NODE);
		this.favoritesList = new LinkedHashSet<>();
		loadFromPreferences();
	}

	/**
	 * Add a type to favorites.
	 *
	 * @param fbTypeName the full type name (e.g., "E_CYCLE", "E_SWITCH")
	 * @return true if the type was added (wasn't already a favorite)
	 */
//	public synchronized boolean addFavorite(final String fbTypeName) {
//		if (fbTypeName == null || fbTypeName.isEmpty()) {
//			return false;
//		}
//
//		final boolean added = favoritesList.add(fbTypeName);
//
//		if (added) {
//			saveToPreferences();
//		}
//
//		return added;
//	}
	public synchronized boolean addFavorite(final String fbTypeName) {
		if (fbTypeName == null || fbTypeName.isEmpty()) {
			return false;
		}

		System.out.println(
				"[FavoritesManager] Adding '" + fbTypeName + "' to list. Current size: " + favoritesList.size());
		final boolean added = favoritesList.add(fbTypeName);
		System.out.println("[FavoritesManager] Add result: " + added + ", new size: " + favoritesList.size());
		System.out.println("[FavoritesManager] List contents: " + favoritesList);

		if (added) {
			saveToPreferences();
		}

		return added;
	}

	/**
	 * Remove a type from favorites.
	 *
	 * @param fbTypeName the full type name to remove
	 * @return true if the type was removed (was a favorite)
	 */
	public synchronized boolean removeFavorite(final String fbTypeName) {
		if (fbTypeName == null || fbTypeName.isEmpty()) {
			return false;
		}

		final boolean removed = favoritesList.remove(fbTypeName);

		if (removed) {
			saveToPreferences();
		}

		return removed;
	}

	/**
	 * Check if a type is favorited.
	 *
	 * @param fbTypeName the full type name to check
	 * @return true if the type is in favorites
	 */
	public synchronized boolean isFavorite(final String fbTypeName) {
		if (fbTypeName == null || fbTypeName.isEmpty()) {
			return false;
		}
		return favoritesList.contains(fbTypeName);
	}

	/**
	 * Get all favorite type names in insertion order.
	 *
	 * @return unmodifiable set of type names (preserves insertion order)
	 */
	public synchronized Set<String> getFavorites() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(favoritesList));
	}

	/**
	 * Clear all favorites.
	 */
	public synchronized void clear() {
		favoritesList.clear();
		saveToPreferences();
	}

	/**
	 * Get the number of favorited types.
	 *
	 * @return count of favorites
	 */
	public synchronized int getFavoriteCount() {
		return favoritesList.size();
	}

	/**
	 * Load favorites list from Eclipse instance preferences.
	 */
	private void loadFromPreferences() {
		try {
			final String stored = preferences.get(PREF_KEY, ""); //$NON-NLS-1$

			if (stored != null && !stored.isEmpty()) {
				final String[] items = stored.split(DELIMITER);
				for (final String item : items) {
					final String trimmed = item.trim();
					if (!trimmed.isEmpty()) {
						favoritesList.add(trimmed);
					}
				}
			}
		} catch (final Exception e) {
			System.err.println("[Favorites] Failed to load preferences: " + e.getMessage()); //$NON-NLS-1$
		}
	}

	/**
	 * Save favorites list to Eclipse instance preferences.
	 */
//	private void saveToPreferences() {
//		try {
//			final StringBuilder sb = new StringBuilder();
//			int i = 0;
//			for (final String favorite : favoritesList) {
//				if (i > 0) {
//					sb.append(DELIMITER);
//				}
//				sb.append(favorite);
//				i++;
//			}
//
//			preferences.put(PREF_KEY, sb.toString());
//			preferences.flush();
//
//		} catch (final BackingStoreException e) {
//			System.err.println("[Favorites] Failed to save preferences: " + e.getMessage()); //$NON-NLS-1$
//		} catch (final Exception e) {
//			System.err.println("[Favorites] Unexpected error saving favorites: " + e.getMessage()); //$NON-NLS-1$
//			e.printStackTrace();
//		}
//	}
//	private void saveToPreferences() {
//		try {
//			final String str = """
//					""";
//			// ... existing code ...
//
//			System.out.println("[FavoritesManager] About to save: " + str);
//			preferences.put(PREF_KEY, str);
//			System.out.println("[FavoritesManager] Put succeeded, about to flush...");
//			preferences.flush();
//			System.out.println("[FavoritesManager] Flush succeeded!");
//
//		} catch (final BackingStoreException e) {
//			System.err.println("[FavoritesManager] Failed to save preferences: " + e.getMessage());
//			e.printStackTrace(); // IMPORTANT: Show full stack trace
//		}
//	}

	private void saveToPreferences() {
		try {
			final StringBuilder sb = new StringBuilder();

			System.out.println(
					"[FavoritesManager] saveToPreferences called. favoritesList size: " + favoritesList.size());
			System.out.println("[FavoritesManager] favoritesList contents: " + favoritesList);

			int i = 0;
			for (final String favorite : favoritesList) {
				System.out.println("[FavoritesManager] Processing favorite #" + i + ": '" + favorite + "'");
				if (i > 0) {
					sb.append(DELIMITER);
				}
				sb.append(favorite);
				i++;
			}

			System.out.println("[FavoritesManager] About to save: " + sb.toString());
			preferences.put(PREF_KEY, sb.toString());
			System.out.println("[FavoritesManager] Put succeeded, about to flush...");
			preferences.flush();
			System.out.println("[FavoritesManager] Flush succeeded!");

		} catch (final BackingStoreException e) {
			System.err.println("[FavoritesManager] Failed to save preferences: " + e.getMessage());
			e.printStackTrace();
		} catch (final Exception e) {
			System.err.println("[FavoritesManager] Unexpected error saving favorites: " + e.getMessage());
			e.printStackTrace();
		}
	}

}