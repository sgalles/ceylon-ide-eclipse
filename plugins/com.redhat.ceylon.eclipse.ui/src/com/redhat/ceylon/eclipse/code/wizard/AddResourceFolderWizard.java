package com.redhat.ceylon.eclipse.code.wizard;

/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathWizard;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.SetFilterWizardPage;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;

public class AddResourceFolderWizard extends BuildPathWizard {

    private AddResourceFolderWizardPage fAddFolderPage;
    private SetFilterWizardPage fFilterPage;
    private final boolean fLinkedMode;
    private boolean fAllowConflict;
    private final boolean fAllowRemoveProjectFolder;
    private final boolean fAllowAddExclusionPatterns;
    private final boolean fCanCommitConflict;
    private final IContainer fParent;

    public AddResourceFolderWizard(CPListElement[] existingEntries, CPListElement newEntry, IPath outputLocation,
            boolean linkedMode, boolean canCommitConflict,
            boolean allowConflict, boolean allowRemoveProjectFolder, boolean allowAddExclusionPatterns) {
        this(existingEntries, newEntry, outputLocation, linkedMode, canCommitConflict, allowConflict, allowRemoveProjectFolder, allowAddExclusionPatterns, newEntry.getJavaProject().getProject());
    }

    public AddResourceFolderWizard(CPListElement[] existingEntries, CPListElement newEntry, IPath outputLocation,
            boolean linkedMode, boolean canCommitConflict,
            boolean allowConflict, boolean allowRemoveProjectFolder, boolean allowAddExclusionPatterns, IContainer parent) {
        super(existingEntries, newEntry, outputLocation, getTitel(newEntry, linkedMode), 
                IDEWorkbenchPlugin.getIDEImageDescriptor("wizban/newfolder_wiz.png"));
        fLinkedMode= linkedMode;
        fCanCommitConflict= canCommitConflict;
        fAllowConflict= allowConflict;
        fAllowRemoveProjectFolder= allowRemoveProjectFolder;
        fAllowAddExclusionPatterns= allowAddExclusionPatterns;
        fParent= parent;
    }

    private static String getTitel(CPListElement newEntry, boolean linkedMode) {
        if (newEntry.getPath() == null) {
            if (linkedMode) {
                return "Link Resources";
            } else {
                return "New Resource Folder";
            }
        } else {
            return "Edit resource folder";
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPages() {
        super.addPages();

        fAddFolderPage= new AddResourceFolderWizardPage(getEntryToEdit(), getExistingEntries(), getOutputLocation(),
                fLinkedMode, fCanCommitConflict,
                fAllowConflict, fAllowRemoveProjectFolder, fAllowAddExclusionPatterns, fParent);
        addPage(fAddFolderPage);

        fFilterPage= new SetFilterWizardPage(getEntryToEdit(), getExistingEntries(), getOutputLocation());
        addPage(fFilterPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CPListElement> getInsertedElements() {
        List<CPListElement> result= super.getInsertedElements();
        if (getEntryToEdit().getOrginalPath() == null)
            result.add(getEntryToEdit());

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CPListElement> getRemovedElements() {
        return fAddFolderPage.getRemovedElements();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CPListElement> getModifiedElements() {
        return fAddFolderPage.getModifiedElements();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean performFinish() {
        getEntryToEdit().setAttribute(CPListElement.INCLUSION, fFilterPage.getInclusionPattern());
        getEntryToEdit().setAttribute(CPListElement.EXCLUSION, fFilterPage.getExclusionPattern());
        setOutputLocation(fAddFolderPage.getOutputLocation());

        boolean res= super.performFinish();
        if (res) {
            selectAndReveal(fAddFolderPage.getCorrespondingResource());
        }
        return res;
    }

    @Override
    public void cancel() {
        fAddFolderPage.restore();
    }
}
