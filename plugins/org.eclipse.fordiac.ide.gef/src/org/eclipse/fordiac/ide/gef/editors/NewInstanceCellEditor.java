/*******************************************************************************
 * Copyright (c) 2019 Johannes Kepler University Linz
 * 				 2022 Primetals Technologies Germany GmbH
 * 				 2025 Chain Mode Integration
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
 *   [Your Name] - chain mode integration
 *******************************************************************************/
package org.eclipse.fordiac.ide.gef.editors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.fordiac.ide.gef.Messages;
import org.eclipse.fordiac.ide.gef.tools.ChainModeManager;
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
import org.eclipse.jface.resource.ImageDescriptor;
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
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

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

	// ════════════════════════════════════════════════════════════════
	// CHAIN MODE: Add these fields
	// ════════════════════════════════════════════════════════════════
	private Label chainModeIndicator;
	private Composite chainModeBar;

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
		System.out.println("[NewInstanceCellEditor] Created with insideCell=" + insideCell);
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
					System.out.println("[NewInstanceCellEditor] MRU tracker initialized");
				}
			} catch (final Exception e) {
				System.err.println("Failed to initialize MRUTracker: " + e.getMessage());
				e.printStackTrace();
			}
		}

		// Initialize Favorites manager (configuration-scoped, shared across workspaces)
		if (favoritesManager == null) {
			try {
				favoritesManager = new FavoritesManager();
			} catch (final Exception e) {
				System.err.println("Failed to initialize FavoritesManager: " + e.getMessage());
				e.printStackTrace();
			}
		}

		// Initialize Frequency tracker (configuration-scoped, automatic usage tracking)
		if (frequencyTracker == null) {
			try {
				frequencyTracker = new FrequencyTracker();
			} catch (final Exception e) {
				System.err.println("Failed to initialize FrequencyTracker: " + e.getMessage());
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
		System.out.println("[NewInstanceCellEditor] createControl called");
		container = createContainer(parent);

		// ════════════════════════════════════════════════════════════════
		// CHAIN MODE: Create chain mode indicator bar
		// ════════════════════════════════════════════════════════════════
		createChainModeBar(container);

		textControl = (Text) super.createControl(container);
		configureTextControl();
		createTypeMenuButton(container);
		createPopUpList(container);

		// initial population of the selection list
		updateSelectionList();

		// ════════════════════════════════════════════════════════════════
		// CHAIN MODE: Update UI based on chain mode state
		// ════════════════════════════════════════════════════════════════
		updateChainModeUI();

		return container;
	}

	// ════════════════════════════════════════════════════════════════
	// CHAIN MODE: Add these new methods
	// ════════════════════════════════════════════════════════════════

	/**
	 * Create the chain mode indicator bar at the top of the editor.
	 */
	private void createChainModeBar(final Composite parent) {
		System.out.println("[NewInstanceCellEditor] Creating chain mode bar");

		chainModeBar = new Composite(parent, SWT.NONE);
		chainModeBar.setLayout(new GridLayout(1, false));
		chainModeBar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		chainModeBar.setVisible(false); // Hidden by default

		chainModeIndicator = new Label(chainModeBar, SWT.NONE);
		chainModeIndicator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		chainModeIndicator.setText("🔗 Chain Mode: ");

		// Style the indicator
		chainModeIndicator.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
	}

	/**
	 * Update the chain mode UI based on current state.
	 */
	private void updateChainModeUI() {
		if (chainModeBar == null || chainModeBar.isDisposed()) {
			return;
		}

		final ChainModeManager chainManager = ChainModeManager.getInstance();
		final boolean isChainMode = chainManager.isChainModeActive();

		System.out.println("[NewInstanceCellEditor] Updating chain mode UI - Active: " + isChainMode);

		if (isChainMode) {
			// Show chain mode indicator with breadcrumb
			final String breadcrumb = chainManager.getChainBreadcrumb();
			chainModeIndicator.setText("🔗 Chain Mode: " + breadcrumb);
			chainModeBar.setVisible(true);

			// Update search placeholder
			if (textControl != null && !textControl.isDisposed()) {
				textControl.setMessage("Search next element in chain...");
			}

			System.out.println("[NewInstanceCellEditor] Chain mode UI shown: " + breadcrumb);
		} else {
			// Hide chain mode indicator
			chainModeBar.setVisible(false);

			// Reset search placeholder
			if (textControl != null && !textControl.isDisposed()) {
				textControl.setMessage(Messages.NewInstanceCellEditor_SearchForType);
			}

			System.out.println("[NewInstanceCellEditor] Chain mode UI hidden");
		}

		// Force layout update
		if (container != null && !container.isDisposed()) {
			container.layout(true, true);
		}
	}

	public Text getText() {
		return text;
	}

	@Override
	public void focusLost() {
		System.out.println("[NewInstanceCellEditor] focusLost called");
		if (!insideAnyEditorArea()) {
			System.out.println("[NewInstanceCellEditor] Focus lost outside editor - cancelling");
			// when we loose focus we fire cancel, so that the entered text is not applied
			fireCancelEditor();
		}
	}

	// make the fireCancleEditor publicly available for the direct edit manager
	@Override
	public void fireCancelEditor() {
		System.out.println("[NewInstanceCellEditor] fireCancelEditor called");
		super.fireCancelEditor();
	}

	@Override
	public void deactivate() {
		System.out.println("[NewInstanceCellEditor] deactivate called");
		if (null != popupShell && !popupShell.isDisposed()) {
			popupShell.setVisible(false);
		}
		super.deactivate();
	}

	@Override
	protected void handleDefaultSelection(final SelectionEvent event) {
		System.out.println("[NewInstanceCellEditor] handleDefaultSelection called");
		if (!((Text) event.getSource()).getText().isEmpty()) {
			super.handleDefaultSelection(event);
		}
	}

	@Override
	public Object doGetValue() {
		System.out.println("[NewInstanceCellEditor] doGetValue called");
		if (null != selectedEntry) {
			System.out.println("[NewInstanceCellEditor] Returning selected entry: " + selectedEntry.getTypeName());

			// Record usage in MRU tracker
			if (mruTracker != null && !mruTracker.isDisposed()) {
				try {
					mruTracker.recordUsage(selectedEntry.getTypeName());
					System.out.println("[NewInstanceCellEditor] Recorded MRU usage");
				} catch (final Exception e) {
					System.err.println("Failed to record MRU usage: " + e.getMessage());
				}
			}

			// Record usage in Frequency tracker
			if (frequencyTracker != null) {
				try {
					frequencyTracker.recordUsage(selectedEntry.getTypeName());
				} catch (final Exception e) {
					System.err.println("Failed to record Frequency usage: " + e.getMessage());
				}
			}

			return selectedEntry;
		}
		System.out.println("[NewInstanceCellEditor] No selected entry, using default");
		return super.doGetValue();
	}

	public boolean insideAnyEditorArea() {
		final Point cursorLocation = popupShell.getDisplay().getCursorLocation();
		final Point containerRelativeCursor = container.getParent().toControl(cursorLocation);
		return container.getBounds().contains(containerRelativeCursor)
				|| popupShell.getBounds().contains(cursorLocation);
	}

	protected Composite createContainer(final Composite parent) {
		System.out.println("[NewInstanceCellEditor] createContainer called");
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
				popupShell.setBounds(screenPos.x, screenPos.y + compositeBounds.height, compositeBounds.width, 600);
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
		System.out.println("[NewInstanceCellEditor] configureTextControl called");
		textControl.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true));
		textControl.setMessage(Messages.NewInstanceCellEditor_SearchForType);
		textControl.addListener(SWT.Modify, event -> updateSelectionList());
		textControl.addListener(SWT.KeyDown, event -> handleKeyPress(event, textControl));
	}

	protected void updateSelectionList() {
		blockTreeSelection = true;
		final String searchText = textControl.getText();
		System.out.println("[NewInstanceCellEditor] updateSelectionList - Search text: '" + searchText + "'");

		if (searchText.length() >= 2) {
			// Normal search with 2+ characters - show only All Types section
			final List<TypeEntry> entries = paletteFilter.findFBAndSubappTypes(searchText);
			System.out.println("[NewInstanceCellEditor] Found " + entries.size() + " matching entries");
			final List<TypeSection> sections = new ArrayList<>();

			final TypeSection allTypesSection = new TypeSection("All Types", entries);
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
			System.out.println("[NewInstanceCellEditor] Empty search - showing sectioned view");
			showSectionedView();
		} else {
			// 1 character typed - hide list (keep current behavior)
			System.out.println("[NewInstanceCellEditor] 1 character - hiding list");
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
			typeSectionLabelProvider.setSearchString("");
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
		final TypeSection section = new TypeSection("Recent");

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
		final TypeSection section = new TypeSection("Favorites");

		if (favoritesManager == null) {
			return section;
		}

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
		final TypeSection section = new TypeSection("Frequent");

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
	 * Select the first TypeEntry in the tree (skipping section headers)
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
		System.out.println("[NewInstanceCellEditor] Key pressed: " + event.keyCode);

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
					System.out.println(
							"[NewInstanceCellEditor] Enter pressed - selected: " + selectedEntry.getTypeName());
					fireApplyEditorValue();
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
		// ════════════════════════════════════════════════════════════════
		// CHAIN MODE: Handle ESC key to exit chain mode
		// ════════════════════════════════════════════════════════════════
		case SWT.ESC:
			System.out.println("[NewInstanceCellEditor] ESC pressed");
			final ChainModeManager chainManager = ChainModeManager.getInstance();
			if (chainManager.isChainModeActive()) {
				System.out.println("[NewInstanceCellEditor] Exiting chain mode via ESC");
				chainManager.exitChainMode();
				updateChainModeUI();
			}
			fireCancelEditor();
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
		final ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();

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
		addToFavoritesAction.setImageDescriptor(
				ImageDescriptor.createFromImage(sharedImages.getImage(ISharedImages.IMG_OBJS_BKMRK_TSK)));

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
		removeFromFavoritesAction.setImageDescriptor(
				ImageDescriptor.createFromImage(sharedImages.getImage(ISharedImages.IMG_ELCL_REMOVE)));

		menuManager.add(addToFavoritesAction);
		menuManager.add(removeFromFavoritesAction);

		menuManager.addMenuListener(manager -> {
			final Object selected = viewer.getStructuredSelection().getFirstElement();

			if (selected instanceof final TypeEntry entry) {
				final String typeName = entry.getTypeName();
				final boolean isFav = favoritesManager.isFavorite(typeName);

				addToFavoritesAction.setEnabled(!isFav);
				removeFromFavoritesAction.setEnabled(isFav);
			} else {
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

		final String searchText = textControl.getText();
		updateSelectionList();

		if (searchText.isEmpty() && selectedEntry != null) {
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
				final IStructuredSelection selection = treeViewer.getStructuredSelection();
				if (!selection.isEmpty() && selection.getFirstElement() instanceof TypeEntry) {
					selectedEntry = (TypeEntry) selection.getFirstElement();
					textControl.setText(selectedEntry.getTypeName());
					fireApplyEditorValue();
				}
				event.doit = false;
			}
		});

		treeViewer.addSelectionChangedListener(event -> {
			if (blockTreeSelection) {
				return;
			}

			final Object selected = event.getStructuredSelection().getFirstElement();

			if (selected instanceof TypeEntry) {
				selectedEntry = (TypeEntry) selected;
			}
		});

		final Runnable createElementFromSelection = () -> {
			final IStructuredSelection selection = treeViewer.getStructuredSelection();
			if (!selection.isEmpty() && selection.getFirstElement() instanceof TypeEntry) {
				selectedEntry = (TypeEntry) selection.getFirstElement();
				textControl.setText(selectedEntry.getTypeName());
				fireApplyEditorValue();
			}
		};

		treeViewer.getTree().addListener(SWT.MouseDown, event -> {
			if (event.button == 1) {
				final org.eclipse.swt.widgets.TreeItem item = treeViewer.getTree().getItem(new Point(event.x, event.y));

				if (item != null && item.getData() instanceof TypeEntry) {
					createElementFromSelection.run();
				}
			}
		});

		treeViewer.getTree().addListener(SWT.MouseDoubleClick, event -> {
			if (selectedEntry != null) {
				fireApplyEditorValue();
			}
		});

		addContextMenu(treeViewer);
	}

	private void createTypeMenuButton(final Composite container) {
		menuButton = new Button(container, SWT.FLAT);
		menuButton.setImage(FordiacImage.ICON_TYPE_NAVIGATOR.getImage());
	}

	/**
	 * Creates the "All Categories" section matching the palette structure.
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

		final TypeSection standardLibraries = new TypeSection("Standard Libraries");
		final Map<String, Map<String, List<TypeEntry>>> standardLibsCategories = new TreeMap<>();
		final Map<String, Map<String, List<TypeEntry>>> otherCategories = new TreeMap<>();

		for (final FBTypeEntry entry : typeLib.getFbTypes()) {
			final String fullPackageName = PackageNameHelper.extractPackageName(entry.getFullTypeName());

			if (fullPackageName == null || fullPackageName.isEmpty()) {
				otherCategories.computeIfAbsent("(no package)", k -> new TreeMap<>())
						.computeIfAbsent("", k -> new ArrayList<>()).add(entry);
				continue;
			}

			final String[] parts = fullPackageName.split(PackageNameHelper.PACKAGE_NAME_DELIMITER);

			if (parts[0].equals("eclipse4diac") || parts[0].equals("iec61131") || parts[0].equals("iec61499")
					|| parts[0].equals("powerlink")) {
				String mainCategory;
				String subCategory;

				if (parts[0].equals("eclipse4diac")) {
					if (parts.length < 2) {
						continue;
					}
					mainCategory = parts[1];
					subCategory = parts.length > 2
							? String.join("/", java.util.Arrays.copyOfRange(parts, 2, parts.length))
							: "";
				} else if (parts[0].equals("iec61131")) {
					mainCategory = "iec61131-3";
					subCategory = parts.length > 1
							? String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length))
							: "";
				} else if (parts[0].equals("powerlink")) {
					mainCategory = "powerlink";
					subCategory = parts.length > 1
							? String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length))
							: "";
				} else if (parts.length >= 2) {
					mainCategory = parts[1];
					subCategory = parts.length > 2
							? String.join("/", java.util.Arrays.copyOfRange(parts, 2, parts.length))
							: "";
				} else {
					continue;
				}

				standardLibsCategories.computeIfAbsent(mainCategory, k -> new TreeMap<>())
						.computeIfAbsent(subCategory, k -> new ArrayList<>()).add(entry);
			} else {
				final String mainCategory = parts[0];
				final String subCategory = parts.length > 1
						? String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length))
						: "";

				otherCategories.computeIfAbsent(mainCategory, k -> new TreeMap<>())
						.computeIfAbsent(subCategory, k -> new ArrayList<>()).add(entry);
			}
		}

		for (final Map.Entry<String, Map<String, List<TypeEntry>>> categoryEntry : standardLibsCategories.entrySet()) {
			final String categoryName = categoryEntry.getKey();
			final Map<String, List<TypeEntry>> subCategories = categoryEntry.getValue();

			final TypeSection categorySection = new TypeSection(categoryName);

			if (subCategories.size() == 1 && subCategories.containsKey("")) {
				final List<TypeEntry> types = subCategories.get("");
				types.sort(Comparator.comparing(TypeEntry::getTypeName));
				for (final TypeEntry type : types) {
					categorySection.addEntry(type);
				}
			} else {
				for (final Map.Entry<String, List<TypeEntry>> subEntry : subCategories.entrySet()) {
					final String subCategoryName = subEntry.getKey();
					final List<TypeEntry> types = subEntry.getValue();
					types.sort(Comparator.comparing(TypeEntry::getTypeName));

					if (subCategoryName.isEmpty()) {
						for (final TypeEntry type : types) {
							categorySection.addEntry(type);
						}
					} else {
						final TypeSection subCategorySection = new TypeSection(subCategoryName, types);
						categorySection.addChildSection(subCategorySection);
					}
				}
			}

			standardLibraries.addChildSection(categorySection);
		}

		if (standardLibraries.hasChildren()) {
			allCategories.addChildSection(standardLibraries);
		}

		for (final Map.Entry<String, Map<String, List<TypeEntry>>> categoryEntry : otherCategories.entrySet()) {
			final String categoryName = categoryEntry.getKey();
			final Map<String, List<TypeEntry>> subCategories = categoryEntry.getValue();

			final TypeSection categorySection = new TypeSection(categoryName);

			if (subCategories.size() == 1 && subCategories.containsKey("")) {
				final List<TypeEntry> types = subCategories.get("");
				types.sort(Comparator.comparing(TypeEntry::getTypeName));
				for (final TypeEntry type : types) {
					categorySection.addEntry(type);
				}
			} else {
				for (final Map.Entry<String, List<TypeEntry>> subEntry : subCategories.entrySet()) {
					final String subCategoryName = subEntry.getKey();
					final List<TypeEntry> types = subEntry.getValue();
					types.sort(Comparator.comparing(TypeEntry::getTypeName));

					if (subCategoryName.isEmpty()) {
						for (final TypeEntry type : types) {
							categorySection.addEntry(type);
						}
					} else {
						final TypeSection subCategorySection = new TypeSection(subCategoryName, types);
						categorySection.addChildSection(subCategorySection);
					}
				}
			}

			allCategories.addChildSection(categorySection);
		}

		return allCategories;
	}

}