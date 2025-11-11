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
 * Represents a section in the type selection popup (e.g., "Recent", "Favorites", "All Categories")
 */
public class TypeSection {
	private final String name;
	private final List<TypeEntry> entries;
	
	public TypeSection(final String name) {
		this.name = name;
		this.entries = new ArrayList<>();
	}
	
	public TypeSection(final String name, final List<TypeEntry> entries) {
		this.name = name;
		this.entries = new ArrayList<>(entries);
	}
	
	public String getName() {
		return name;
	}
	
	/**
	 * Get display name with item count (e.g., "Recent (3)")
	 */
	public String getDisplayName() {
		if (entries.isEmpty()) {
			return name;
		}
		return name + " (" + entries.size() + ")";
	}
	
	public List<TypeEntry> getEntries() {
		return entries;
	}
	
	public void addEntry(final TypeEntry entry) {
		entries.add(entry);
	}
	
	public boolean isEmpty() {
		return entries.isEmpty();
	}
	
	public int size() {
		return entries.size();
	}
}
