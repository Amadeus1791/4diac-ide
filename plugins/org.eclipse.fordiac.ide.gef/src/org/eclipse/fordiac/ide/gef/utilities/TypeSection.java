/*******************************************************************************
 * Copyright (c) 2025
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.fordiac.ide.gef.utilities;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;

/**
 * Represents a section in the type selection popup (e.g., "Recent",
 * "Favorites", "All Categories") Supports both flat lists and nested
 * hierarchical sections.
 */
public class TypeSection {
	private final String name;
	private final List<TypeEntry> entries;
	private final List<TypeSection> childSections; // NEW: For hierarchical structure

	public TypeSection(final String name) {
		this.name = name;
		this.entries = new ArrayList<>();
		this.childSections = new ArrayList<>(); // NEW
	}

	public TypeSection(final String name, final List<TypeEntry> entries) {
		this.name = name;
		this.entries = new ArrayList<>(entries);
		this.childSections = new ArrayList<>(); // NEW
	}

	public String getName() {
		return name;
	}

	/**
	 * Get display name with item count (e.g., "Recent (3)") For nested sections,
	 * counts all types recursively.
	 */
	public String getDisplayName() {
		final int totalCount = getTotalCount();
		if (totalCount == 0) {
			return name;
		}
		return name + " (" + totalCount + ")";
	}

	public List<TypeEntry> getEntries() {
		return entries;
	}

	// NEW: Support for child sections
	public List<TypeSection> getChildSections() {
		return childSections;
	}

	// NEW: Add a child section
	public void addChildSection(final TypeSection section) {
		childSections.add(section);
	}

	// NEW: Check if has any children (sections or entries)
	public boolean hasChildren() {
		return !childSections.isEmpty() || !entries.isEmpty();
	}

	// NEW: Get total count including child sections
	public int getTotalCount() {
		int count = entries.size();
		for (final TypeSection child : childSections) {
			count += child.getTotalCount();
		}
		return count;
	}

	public void addEntry(final TypeEntry entry) {
		entries.add(entry);
	}

	public boolean isEmpty() {
		return entries.isEmpty() && childSections.isEmpty(); // UPDATED
	}

	public int size() {
		return entries.size();
	}
}