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

import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;

/**
 * Content provider for the structured type selection tree
 */
public class TypeSectionContentProvider implements ITreeContentProvider {

	@Override
	public Object[] getElements(final Object inputElement) {
		if (inputElement instanceof List<?>) {
			return ((List<?>) inputElement).toArray();
		}
		return new Object[0];
	}

	@Override
	public Object[] getChildren(final Object parentElement) {
		if (parentElement instanceof TypeSection) {
			return ((TypeSection) parentElement).getEntries().toArray();
		}
		return new Object[0];
	}

	@Override
	public Object getParent(final Object element) {
		return null; // Not needed for our use case
	}

	@Override
	public boolean hasChildren(final Object element) {
		if (element instanceof TypeSection) {
			return !((TypeSection) element).isEmpty();
		}
		return false;
	}
}
