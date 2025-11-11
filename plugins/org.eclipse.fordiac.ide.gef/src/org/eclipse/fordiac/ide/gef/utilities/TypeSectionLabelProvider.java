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

import org.eclipse.fordiac.ide.model.edit.providers.ResultListLabelProvider;
import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;

/**
 * Label provider for the structured type selection tree.
 * Handles both section headers and type entries.
 */
public class TypeSectionLabelProvider extends LabelProvider implements IStyledLabelProvider {

	private final ResultListLabelProvider typeEntryLabelProvider;
	private Font boldFont;

	public TypeSectionLabelProvider(final ResultListLabelProvider typeEntryLabelProvider) {
		this.typeEntryLabelProvider = typeEntryLabelProvider;
		createBoldFont();
	}

	private void createBoldFont() {
		final Display display = Display.getCurrent();
		if (display != null) {
			final Font systemFont = display.getSystemFont();
			final FontData[] fontData = systemFont.getFontData();
			for (final FontData fd : fontData) {
				fd.setStyle(SWT.BOLD);
			}
			boldFont = new Font(display, fontData);
		}
	}

	@Override
	public StyledString getStyledText(final Object element) {
		if (element instanceof TypeSection) {
			final TypeSection section = (TypeSection) element;
			
			// Use display name with count
			final String displayName = section.getDisplayName();
			
			// Make section headers bold and gray
			final StyledString styledString = new StyledString(displayName, 
					StyledString.QUALIFIER_STYLER);
			return styledString;
		} else if (element instanceof TypeEntry) {
			// Delegate to the existing type entry label provider
			return typeEntryLabelProvider.getStyledText(element);
		}
		return new StyledString(element.toString());
	}

	@Override
	public String getText(final Object element) {
		return getStyledText(element).getString();
	}

	public void setSearchString(final String searchString) {
		typeEntryLabelProvider.setSearchString(searchString);
	}

	@Override
	public void dispose() {
		if (boldFont != null && !boldFont.isDisposed()) {
			boldFont.dispose();
		}
		super.dispose();
	}
}
