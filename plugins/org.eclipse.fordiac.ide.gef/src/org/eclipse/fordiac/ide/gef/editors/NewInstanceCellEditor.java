/*******************************************************************************
 * Copyright (c) 2019 Johannes Kepler University Linz
 * 				 2022 Primetals Technologies Germany GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Alois Zoitl - initial API and implementation and/or initial documentation
 *   Fabio Gandolfi - insideCell parameter to use the CellEditor inside TableViewer cells
 *******************************************************************************/
package org.eclipse.fordiac.ide.gef.editors;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.fordiac.ide.gef.Messages;
import org.eclipse.fordiac.ide.gef.utilities.CellEditorLayoutFactory;
import org.eclipse.fordiac.ide.gef.utilities.FavoritesManager;
import org.eclipse.fordiac.ide.gef.utilities.FrequencyTracker;
import org.eclipse.fordiac.ide.gef.utilities.MostRecentlyUsedTracker;
import org.eclipse.fordiac.ide.gef.utilities.TypeSection;
import org.eclipse.fordiac.ide.gef.utilities.TypeSectionContentProvider;
import org.eclipse.fordiac.ide.gef.utilities.TypeSectionLabelProvider;
import org.eclipse.fordiac.ide.model.edit.providers.ResultListLabelProvider;
import org.eclipse.fordiac.ide.model.typelibrary.PaletteFilter;
import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.fordiac.ide.ui.imageprovider.FordiacImage;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class NewInstanceCellEditor extends TextCellEditor {

	private static final int NUM_COLUMNS = 2;
	private static final int MAX_RECENT_ITEMS = 5;
	private static final int MAX_FREQUENT_ITEMS = 5;

	// if NewInstanceCellEditor is used inside a TableCell = true
	// if not for example like in main editor for creating new FBs = false(default)
	private boolean insideCell;

	private Composite container;
	private Button menuButton;
	protected Shell popupShell;
	protected TreeViewer treeViewer;
	private PaletteFilter paletteFilter;
	private boolean blockTreeSelection = false;
	private TypeEntry selectedEntry = null;
	protected Text textControl;
	private MostRecentlyUsedTracker mruTracker;
	private FavoritesManager favoritesManager;
	private FrequencyTracker frequencyTracker;

	private ResultListLabelProvider resultListLabelProvider;
	private TypeSectionLabelProvider typeSectionLabelProvider;

	public NewInstanceCellEditor() {
	}

	public NewInstanceCellEditor(final Composite parent) {
		this(parent, SWT.NONE);
	}

	public NewInstanceCellEditor(final Composite parent, final int style) {
		this(parent, style, false);
	}

	public NewInstanceCellEditor(final Composite parent, final int style, final boolean insideCell) {
		super(parent, style | SWT.SEARCH | SWT.ICON_CANCEL | SWT.ICON_SEARCH);
		this.insideCell = insideCell;
	}

	public Button getMenuButton() {
		return menuButton;
	}

	public void setTypeLibrary(final TypeLibrary typeLib) {
		paletteFilter = new PaletteFilter(typeLib);

		// Initialize MRU tracker for the current project
		if (typeLib != null && mruTracker == null) {
			try {
				final IProject project = typeLib.getProject();
				if (project != null && project.exists()) {
					mruTracker = new MostRecentlyUsedTracker(project);
				}
			} catch (final Exception e) {
				System.err.println("Failed to initialize MRUTracker: " + e.getMessage()); //$NON-NLS-1$
				e.printStackTrace();
			}
		}

		// Initialize Favorites manager (configuration-scoped, shared across workspaces)
		if (favoritesManager == null) {
			try {
				favoritesManager = new FavoritesManager();
			} catch (final Exception e) {
				System.err.println("Failed to initialize FavoritesManager: " + e.getMessage()); //$NON-NLS-1$
				e.printStackTrace();
			}
		}

		// Initialize Frequency tracker (configuration-scoped, automatic usage tracking)
		if (frequencyTracker == null) {
			try {
				frequencyTracker = new FrequencyTracker();
			} catch (final Exception e) {
				System.err.println("Failed to initialize FrequencyTracker: " + e.getMessage()); //$NON-NLS-1$
				e.printStackTrace();
			}
		}

		// Trigger update now that paletteFilter is initialized
		if (textControl != null && !textControl.isDisposed()) {
			updateSelectionList();
		}
	}

	public FavoritesManager getFavoritesManager() {
		return favoritesManager;
	}

	public FrequencyTracker getFrequencyTracker() {
		return frequencyTracker;
	}

	@Override
	protected Control createControl(final Composite parent) {
		container = createContainer(parent);
		textControl = (Text) super.createControl(container);
		configureTextControl();
		createTypeMenuButton(container);
		createPopUpList(container);
		// initial population of the selection list
		updateSelectionList();
		return container;
	}

	public Text getText() {
		return text;
	}

	@Override
	public void focusLost() {
		if (!insideAnyEditorArea()) {
			// when we loose focus we fire cancel, so that the entered text is not applied
			fireCancelEditor();
		}
	}

	// make the fireCancleEditor publicly available for the direct edit manager
	@Override
	public void fireCancelEditor() {
		super.fireCancelEditor();
	}

	@Override
	public void deactivate() {
		if (null != popupShell && !popupShell.isDisposed()) {
			popupShell.setVisible(false);
		}
		super.deactivate();
	}

	@Override
	protected void handleDefaultSelection(final SelectionEvent event) {
		if (!((Text) event.getSource()).getText().isEmpty()) {
			super.handleDefaultSelection(event);
		}
	}

	@Override
	public Object doGetValue() {
		if (null != selectedEntry) {
			// Record usage in MRU tracker
			if (mruTracker != null && !mruTracker.isDisposed()) {
				try {
					mruTracker.recordUsage(selectedEntry.getTypeName());
				} catch (final Exception e) {
					System.err.println("Failed to record MRU usage: " + e.getMessage()); //$NON-NLS-1$
				}
			}

			// Record usage in Frequency tracker
			if (frequencyTracker != null) {
				try {
					frequencyTracker.recordUsage(selectedEntry.getTypeName());
				} catch (final Exception e) {
					System.err.println("Failed to record Frequency usage: " + e.getMessage()); //$NON-NLS-1$
				}
			}

			return selectedEntry;
		}
		return super.doGetValue();
	}

	public boolean insideAnyEditorArea() {
		final Point cursorLocation = popupShell.getDisplay().getCursorLocation();
		final Point containerRelativeCursor = container.getParent().toControl(cursorLocation);
		return container.getBounds().contains(containerRelativeCursor)
				|| popupShell.getBounds().contains(cursorLocation);
	}

	protected Composite createContainer(final Composite parent) {
		final Composite newContainer = new Composite(parent, SWT.NONE) {
			@Override
			public void setBounds(final int x, final int y, final int width, final int height) {
				super.setBounds(x, y, width, height);

				final Point screenPos;
				if (insideCell) {
					screenPos = new Point(x, y);
				} else {
					screenPos = getParent().toDisplay(getLocation());
				}
				final Rectangle compositeBounds = getBounds();
				popupShell.setBounds(screenPos.x, screenPos.y + compositeBounds.height, compositeBounds.width, 300);
				if (!popupShell.isVisible()) {
					popupShell.setVisible(true);
				}
			}
		};
		newContainer.setBackground(parent.getBackground());
		newContainer.setForeground(parent.getForeground());

		// set layout with minimal space to keep the cell editor compact

		newContainer.setLayout(CellEditorLayoutFactory.getNewGridZeroLayout(NUM_COLUMNS));
		return newContainer;
	}

	public void configureTextControl() {
		textControl.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true));
		textControl.setMessage(Messages.NewInstanceCellEditor_SearchForType);
		textControl.addListener(SWT.Modify, event -> updateSelectionList());
		textControl.addListener(SWT.KeyDown, event -> handleKeyPress(event, textControl));
	}

	protected void updateSelectionList() {
		blockTreeSelection = true;
		final String searchText = textControl.getText();

		if (searchText.length() >= 2) {
			// Normal search with 2+ characters - show only All Types section
			final List<TypeEntry> entries = paletteFilter.findFBAndSubappTypes(searchText);
			final List<TypeSection> sections = new ArrayList<>();

			final TypeSection allTypesSection = new TypeSection("All Types", entries); //$NON-NLS-1$
			sections.add(allTypesSection);

			typeSectionLabelProvider.setSearchString(searchText);
			treeViewer.setInput(sections);
			treeViewer.expandAll();

			if (!entries.isEmpty()) {
				selectFirstTypeEntry();
			}
			markDirty();
		} else if (searchText.length() == 0) {
			// Show sectioned view with Recent/Favorites/Frequent
			showSectionedView();
		} else {
			// 1 character typed - hide list (keep current behavior)
			treeViewer.setInput(null);
		}

		blockTreeSelection = false;
	}

	/**
	 * Show the sectioned view with Recent, Favorites, and Frequent sections
	 */
	private void showSectionedView() {
		if (paletteFilter == null) {
			treeViewer.setInput(null);
			return;
		}

		final List<TypeSection> sections = new ArrayList<>();

		// Create Recent section
		final TypeSection recentSection = createRecentSection();
		if (!recentSection.isEmpty()) {
			sections.add(recentSection);
		}

		// Create Favorites section
		final TypeSection favoritesSection = createFavoritesSection();
		if (!favoritesSection.isEmpty()) {
			sections.add(favoritesSection);
		}

		// Create Frequent section
		final TypeSection frequentSection = createFrequentSection();
		if (!frequentSection.isEmpty()) {
			sections.add(frequentSection);
		}

		if (!sections.isEmpty()) {
			typeSectionLabelProvider.setSearchString(""); //$NON-NLS-1$
			treeViewer.setInput(sections);
			treeViewer.expandAll();
			selectFirstTypeEntry();
		} else {
			treeViewer.setInput(null);
		}
	}

	/**
	 * Create the Recent section from MRU tracker
	 */
	private TypeSection createRecentSection() {
		final TypeSection section = new TypeSection("Recent"); //$NON-NLS-1$

		if (mruTracker == null || mruTracker.isDisposed()) {
			return section;
		}

		final List<String> mruTypeNames = mruTracker.getMRUList();
		int count = 0;

		for (final String typeName : mruTypeNames) {
			if (count >= MAX_RECENT_ITEMS) {
				break;
			}
			final TypeEntry entry = paletteFilter.findTypeEntry(typeName);
			if (entry != null) {
				section.addEntry(entry);
				count++;
			}
		}

		return section;
	}

	/**
	 * Create the Favorites section from FavoritesManager
	 */
	private TypeSection createFavoritesSection() {
		final TypeSection section = new TypeSection("Favorites"); //$NON-NLS-1$

		if (favoritesManager == null) {
			return section;
		}

		// FIX #2: Convert Set<String> to List<String>
		final List<String> favoriteTypeNames = new ArrayList<>(favoritesManager.getFavorites());

		for (final String typeName : favoriteTypeNames) {
			final TypeEntry entry = paletteFilter.findTypeEntry(typeName);
			if (entry != null) {
				section.addEntry(entry);
			}
		}

		return section;
	}

	/**
	 * Create the Frequent section from FrequencyTracker
	 */
	private TypeSection createFrequentSection() {
		final TypeSection section = new TypeSection("Frequent"); //$NON-NLS-1$

		if (frequencyTracker == null) {
			return section;
		}

		final List<String> frequentTypeNames = frequencyTracker.getMostFrequent(MAX_FREQUENT_ITEMS);

		for (final String typeName : frequentTypeNames) {
			final TypeEntry entry = paletteFilter.findTypeEntry(typeName);
			if (entry != null) {
				section.addEntry(entry);
			}
		}

		return section;
	}

	/**
	 * Select the first TypeEntry in the tree (skipping section headers) FIX #3: Use
	 * treeViewer.getTree().getItem(0).getData() instead of getElementAt()
	 */
	private void selectFirstTypeEntry() {
		blockTreeSelection = true;

		// Get the first section from the tree
		if (treeViewer.getTree().getItemCount() > 0) {
			final Object firstSection = treeViewer.getTree().getItem(0).getData();
			if ((firstSection instanceof final TypeSection section) && !section.isEmpty()) {
				final TypeEntry firstEntry = section.getEntries().get(0);
				treeViewer.setSelection(new StructuredSelection(firstEntry), true);
			}
		}

		blockTreeSelection = false;
	}

	private void handleKeyPress(final Event event, final Text textControl) {
		switch (event.keyCode) {
		case SWT.ARROW_DOWN:
			navigateTree(true);
			event.doit = false;
			break;
		case SWT.ARROW_UP:
			navigateTree(false);
			event.doit = false;
			break;
		case SWT.CR:
			if (popupShell.isVisible()) {
				final IStructuredSelection selection = treeViewer.getStructuredSelection();
				if (!selection.isEmpty() && selection.getFirstElement() instanceof TypeEntry) {
					selectedEntry = (TypeEntry) selection.getFirstElement();
					textControl.setText(selectedEntry.getTypeName());
				}
			} else {
				event.doit = false;
			}
			break;
		default:
			break;
		}
	}

	/**
	 * Navigate through tree items, skipping section headers
	 */
	private void navigateTree(final boolean down) {
		if (treeViewer.getTree().getItemCount() == 0) {
			return;
		}

		// Get all visible tree items in order
		final List<org.eclipse.swt.widgets.TreeItem> visibleItems = new ArrayList<>();
		collectVisibleItems(treeViewer.getTree(), visibleItems);

		// Filter to only TypeEntry items (skip section headers)
		final List<org.eclipse.swt.widgets.TreeItem> entryItems = new ArrayList<>();
		for (final org.eclipse.swt.widgets.TreeItem item : visibleItems) {
			if (item.getData() instanceof TypeEntry) {
				entryItems.add(item);
			}
		}

		if (entryItems.isEmpty()) {
			return;
		}

		blockTreeSelection = true;

		// Find current selection
		final org.eclipse.swt.widgets.TreeItem[] selection = treeViewer.getTree().getSelection();
		int currentIndex = -1;

		if (selection.length > 0) {
			final org.eclipse.swt.widgets.TreeItem currentItem = selection[0];

			// Find by identity
			for (int i = 0; i < entryItems.size(); i++) {
				if (entryItems.get(i) == currentItem) {
					currentIndex = i;
					break;
				}
			}
		}

		// Calculate next index
		int nextIndex;
		if (currentIndex == -1) {
			nextIndex = 0;
		} else if (down) {
			nextIndex = (currentIndex + 1) % entryItems.size();
		} else {
			nextIndex = currentIndex - 1;
			if (nextIndex < 0) {
				nextIndex = entryItems.size() - 1;
			}
		}

		// CRITICAL: Select directly on tree widget, not through viewer
		final org.eclipse.swt.widgets.TreeItem nextItem = entryItems.get(nextIndex);
		treeViewer.getTree().setSelection(nextItem);
		treeViewer.getTree().showItem(nextItem);

		// Update the internal selection field
		if (nextItem.getData() instanceof TypeEntry) {
			selectedEntry = (TypeEntry) nextItem.getData();
		}

		blockTreeSelection = false;
	}

	/**
	 * Recursively collect all visible tree items
	 */
	private void collectVisibleItems(final org.eclipse.swt.widgets.Tree tree,
			final List<org.eclipse.swt.widgets.TreeItem> result) {
		collectVisibleItemsRecursive(tree.getItems(), result);
	}

	private void collectVisibleItemsRecursive(final org.eclipse.swt.widgets.TreeItem[] items,
			final List<org.eclipse.swt.widgets.TreeItem> result) {
		for (final org.eclipse.swt.widgets.TreeItem item : items) {
			result.add(item);
			if (item.getExpanded() && item.getItemCount() > 0) {
				collectVisibleItemsRecursive(item.getItems(), result);
			}
		}
	}

	private void createPopUpList(final Composite container) {
		popupShell = new Shell(container.getShell(), SWT.ON_TOP | SWT.NO_FOCUS | SWT.NO_TRIM);
		popupShell.setLayout(new FillLayout());

		treeViewer = new TreeViewer(popupShell, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		ColumnViewerToolTipSupport.enableFor(treeViewer);

		treeViewer.setContentProvider(new TypeSectionContentProvider());

		resultListLabelProvider = new ResultListLabelProvider();
		typeSectionLabelProvider = new TypeSectionLabelProvider(resultListLabelProvider);
		final DelegatingStyledCellLabelProvider delegatingStyledCellLabelProvider = new DelegatingStyledCellLabelProvider(
				typeSectionLabelProvider);
		treeViewer.setLabelProvider(delegatingStyledCellLabelProvider);

		final TableLayout layout = new TableLayout();
		layout.addColumnData(new ColumnWeightData(100));
		treeViewer.getTree().setLayout(layout);

		treeViewer.getControl().addListener(SWT.KeyDown, event -> {
			if (event.keyCode == SWT.ESC) {
				fireCancelEditor();
			}
		});

		treeViewer.addSelectionChangedListener(event -> {
			if (!blockTreeSelection) {
				final IStructuredSelection selection = treeViewer.getStructuredSelection();
				if (!selection.isEmpty() && selection.getFirstElement() instanceof TypeEntry) {
					selectedEntry = (TypeEntry) selection.getFirstElement();
					fireApplyEditorValue();
				}
			}
		});
	}

	private void createTypeMenuButton(final Composite container) {
		menuButton = new Button(container, SWT.FLAT);
		menuButton.setImage(FordiacImage.ICON_TYPE_NAVIGATOR.getImage());
	}
}