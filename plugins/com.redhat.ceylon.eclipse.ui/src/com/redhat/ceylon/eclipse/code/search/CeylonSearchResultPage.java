package com.redhat.ceylon.eclipse.code.search;

import static com.redhat.ceylon.eclipse.code.editor.Navigation.gotoLocation;
import static com.redhat.ceylon.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_FILE;
import static com.redhat.ceylon.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_FOLDER;
import static com.redhat.ceylon.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_MODULE;
import static com.redhat.ceylon.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_PACKAGE;
import static com.redhat.ceylon.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_PROJECT;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.FLAT_MODE;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.FOLDER_MODE;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.MODULE_MODE;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.PACKAGE_MODE;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.PROJECT_MODE;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.TREE_MODE;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.UNIT_MODE;
import static org.eclipse.search.ui.IContextMenuConstants.GROUP_VIEWER_SETUP;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.search.JavaSearchEditorOpener;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.Match;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.texteditor.ITextEditor;

import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;

public class CeylonSearchResultPage extends AbstractTextSearchViewPage {
    
    private CeylonStructuredContentProvider contentProvider;
    
    public CeylonSearchResultPage() {
        super(FLAG_LAYOUT_FLAT|FLAG_LAYOUT_TREE);
        setElementLimit(50);
        initGroupingActions();
    }
    
    @Override
    protected void clear() {
        if (contentProvider!=null) {
            contentProvider.clear();
        }
        //getViewer().refresh();
    }

    private void configureViewer(StructuredViewer viewer) {
        viewer.setContentProvider(contentProvider);
        viewer.setLabelProvider(new MatchCountingLabelProvider(this));
        viewer.setComparator(new CeylonViewerComparator());
    }

    @Override
    protected void configureTableViewer(final TableViewer viewer) {
        contentProvider = new CeylonSearchResultContentProvider(viewer, this);
        configureViewer(viewer);
    }

    @Override
    protected void configureTreeViewer(TreeViewer viewer) {
        contentProvider = new CeylonSearchResultTreeContentProvider(viewer, this);
        configureViewer(viewer);
    }

    @Override
    protected void elementsChanged(Object[] elements) {
        if (contentProvider!=null) {
            contentProvider.elementsChanged(elements);
        }
        getViewer().refresh();
    }
    
    @Override
    protected void showMatch(Match match, int offset, int length, boolean activate)
            throws PartInitException {
        Object elem = match.getElement();
        if (elem instanceof CeylonElement) {
            open((CeylonElement) elem, offset, length, activate);
        }
        else if (elem instanceof IJavaElement) {
            open((IJavaElement) elem, match, offset, length, activate);
        }
    }

    private void open(IJavaElement element, Match match, int offset,
            int length, boolean activate) throws PartInitException {
        IFile file = (IFile) element.getResource();
        if (file==null) {
            //a binary archive
            //TODO: is there some way to detect that the source
            //      here is actually written in Ceylon and open
            //      in the Ceylon editor? Relevant for ceylon.language
            JavaSearchEditorOpener editorOpener = new JavaSearchEditorOpener();
            IEditorPart javaEditor = editorOpener.openMatch(match);
            if (javaEditor instanceof ITextEditor) {
                ((ITextEditor) javaEditor).selectAndReveal(offset, length);
            }
        }
        else {
            //a source file in the workspace
            IWorkbenchPage page = getSite().getPage();
            if (offset >= 0 && length != 0) {
                openAndSelect(page, file, offset, length, activate);
            } 
            else {
                open(page, file, activate);
            }
        }
    }

    private void open(CeylonElement element, int offset, int length,
            boolean activate) throws PartInitException {
        IFile file = element.getFile();
        if (file==null) {
            Path path = new Path(element.getVirtualFile().getPath());
            gotoLocation(path, offset, length);
        }
        else {
            IWorkbenchPage page = getSite().getPage();
            if (offset>=0 && length!=0) {
                openAndSelect(page, file, offset, length, activate);
            } 
            else {
                open(page, file, activate);
            }
        }
    }
    
    private static final String GROUP_LAYOUT = 
            PLUGIN_ID + ".search.CeylonSearchResultPage.layout";
    private static final String GROUP_GROUPING = 
            PLUGIN_ID + ".search.CeylonSearchResultPage.grouping";
    private static final String KEY_GROUPING = 
            PLUGIN_ID + ".search.CeylonSearchResultPage.grouping";
    
    private GroupAction fGroupFileAction;
    private GroupAction fGroupPackageAction;
    private GroupAction fGroupModuleAction;
    private GroupAction fGroupFolderAction;
    private GroupAction fGroupProjectAction;
    
    private LayoutAction fLayoutFlatAction;
    private LayoutAction fLayoutTreeAction;
    
    private int fCurrentGrouping;
    
    private void initGroupingActions() {
        fGroupProjectAction= new GroupAction("Project", "Group by Project", 
                PROJECT_MODE, LEVEL_PROJECT);
        fGroupFolderAction= new GroupAction("Source Folder", "Group by Source Folder", 
                FOLDER_MODE, LEVEL_FOLDER);
        fGroupModuleAction= new GroupAction("Module", "Group by Module", 
                MODULE_MODE, LEVEL_MODULE);
        fGroupPackageAction= new GroupAction("Package", "Group by Package", 
                PACKAGE_MODE, LEVEL_PACKAGE);
        fGroupFileAction= new GroupAction("Source File", "Group by Source File", 
                UNIT_MODE, LEVEL_FILE);
        
        fLayoutTreeAction= new LayoutAction("Tree", "Tree Layout", 
                TREE_MODE, FLAG_LAYOUT_TREE);
        fLayoutFlatAction= new LayoutAction("Float", "Flat Layout", 
                FLAT_MODE, FLAG_LAYOUT_FLAT);
    }
    
    private void updateGroupingActions() {
        fGroupProjectAction.setChecked(fCurrentGrouping == LEVEL_PROJECT);
        fGroupFolderAction.setChecked(fCurrentGrouping == LEVEL_FOLDER);
        fGroupModuleAction.setChecked(fCurrentGrouping == LEVEL_MODULE);
        fGroupPackageAction.setChecked(fCurrentGrouping == LEVEL_PACKAGE);
        fGroupFileAction.setChecked(fCurrentGrouping == LEVEL_FILE);
    }
    
    private void updateLayoutActions() {
        int layout = getLayout();
        fLayoutFlatAction.setChecked(layout==FLAG_LAYOUT_FLAT);
        fLayoutTreeAction.setChecked(layout==FLAG_LAYOUT_TREE);
    }
    
    @Override
    protected void fillToolbar(IToolBarManager tbm) {
        super.fillToolbar(tbm);
        tbm.appendToGroup(GROUP_VIEWER_SETUP, new Separator(GROUP_LAYOUT));
        tbm.appendToGroup(GROUP_LAYOUT, fLayoutTreeAction);
        tbm.appendToGroup(GROUP_LAYOUT, fLayoutFlatAction);
        updateLayoutActions();
        if (getLayout()!= FLAG_LAYOUT_FLAT) {
            tbm.appendToGroup(GROUP_VIEWER_SETUP, new Separator(GROUP_GROUPING));
            tbm.appendToGroup(GROUP_GROUPING, fGroupProjectAction);
            tbm.appendToGroup(GROUP_GROUPING, fGroupFolderAction);
            tbm.appendToGroup(GROUP_GROUPING, fGroupModuleAction);
            tbm.appendToGroup(GROUP_GROUPING, fGroupPackageAction);
            tbm.appendToGroup(GROUP_GROUPING, fGroupFileAction);
            try {
                fCurrentGrouping = getSettings().getInt(KEY_GROUPING);
            }
            catch (NumberFormatException nfe) {
                //missing key
                fCurrentGrouping = LEVEL_PROJECT;
            }
            contentProvider.setLevel(fCurrentGrouping);
            updateGroupingActions();
        }
        this.getSite().getActionBars().updateActionBars();
    }
    
    @Override
    protected void fillContextMenu(IMenuManager mgr) {
        super.fillContextMenu(mgr);
        MenuManager submenu = new MenuManager("Find");
        submenu.setActionDefinitionId(CeylonEditor.FIND_MENU_ID);
        submenu.add(new FindReferencesAction(this, 
                this.getViewer().getSelection()));
        submenu.add(new FindAssignmentsAction(this, 
                this.getViewer().getSelection()));
        submenu.add(new FindSubtypesAction(this, 
                this.getViewer().getSelection()));
        submenu.add(new FindRefinementsAction(this, 
                this.getViewer().getSelection()));
        mgr.add(submenu);
    }
    
    private class GroupAction extends Action {
        private int fGrouping;

        public GroupAction(String label, String tooltip, 
                String imageKey, int grouping) {
            super(label);
            setToolTipText(tooltip);
            setImageDescriptor(CeylonPlugin.getInstance()
                    .getImageRegistry()
                    .getDescriptor(imageKey));
            fGrouping = grouping;
        }
        
        @Override
        public boolean isEnabled() {
            return getLayout()!= FLAG_LAYOUT_FLAT;
        }

        @Override
        public void run() {
            setGrouping(fGrouping);
        }

    }

    private class LayoutAction extends Action {
        private int fLayout;

        public LayoutAction(String label, String tooltip, 
                String imageKey, int layout) {
            super(label);
            setToolTipText(tooltip);
            setImageDescriptor(CeylonPlugin.getInstance()
                    .getImageRegistry()
                    .getDescriptor(imageKey));
            fLayout = layout;
        }

        @Override
        public void run() {
            setLayout(fLayout);
            setChecked(getLayout()==fLayout);
        }
    }

    void setGrouping(int grouping) {
        fCurrentGrouping= grouping;
        contentProvider.setLevel(grouping);
        updateGroupingActions();
        getSettings().put(KEY_GROUPING, fCurrentGrouping);
        getViewPart().updateLabel();
    }
    
}
