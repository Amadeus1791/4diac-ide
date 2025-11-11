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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Tracks usage frequency of function block types across all projects. Frequency
 * is instance-scoped (per workspace/user) and persists across Eclipse sessions.
 *
 * This complements: - MostRecentlyUsedTracker (project-scoped, tracks recency)
 * - FavoritesManager (instance-scoped, explicit bookmarks)
 */
public class FrequencyTracker {

	/** Preference node ID for storing frequency data */
	private static final String PREF_NODE = "org.eclipse.fordiac.ide.gef";

	/** Preference key for frequency data */
	private static final String PREF_KEY = "frequency_fb_types"; //$NON-NLS-1$

	/** Delimiter for serializing frequency entries */
	private static final String ENTRY_DELIMITER = ";"; //$NON-NLS-1$

	/** Delimiter between type name and count */
	private static final String COUNT_DELIMITER = ":"; //$NON-NLS-1$

	/** Eclipse instance-scoped preferences */
	private final IEclipsePreferences preferences;

	/** In-memory frequency map: typeName -> usage count */
	private final Map<String, Integer> frequencyMap;

	/**
	 * Create a frequency tracker (instance-scoped for the current user/workspace).
	 */
	public FrequencyTracker() {
		this.preferences = ConfigurationScope.INSTANCE.getNode(PREF_NODE);
		this.frequencyMap = new HashMap<>();
		loadFromPreferences();
	}

	/**
	 * Record that a type was used (increment its usage count).
	 *
	 * @param fbTypeName the full type name (e.g., "E_CYCLE", "E_SWITCH")
	 */
	public synchronized void recordUsage(final String fbTypeName) {
		if (fbTypeName == null || fbTypeName.isEmpty()) {
			return;
		}

		// Increment count (or initialize to 1 if first use)
		final int currentCount = frequencyMap.getOrDefault(fbTypeName, 0);
		frequencyMap.put(fbTypeName, currentCount + 1);

		saveToPreferences();
	}

	/**
	 * Get the usage count for a specific type.
	 *
	 * @param fbTypeName the type name to query
	 * @return usage count, or 0 if never used
	 */
	public synchronized int getUsageCount(final String fbTypeName) {
		if (fbTypeName == null || fbTypeName.isEmpty()) {
			return 0;
		}
		return frequencyMap.getOrDefault(fbTypeName, 0);
	}

	/**
	 * Get the most frequently used types, sorted by usage count (descending).
	 *
	 * @param limit maximum number of types to return (e.g., top 5)
	 * @return list of type names sorted by frequency (most used first)
	 */
	public synchronized List<String> getMostFrequent(final int limit) {
		if (limit <= 0) {
			return Collections.emptyList();
		}

		return frequencyMap.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
						.thenComparing(Map.Entry.comparingByKey())) // Secondary sort by name for stability
				.limit(limit).map(Map.Entry::getKey).collect(Collectors.toList());
	}

	/**
	 * Get all tracked types with their counts, sorted by frequency (descending).
	 *
	 * @return unmodifiable list of entries sorted by usage count
	 */
	public synchronized List<FrequencyEntry> getAllFrequencies() {
		final List<FrequencyEntry> entries = new ArrayList<>();

		for (final Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
			entries.add(new FrequencyEntry(entry.getKey(), entry.getValue()));
		}

		// Sort by count descending, then by name ascending
		entries.sort(Comparator.comparingInt(FrequencyEntry::getCount).reversed()
				.thenComparing(FrequencyEntry::getTypeName));

		return Collections.unmodifiableList(entries);
	}

	/**
	 * Clear all frequency data.
	 */
	public synchronized void clear() {
		frequencyMap.clear();
		saveToPreferences();
	}

	/**
	 * Get the number of tracked types.
	 *
	 * @return count of types with recorded usage
	 */
	public synchronized int getTrackedTypeCount() {
		return frequencyMap.size();
	}

	/**
	 * Load frequency data from Eclipse instance preferences.
	 */
	private void loadFromPreferences() {
		try {
			final String stored = preferences.get(PREF_KEY, ""); //$NON-NLS-1$

			if (stored != null && !stored.isEmpty()) {
				final String[] entries = stored.split(ENTRY_DELIMITER);
				for (final String entry : entries) {
					final String trimmed = entry.trim();
					if (trimmed.isEmpty()) {
						continue;
					}

					// Parse "TypeName:Count"
					final String[] parts = trimmed.split(COUNT_DELIMITER);
					if (parts.length == 2) {
						final String typeName = parts[0].trim();
						try {
							final int count = Integer.parseInt(parts[1].trim());
							if (!typeName.isEmpty() && count > 0) {
								frequencyMap.put(typeName, count);
							}
						} catch (final NumberFormatException e) {
							// Skip invalid entries
							System.err.println("[FrequencyTracker] Invalid count for: " + entry); //$NON-NLS-1$
						}
					}
				}
			}
		} catch (final Exception e) {
			System.err.println("[FrequencyTracker] Failed to load preferences: " + e.getMessage()); //$NON-NLS-1$
		}
	}

	/**
	 * Save frequency data to Eclipse instance preferences.
	 */
	private void saveToPreferences() {
		try {
			final StringBuilder sb = new StringBuilder();
			int i = 0;
			for (final Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
				if (i > 0) {
					sb.append(ENTRY_DELIMITER);
				}
				sb.append(entry.getKey());
				sb.append(COUNT_DELIMITER);
				sb.append(entry.getValue());
				i++;
			}

			preferences.put(PREF_KEY, sb.toString());
			preferences.flush();

		} catch (final BackingStoreException e) {
			System.err.println("[FrequencyTracker] Failed to save preferences: " + e.getMessage()); //$NON-NLS-1$
		} catch (final Exception e) {
			System.err.println("[FrequencyTracker] Unexpected error saving frequencies: " + e.getMessage()); //$NON-NLS-1$
			e.printStackTrace();
		}
	}

	/**
	 * Data class representing a type's usage frequency.
	 */
	public static class FrequencyEntry {
		private final String typeName;
		private final int count;

		public FrequencyEntry(final String typeName, final int count) {
			this.typeName = typeName;
			this.count = count;
		}

		public String getTypeName() {
			return typeName;
		}

		public int getCount() {
			return count;
		}

		@Override
		public String toString() {
			return typeName + " (" + count + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
}