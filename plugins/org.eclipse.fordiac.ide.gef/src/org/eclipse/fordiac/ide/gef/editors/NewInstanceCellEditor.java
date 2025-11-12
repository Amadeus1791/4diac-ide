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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import org.eclipse.fordiac.ide.model.helpers.PackageNameHelper;
import org.eclipse.fordiac.ide.model.typelibrary.FBTypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.PaletteFilter;
import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.fordiac.ide.ui.imageprovider.FordiacImage;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
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
import org.eclipse.swt.widgets.Menu;
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
	 * Show the sectioned view with Recent, Favorites, Frequent, and All Categories
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

		// Create All Categories section
		final TypeSection allCategoriesSection = createAllCategoriesSection();
		sections.add(allCategoriesSection); // Always add, even if empty

		if (!sections.isEmpty()) {
			typeSectionLabelProvider.setSearchString(""); //$NON-NLS-1$
			treeViewer.setInput(sections);

			// Expand Recent, Favorites, Frequent but NOT All Categories
			treeViewer.setExpandedElements(recentSection, favoritesSection, frequentSection);

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
					textControl.setText(selectedEntry.getTypeName()); // ← Add if missing
					fireApplyEditorValue(); // ← This should be here
				}
			} else {
				event.doit = false;
			}
			break;
		case SWT.ARROW_RIGHT:
			// Expand selected section if it's collapsed
			final org.eclipse.swt.widgets.TreeItem[] selectionRight = treeViewer.getTree().getSelection();
			if (selectionRight.length > 0) {
				final org.eclipse.swt.widgets.TreeItem item = selectionRight[0];
				if (!item.getExpanded() && item.getItemCount() > 0) {
					treeViewer.setExpandedState(item.getData(), true);
					event.doit = false;
				}
			}
			break;
		case SWT.ARROW_LEFT:
			// Collapse selected section if it's expanded
			final org.eclipse.swt.widgets.TreeItem[] selectionLeft = treeViewer.getTree().getSelection();
			if (selectionLeft.length > 0) {
				final org.eclipse.swt.widgets.TreeItem item = selectionLeft[0];
				if (item.getExpanded()) {
					treeViewer.setExpandedState(item.getData(), false);
					event.doit = false;
				}
			}
			break;
		default:
			break;
		}
	}

	/**
	 * Navigate through tree items, including section headers
	 */
	private void navigateTree(final boolean down) {
		if (treeViewer.getTree().getItemCount() == 0) {
			return;
		}

		// Get all visible tree items in order (including section headers)
		final List<org.eclipse.swt.widgets.TreeItem> visibleItems = new ArrayList<>();
		collectVisibleItems(treeViewer.getTree(), visibleItems);

		if (visibleItems.isEmpty()) {
			return;
		}

		blockTreeSelection = true;

		// Find current selection
		final org.eclipse.swt.widgets.TreeItem[] selection = treeViewer.getTree().getSelection();
		int currentIndex = -1;

		if (selection.length > 0) {
			final org.eclipse.swt.widgets.TreeItem currentItem = selection[0];

			// Find by identity
			for (int i = 0; i < visibleItems.size(); i++) {
				if (visibleItems.get(i) == currentItem) {
					currentIndex = i;
					break;
				}
			}
		}

		// Calculate next index (no wrapping - stop at boundaries)
		int nextIndex;
		if (currentIndex == -1) {
			nextIndex = 0;
		} else if (down) {
			nextIndex = currentIndex + 1;
			if (nextIndex >= visibleItems.size()) {
				nextIndex = visibleItems.size() - 1; // Stay at last item
			}
		} else {
			nextIndex = currentIndex - 1;
			if (nextIndex < 0) {
				nextIndex = 0; // Stay at first item
			}
		}

		// Select the next item DIRECTLY on the tree widget
		final org.eclipse.swt.widgets.TreeItem nextItem = visibleItems.get(nextIndex);
		treeViewer.getTree().setSelection(nextItem);
		treeViewer.getTree().showItem(nextItem);

		// Update the selectedEntry field only if it's a TypeEntry
		if (nextItem.getData() instanceof TypeEntry) {
			selectedEntry = (TypeEntry) nextItem.getData();
		}
		// else: Selected a section header - don't update selectedEntry

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

	/**
	 * Add context menu to tree viewer for favorites management
	 */
	private void addContextMenu(final TreeViewer viewer) {
		final MenuManager menuManager = new MenuManager();

		// Action: Add to Favorites
		final Action addToFavoritesAction = new Action("Add to Favorites") {
			@Override
			public void run() {
				final IStructuredSelection selection = viewer.getStructuredSelection();
				final Object selected = selection.getFirstElement();

				if (selected instanceof final TypeEntry entry) {
					favoritesManager.addFavorite(entry.getTypeName());
					refreshPopup();
				}
			}
		};

		// Action: Remove from Favorites
		final Action removeFromFavoritesAction = new Action("Remove from Favorites") {
			@Override
			public void run() {
				final IStructuredSelection selection = viewer.getStructuredSelection();
				final Object selected = selection.getFirstElement();

				if (selected instanceof final TypeEntry entry) {
					favoritesManager.removeFavorite(entry.getTypeName());
					refreshPopup();
				}
			}
		};

		menuManager.add(addToFavoritesAction);
		menuManager.add(removeFromFavoritesAction);

		// UPDATE ENABLED STATE DYNAMICALLY before menu shows
		menuManager.addMenuListener(manager -> {
			final Object selected = viewer.getStructuredSelection().getFirstElement();

			if (selected instanceof final TypeEntry entry) {
				final String typeName = entry.getTypeName();
				final boolean isFav = favoritesManager.isFavorite(typeName);

				addToFavoritesAction.setEnabled(!isFav); // Enable if NOT favorite
				removeFromFavoritesAction.setEnabled(isFav); // Enable if IS favorite
			} else {
				// Section header or nothing selected
				addToFavoritesAction.setEnabled(false);
				removeFromFavoritesAction.setEnabled(false);
			}
		});

		final Menu menu = menuManager.createContextMenu(viewer.getTree());
		viewer.getTree().setMenu(menu);
	}

	/**
	 * Refresh the popup to show updated sections after favorites change
	 */
	private void refreshPopup() {
		if (textControl == null || textControl.isDisposed()) {
			return;
		}

		// Get current search text
		final String searchText = textControl.getText();

		// Update the selection list (this rebuilds all sections)
		updateSelectionList();

		// If search was empty, we're in sectioned view - restore selection if possible
		if (searchText.isEmpty() && selectedEntry != null) {
			// Try to reselect the previously selected entry
			blockTreeSelection = true;
			treeViewer.setSelection(new StructuredSelection(selectedEntry), true);
			blockTreeSelection = false;
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
			} else if (event.keyCode == SWT.CR || event.keyCode == SWT.KEYPAD_CR) {
				// Enter key pressed in tree - create element
				final IStructuredSelection selection = treeViewer.getStructuredSelection();
				if (!selection.isEmpty() && selection.getFirstElement() instanceof TypeEntry) {
					selectedEntry = (TypeEntry) selection.getFirstElement();
					textControl.setText(selectedEntry.getTypeName());
					fireApplyEditorValue();
				}
				event.doit = false;
			}
		});

		// Selection listener - only tracks selection without creating elements
		treeViewer.addSelectionChangedListener(event -> {
			// Ignore selections during programmatic tree manipulation
			if (blockTreeSelection) {
				return;
			}

			final Object selected = event.getStructuredSelection().getFirstElement();

			// Only accept TypeEntry selections, ignore section headers
			if (selected instanceof TypeEntry) {
				selectedEntry = (TypeEntry) selected;
				// DO NOT create element here - let click handlers do that
			}
		});

		// Handle BOTH single-click AND double-click the same way
		// This mimics the Enter key behavior (see line 437-444)
		final Runnable createElementFromSelection = () -> {
			final IStructuredSelection selection = treeViewer.getStructuredSelection();
			if (!selection.isEmpty() && selection.getFirstElement() instanceof TypeEntry) {
				selectedEntry = (TypeEntry) selection.getFirstElement();
				textControl.setText(selectedEntry.getTypeName());
				fireApplyEditorValue();
			}
		};

		// Single-click: Create element (same behavior as Enter key)
		treeViewer.getTree().addListener(SWT.MouseDown, event -> {
			// Only handle left mouse button (button 1), ignore right-click (button 3)
			if (event.button == 1) {
				// Get item at click position to validate it's not empty space
				final org.eclipse.swt.widgets.TreeItem item = treeViewer.getTree().getItem(new Point(event.x, event.y));

				// Only proceed if clicking on an actual TypeEntry item
				if (item != null && item.getData() instanceof TypeEntry) {
					createElementFromSelection.run();
				}
			}
		});

		// Double-click: Same behavior (for users who prefer double-click)
		treeViewer.getTree().addListener(SWT.MouseDoubleClick, event -> {
			// The first click already created the element via MouseDown
			// This is redundant but some users may expect double-click behavior
			// We check selectedEntry to avoid creating twice on double-click
			if (selectedEntry != null) {
				// Already created by first click, but doesn't hurt to call again
				fireApplyEditorValue();
			}
		});
		// ═══════════════════════════════════════════════════════════════
		// NEW: Add context menu for favorites management
		// ═══════════════════════════════════════════════════════════════
		addContextMenu(treeViewer);
	}

	private void createTypeMenuButton(final Composite container) {
		menuButton = new Button(container, SWT.FLAT);
		menuButton.setImage(FordiacImage.ICON_TYPE_NAVIGATOR.getImage());
	}

	/**
	 * Creates the "All Categories" section matching the palette structure. Shows
	 * only the leaf-level package folders (e.g., "convert", "core", "events")
	 * without the parent hierarchy (e.g., no "eclipse4diac" parent).
	 */
	private TypeSection createAllCategoriesSection() {
		final TypeSection allCategories = new TypeSection("All Categories");

		if (paletteFilter == null) {
			return allCategories;
		}

		final TypeLibrary typeLib = paletteFilter.getTypeLibrary();
		if (typeLib == null) {
			return allCategories;
		}

		// Group types by their LEAF package (last part of the package path)
		// e.g., "eclipse4diac::convert" -> "convert"
		// "eclipse4diac::io::ads" -> "ads"
		// "iec61131::events" -> "events"
		final Map<String, List<TypeEntry>> leafPackageMap = new TreeMap<>();

		for (final FBTypeEntry entry : typeLib.getFbTypes()) {
			final String fullPackageName = PackageNameHelper.extractPackageName(entry.getFullTypeName());

			if (fullPackageName == null || fullPackageName.isEmpty()) {
				// No package - add to a special "root" section
				leafPackageMap.computeIfAbsent("(no package)", k -> new ArrayList<>()).add(entry);
				continue;
			}

			// Get the LAST part of the package (the leaf folder)
			// "eclipse4diac::convert" -> "convert"
			// "eclipse4diac::io::ads" -> "ads"
			final String[] parts = fullPackageName.split(PackageNameHelper.PACKAGE_NAME_DELIMITER);
			final String leafPackage = parts[parts.length - 1];

			leafPackageMap.computeIfAbsent(leafPackage, k -> new ArrayList<>()).add(entry);
		}

		// Create a section for each leaf package (already sorted alphabetically by
		// TreeMap)
		for (final Map.Entry<String, List<TypeEntry>> entry : leafPackageMap.entrySet()) {
			final String packageName = entry.getKey();
			final List<TypeEntry> packageTypes = entry.getValue();

			// Sort types within each package
			packageTypes.sort(Comparator.comparing(TypeEntry::getTypeName));

			// Create section with types
			final TypeSection packageSection = new TypeSection(packageName, packageTypes);
			allCategories.addChildSection(packageSection);
		}

		return allCategories;
	}

}