///*******************************************************************************
// * Copyright (c) 2025 Primetals Technologies Austria GmbH
// *
// * This program and the accompanying materials are made available under the
// * terms of the Eclipse Public License 2.0 which is available at
// * http://www.eclipse.org/legal/epl-2.0.
// *
// * SPDX-License-Identifier: EPL-2.0
// *
// * Contributors:
// *   Wolfgang Schedl - initial API and implementation (DEBUG HANDLER)
// *******************************************************************************/
//package org.eclipse.fordiac.ide.systemmanagement.ui.handlers;
//
//import org.eclipse.core.commands.AbstractHandler;
//import org.eclipse.core.commands.ExecutionEvent;
//import org.eclipse.core.commands.ExecutionException;
//import org.eclipse.core.resources.IFile;
//import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
//import org.eclipse.jface.viewers.ISelection;
//import org.eclipse.jface.viewers.IStructuredSelection;
//import org.eclipse.ui.handlers.HandlerUtil;
//
///**
// * DEBUG HANDLER - Use this to find out what type of object is selected when you
// * right-click in System Explorer
// */
//public class DebugSelectionHandler extends AbstractHandler {
//
//	@Override
//	public Object execute(final ExecutionEvent event) throws ExecutionException {
//		final ISelection selection = HandlerUtil.getCurrentSelection(event);
//
//		System.out.println("==========================================");
//		System.out.println("=== DEBUG SELECTION HANDLER ===");
//		System.out.println("Selection: " + selection);
//		System.out.println("Selection class: " + (selection != null ? selection.getClass().getName() : "null"));
//
//		if (selection instanceof final IStructuredSelection structuredSelection) {
//			final Object firstElement = structuredSelection.getFirstElement();
//
//			System.out.println("First element: " + firstElement);
//			System.out.println(
//					"First element class: " + (firstElement != null ? firstElement.getClass().getName() : "null"));
//
//			if (firstElement != null) {
//				System.out.println("\n--- Class Hierarchy ---");
//				Class<?> clazz = firstElement.getClass();
//				while (clazz != null) {
//					System.out.println("  " + clazz.getName());
//					clazz = clazz.getSuperclass();
//				}
//
//				System.out.println("\n--- Interfaces ---");
//				for (final Class<?> iface : firstElement.getClass().getInterfaces()) {
//					System.out.println("  " + iface.getName());
//				}
//
//				System.out.println("\n--- Type Checks ---");
//				System.out.println("Is TypeEntry? " + (firstElement instanceof TypeEntry));
//				System.out.println("Is IFile? " + (firstElement instanceof IFile));
//
//				if (firstElement instanceof final TypeEntry typeEntry) {
//					System.out.println("\n--- TypeEntry Details ---");
//					System.out.println("Type name: " + typeEntry.getTypeName());
//					System.out.println("Full type name: " + typeEntry.getFullTypeName());
//					System.out.println("File: " + typeEntry.getFile());
//				}
//
//				if (firstElement instanceof final IFile file) {
//					System.out.println("\n--- IFile Details ---");
//					System.out.println("Name: " + file.getName());
//					System.out.println("Extension: " + file.getFileExtension());
//					System.out.println("Path: " + file.getFullPath());
//				}
//			}
//		} else {
//			System.out.println("Selection is NOT IStructuredSelection!");
//		}
//
//		System.out.println("==========================================");
//
//		return null;
//	}
//}