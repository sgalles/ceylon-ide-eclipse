package com.redhat.ceylon.eclipse.code.search;

import static com.redhat.ceylon.eclipse.code.editor.Navigation.gotoFile;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.CEYLON_DECS;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.CEYLON_REFS;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.FLAT_MODE;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.TREE_MODE;
import static com.redhat.ceylon.eclipse.util.Nodes.getReferencedExplicitDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.jface.text.IInformationControlExtension3;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Referenceable;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider;
import com.redhat.ceylon.eclipse.code.outline.TreeNodeLabelProvider;
import com.redhat.ceylon.eclipse.code.outline.TreeViewMouseListener;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;
import com.redhat.ceylon.eclipse.util.EditorUtil;
import com.redhat.ceylon.eclipse.util.FindReferencesVisitor;
import com.redhat.ceylon.eclipse.util.FindRefinementsVisitor;
import com.redhat.ceylon.eclipse.util.FindSubtypesVisitor;
import com.redhat.ceylon.eclipse.util.Highlights;

public final class ReferencesPopup extends PopupDialog 
        implements IInformationControl, IInformationControlExtension2,
                   IInformationControlExtension3 {
    
    private static final ImageRegistry imageRegistry = CeylonPlugin.getInstance().getImageRegistry();
    private static final Image REFS_IMAGE = imageRegistry.get(CEYLON_REFS);
    private static final Image DECS_IMAGE = imageRegistry.get(CEYLON_DECS);

    public class ChangeLayoutListener implements SelectionListener {
        @Override
        public void widgetSelected(SelectionEvent e) {
            treeLayout = !treeLayout;
            viewer = treeLayout ? treeViewer : tableViewer;
            treeViewer.getTree().setVisible(treeLayout);
            tableViewer.getTable().setVisible(!treeLayout);
            ((GridData)treeViewer.getControl().getLayoutData()).exclude=!treeLayout;
            ((GridData)tableViewer.getControl().getLayoutData()).exclude=treeLayout;
            viewer.getControl().getParent().layout(/*true*/);
            setInput(null);
            getDialogSettings().put("treeLayout", treeLayout);
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {}
    }

    public static final class LabelProvider extends TreeNodeLabelProvider {
        public LabelProvider() {
            super(new SearchResultsLabelProvider());
        }
        
        @Override
        public Object unwrap(Object element) {
            Object unwrapped = super.unwrap(element);
            if (unwrapped instanceof CeylonSearchMatch) {
                return ((CeylonSearchMatch) unwrapped).getElement();
            }
            else {
                return unwrapped;
            }
        }
    }

    public final class ClickListener implements MouseListener {
        @Override
        public void mouseUp(MouseEvent e) {
            gotoSelectedElement();
        }

        @Override
        public void mouseDown(MouseEvent e) {}

        @Override
        public void mouseDoubleClick(MouseEvent e) {
            gotoSelectedElement();
        }
    }

    public final class ShortcutKeyListener implements KeyListener {
        @Override
        public void keyReleased(KeyEvent e) {}

        @Override
        public void keyPressed(KeyEvent e) {
            triggerCommand(e);
            if (e.keyCode == 0x0D || e.keyCode == SWT.KEYPAD_CR) { // Enter key
                gotoSelectedElement();
            }
        }
    }

    public final class Filter extends ViewerFilter {
        @Override
        public boolean select(Viewer viewer, Object parentElement, Object element) {
            TreeNode treeNode = (TreeNode) element;
            Object value = treeNode.getValue();
            if (value instanceof CeylonSearchMatch) {
                CeylonSearchMatch match = (CeylonSearchMatch) value;
                String filter = filterText.getText().toLowerCase();
                String[] split = match.getElement().getLabel().getString().split(" ");
                return split.length>1 && split[1].toLowerCase().startsWith(filter) ||
                        match.getElement().getPackageLabel()
                            .toLowerCase().startsWith(filter) ||
                        match.getElement().getFile().getName().toString()
                            .toLowerCase().startsWith(filter);
            }
            else {
                for (TreeNode child: treeNode.getChildren()) {
                    if (select(viewer, element, child)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    protected Text filterText;
    
    private ColumnViewer viewer;
    
    private final CeylonEditor editor;
    
    private StyledText titleLabel;

    private TriggerSequence commandBinding;
    private TriggerSequence findCommandBinding;
    
    private boolean showingRefinements = false;
    
    public ReferencesPopup(Shell parent, int shellStyle, CeylonEditor editor) {
        super(parent, shellStyle, true, true, false, true,
                true, null, null);
        treeLayout = getDialogSettings().getBoolean("treeLayout");
        includeImports = getDialogSettings().getBoolean("includeImports");
        setTitleText("Quick Find References");
        this.editor = editor;
        commandBinding = EditorUtil.getCommandBinding(PLUGIN_ID + 
                ".editor.findReferences");
        findCommandBinding = EditorUtil.getCommandBinding(PLUGIN_ID + 
                ".action.findReferences");
        setStatusText();
        create();
        
        Color bg = parent.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);
        getShell().setBackground(bg);
        setBackgroundColor(bg);

        //setBackgroundColor(getEditorWidget(editor).getBackground());
        setForegroundColor(getEditorWidget(editor).getForeground());        
    }
    
    private void setIcon() {
        if (showingRefinements) {
            icon.setImage(DECS_IMAGE);
        }
        else {
            icon.setImage(REFS_IMAGE);
        }
    }

    private void setStatusText() {
        StringBuilder builder = new StringBuilder();
        if (findCommandBinding!=null) {
            builder.append(findCommandBinding.format())
                    .append(" to find all references");
        }
        if (commandBinding!=null) {
            String message;
            if (showingRefinements) {
                message = " to show references";
            }
            else {
                if (type) {
                    message = " to show subtypes";
                }
                else {
                    message = " to show refinements";
                }
            }
            if (builder.length()>0) {
                builder.append(" - ");
            }
            builder.append(commandBinding.format()).append(message);
        }
        if (builder.length()>0) {
            setInfoText(builder.toString());
        }
    }

    private StyledText getEditorWidget(CeylonEditor editor) {
        return editor.getCeylonSourceViewer().getTextWidget();
    }

    protected Control createContents(Composite parent) {
        Composite composite = (Composite) super.createContents(parent);
        GridLayout layout = (GridLayout) composite.getLayout();
        layout.verticalSpacing=8;
        layout.marginLeft=8;
        layout.marginRight=8;
        layout.marginTop=8;
        layout.marginBottom=8;
        Control[] children = composite.getChildren();
        children[children.length-2].setVisible(false);
        return composite;
    }
    
    @Override
    protected Control createDialogArea(Composite parent) {
        treeViewer = new TreeViewer(parent, SWT.FLAT);
        treeViewer.getTree().setVisible(treeLayout);
        GridData gdTree = new GridData(GridData.FILL_HORIZONTAL|GridData.FILL_VERTICAL);
        gdTree.exclude = !treeLayout;
        treeViewer.getTree().setLayoutData(gdTree);
        treeViewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);
        tableViewer = new TableViewer(parent, SWT.FLAT);
        tableViewer.getTable().setVisible(!treeLayout);
        GridData gdTable = new GridData(GridData.FILL_HORIZONTAL|GridData.FILL_VERTICAL);
        gdTable.exclude = treeLayout;
        tableViewer.getTable().setLayoutData(gdTable);
        viewer = treeLayout ? treeViewer : tableViewer;
        tableViewer.setComparator(new CeylonViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                return super.compare(viewer, 
                        ((TreeNode) e1).getValue(), 
                        ((TreeNode) e2).getValue());
            }
        });
        tableViewer.setContentProvider(ArrayContentProvider.getInstance());
        treeViewer.setContentProvider(new TreeNodeContentProvider());
        tableViewer.setLabelProvider(new LabelProvider());
        treeViewer.setLabelProvider(new LabelProvider());
        installFilter();
        ViewerFilter filter = new Filter();
        tableViewer.addFilter(filter);
        treeViewer.addFilter(filter);
//        viewer.getTable().addSelectionListener(new SelectionListener() {
//            public void widgetSelected(SelectionEvent e) {
//                // do nothing
//            }
//            public void widgetDefaultSelected(SelectionEvent e) {
//                gotoSelectedElement();
//            }
//        });
        Cursor cursor = new Cursor(getShell().getDisplay(), SWT.CURSOR_HAND);
        tableViewer.getControl().setCursor(cursor);
        treeViewer.getControl().setCursor(cursor);
        tableViewer.getControl().addMouseMoveListener(new MouseMoveListener() {
            @Override
            public void mouseMove(MouseEvent e) {
                Item item = tableViewer.getTable()
                            .getItem(new Point(e.x, e.y));
                if (item!=null) {
                    tableViewer.setSelection(new StructuredSelection(item.getData()));
                }
            }
        });
        treeViewer.getControl().addMouseMoveListener(new TreeViewMouseListener(treeViewer));
        ShortcutKeyListener listener = new ShortcutKeyListener();
        tableViewer.getControl().addKeyListener(listener);
        treeViewer.getControl().addKeyListener(listener);
        MouseListener clickListener = new ClickListener();
        tableViewer.getControl().addMouseListener(clickListener);
        treeViewer.getControl().addMouseListener(clickListener);
        viewer.getControl().getParent().layout(/*true*/);
        return viewer.getControl();
    }
    
    protected void gotoSelectedElement() {
        Object node = ((StructuredSelection) viewer.getSelection()).getFirstElement();
        if (node!=null) {
            Object elem = ((TreeNode) node).getValue();
            if (elem instanceof CeylonSearchMatch) {
                CeylonSearchMatch match = (CeylonSearchMatch) elem;
                gotoFile(match.getElement().getFile(), match.getOffset(), match.getLength());
            }
        }
    }

    private static GridLayoutFactory popupLayoutFactory;
    protected static GridLayoutFactory getPopupLayout() {
        if (popupLayoutFactory == null) {
            popupLayoutFactory = GridLayoutFactory.fillDefaults()
                    .margins(POPUP_MARGINWIDTH, POPUP_MARGINHEIGHT)
                    .spacing(POPUP_HORIZONTALSPACING, POPUP_VERTICALSPACING);
        }
        return popupLayoutFactory;
    }
    
    protected StyledString styleTitle(final StyledText title) {
        StyledString result = new StyledString();
        StringTokenizer tokens = 
                new StringTokenizer(title.getText(), "-'", false);
        styleDescription(title, result, tokens.nextToken());
        result.append("-");
        result.append(tokens.nextToken());
        result.append("'");
        Highlights.styleProposal(result, tokens.nextToken(), false);
        result.append("'");
        return result;
    }

    protected void styleDescription(final StyledText title, StyledString result,
            String desc) {
        final FontData[] fontDatas = title.getFont().getFontData();
        for (int i = 0; i < fontDatas.length; i++) {
            fontDatas[i].setStyle(SWT.BOLD);
        }
        result.append(desc, new Styler() {
            @Override
            public void applyStyles(TextStyle textStyle) {
                textStyle.font=new Font(title.getDisplay(), fontDatas);
            }
        });
    }
    
    private boolean includeImports = false;
    private boolean treeLayout = false;
    
    @Override
    protected Control createTitleControl(Composite parent) {
        getPopupLayout().copy()
            .numColumns(4)
            .spacing(6, 6)
            .applyTo(parent);
        icon = new Label(parent, SWT.NONE);
        icon.setImage(REFS_IMAGE);
//        getShell().addKeyListener(new GotoListener());
        titleLabel = new StyledText(parent, SWT.NONE);
        titleLabel.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                titleLabel.setStyleRanges(styleTitle(titleLabel).getStyleRanges());
            }
        });
        titleLabel.setEditable(false);
        GridDataFactory.fillDefaults()
            .align(SWT.FILL, SWT.CENTER)
            .grab(true,false)
            .span(1, 1)
            .applyTo(titleLabel);
//        Button button = new Button(parent, SWT.TOGGLE);
//        button.setImage(CeylonLabelProvider.IMPORT);
//        button.setText("include imports");
        ToolBar toolBar = new ToolBar(parent, SWT.FLAT);
        createModeButtons(toolBar);
        new ToolItem(toolBar, SWT.SEPARATOR);
        createLayoutButtons(toolBar);
        new ToolItem(toolBar, SWT.SEPARATOR);
        createImportsButton(toolBar);
        return null;
    }

    private void createImportsButton(ToolBar toolBar) {
        ToolItem button = new ToolItem(toolBar, SWT.CHECK);
        button.setImage(CeylonLabelProvider.IMPORT);
        button.setToolTipText("Show Matches in Import Statements");
        button.setSelection(includeImports);
        button.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                includeImports = !includeImports;
                setInput(null);
                getDialogSettings().put("includeImports", includeImports);
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
    }

    private void createModeButtons(ToolBar toolBar) {
        button1 = new ToolItem(toolBar, SWT.CHECK);
        button1.setImage(REFS_IMAGE);
        button1.setToolTipText("Show References");
        button2 = new ToolItem(toolBar, SWT.CHECK);
        button2.setImage(DECS_IMAGE);
        button2.setToolTipText("Show Refinements/Subtypes");
        updateButtonSelection();
        button1.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (button1.getSelection()) {
                    showingRefinements = false;
                    setInput(null);
                    button2.setSelection(false);
                }
                else {
                    button1.setSelection(true);
                }
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
        button2.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (button2.getSelection()) {
                    showingRefinements = true;
                    setInput(null);
                    button1.setSelection(false);
                }
                else {
                    button2.setSelection(true);
                }
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
    }
    
    private void updateButtonSelection() {
        button2.setSelection(showingRefinements);
        button1.setSelection(!showingRefinements);
    }
    
    private void createLayoutButtons(ToolBar toolBar) {
        final ToolItem button1 = new ToolItem(toolBar, SWT.CHECK);
        button1.setImage(imageRegistry.get(FLAT_MODE));
        button1.setToolTipText("Flat Layout");
        button1.setSelection(!treeLayout);
        final ToolItem button2 = new ToolItem(toolBar, SWT.CHECK);
        button2.setImage(imageRegistry.get(TREE_MODE));
        button2.setToolTipText("Tree Layout");
        button2.setSelection(treeLayout);
        button1.addSelectionListener(new ChangeLayoutListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (button1.getSelection()) {
                    super.widgetSelected(e);
                    button2.setSelection(false);
                }
                else {
                    button1.setSelection(true);
                }
            }
        });
        button2.addSelectionListener(new ChangeLayoutListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (button2.getSelection()) {
                    super.widgetSelected(e);
                    button1.setSelection(false);
                }
                else {
                    button2.setSelection(true);
                }
            }
        });
    }
    
    protected Text createFilterText(Composite parent) {
        filterText= new Text(parent, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
        filterText.setMessage("type filter text");
        Dialog.applyDialogFont(filterText);

        GridData data = new GridData(GridData.FILL_HORIZONTAL);
        data.horizontalAlignment = GridData.FILL;
        data.verticalAlignment = GridData.CENTER;
        filterText.setLayoutData(data);

        filterText.addKeyListener(new KeyListener() {
            public void keyPressed(KeyEvent e) {
                triggerCommand(e);
                if (e.keyCode == 0x0D || e.keyCode == SWT.KEYPAD_CR) // Enter key
                    gotoSelectedElement();
                if (e.keyCode == SWT.ARROW_DOWN)
                    viewer.getControl().setFocus();
                if (e.keyCode == SWT.ARROW_UP)
                    viewer.getControl().setFocus();
                if (e.character == 0x1B) // ESC
                    dispose();
            }
            public void keyReleased(KeyEvent e) {
                // do nothing
            }
        });
        
        return filterText;
    }

    private void triggerCommand(KeyEvent e) {
        if (EditorUtil.triggersBinding(e, commandBinding)) {
            showingRefinements = !showingRefinements;
            setInput(null);
            e.doit=false;
        }
        else if (EditorUtil.triggersBinding(e, findCommandBinding)) {
            showingRefinements = !showingRefinements;
            new FindReferencesAction(editor).run();
            e.doit=false;
        }
    }
    @Override
    protected void setTitleText(String text) {
        if (titleLabel!=null) {
            titleLabel.setText(text);
        }
    }
    
    private void installFilter() {
        filterText.setText("");
        filterText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                String text = ((Text) e.widget).getText();
                setMatcherString(text, true);
            }
        });
    }

    protected void setMatcherString(String pattern, boolean update) {
        /*if (pattern.length() == 0) {
            fPatternMatcher= null;
        } else {
            fPatternMatcher= new JavaElementPrefixPatternMatcher(pattern);
        }*/

        if (update) {
            viewer.getControl().setRedraw(false);
            viewer.refresh();
            viewer.getControl().setRedraw(true);
            selectFirst();
        }
    }

    @Override
    protected Control createTitleMenuArea(Composite parent) {
        Control result = super.createTitleMenuArea(parent);
        filterText = createFilterText(parent);
        return result;
    }
    
    /*@Override
    protected void adjustBounds() {
        Rectangle bounds = getShell().getBounds();
        int h = bounds.height;
        if (h>400) {
            bounds.height=400;
            bounds.y = bounds.y + (h-400)/3;
            getShell().setBounds(bounds);
        }
        int w = bounds.width;
        if (w<600) {
            bounds.width=600;
            getShell().setBounds(bounds);
        }
    }*/
    
    public void setInformation(String information) {
        // this method is ignored, see IInformationControlExtension2
    }

    public void setSize(int width, int height) {
        getShell().setSize(width, height);
    }

    public void addDisposeListener(DisposeListener listener) {
        getShell().addDisposeListener(listener);
    }

    public void removeDisposeListener(DisposeListener listener) {
        getShell().removeDisposeListener(listener);
    }

    public void setForegroundColor(Color foreground) {
        applyForegroundColor(foreground, getContents());
    }

    public void setBackgroundColor(Color background) {
        applyBackgroundColor(background, getContents());
    }

    public boolean isFocusControl() {
        return getShell().getDisplay().getActiveShell() == getShell();
    }

    public void setFocus() {
        getShell().forceFocus();
        filterText.setFocus();
    }

    public void addFocusListener(FocusListener listener) {
        getShell().addFocusListener(listener);
    }

    public void removeFocusListener(FocusListener listener) {
        getShell().removeFocusListener(listener);
    }


    public void setSizeConstraints(int maxWidth, int maxHeight) {
        // ignore
    }

    public void setLocation(Point location) {
        /*
         * If the location is persisted, it gets managed by PopupDialog - fine. Otherwise, the location is
         * computed in Window#getInitialLocation, which will center it in the parent shell / main
         * monitor, which is wrong for two reasons:
         * - we want to center over the editor / subject control, not the parent shell
         * - the center is computed via the initalSize, which may be also wrong since the size may
         *   have been updated since via min/max sizing of AbstractInformationControlManager.
         * In that case, override the location with the one computed by the manager. Note that
         * the call to constrainShellSize in PopupDialog.open will still ensure that the shell is
         * entirely visible.
         */
        if (!getPersistLocation() || getDialogSettings() == null)
            getShell().setLocation(location);
    }

    public Point computeSizeHint() {
        // return the shell's size - note that it already has the persisted size if persisting
        // is enabled.
        return getShell().getSize();
    }

    public void setVisible(boolean visible) {
        if (visible) {
            open();
        }
        else {
            saveDialogBounds(getShell());
            getShell().setVisible(false);
        }
    }

    public final void dispose() {
        close();
    }
    
    private boolean type;

    private TreeViewer treeViewer;
    private TableViewer tableViewer;
    private Label icon;
    private ToolItem button1;
    private ToolItem button2;
    
    @Override
    public void setInput(Object input) {
        CeylonParseController pc = editor.getParseController();
        Referenceable declaration = 
                getReferencedExplicitDeclaration(editor.getSelectedNode(), 
                        pc.getRootNode());
        if (declaration==null) {
            return;
        }
        type = declaration instanceof TypeDeclaration;
        String message;
        if (showingRefinements) {
            if (type) {
                message = "subtypes of";
            }
            else {
                message = "refinements of";
            }
        } else {
            message = "references to";
        }
        String name;
        if (declaration instanceof Declaration) {
            name = ((Declaration) declaration).getName(pc.getRootNode().getUnit());
        }
        else {
            name = declaration.getNameAsString();
        }
        setTitleText("Quick Find References - " + message + " '" + 
                        name + "' in project source");
        TreeNode root = new TreeNode(new Object());
        Map<Package,TreeNode> packageNodes = new HashMap<Package,TreeNode>();
        Map<Module,TreeNode> moduleNodes = new HashMap<Module,TreeNode>();
        List<TreeNode> allMatchesList = new ArrayList<TreeNode>();
        List<TreeNode> allUnitsList = new ArrayList<TreeNode>();
        for (PhasedUnit pu: pc.getTypeChecker()
                .getPhasedUnits()
                .getPhasedUnits()) {
            Tree.CompilationUnit cu = pu.getCompilationUnit();
            if (pu.getUnit().equals(pc.getRootNode().getUnit()) &&
                    editor.isDirty()) {
                //search in the current dirty editor
                cu = pc.getRootNode();
            }
            Unit u = cu.getUnit();
            TreeNode unitNode = new TreeNode(u);
            List<TreeNode> unitList = new ArrayList<TreeNode>();
            Set<Node> nodes;
            if (showingRefinements) {
                if (type) {
                    FindSubtypesVisitor frv = 
                            new FindSubtypesVisitor((TypeDeclaration) declaration);
                    frv.visit(cu);
                    nodes = new HashSet<Node>(frv.getDeclarationNodes());
                }
                else {
                    FindRefinementsVisitor frv = 
                            new FindRefinementsVisitor((Declaration) declaration);
                    frv.visit(cu);
                    nodes = new HashSet<Node>(frv.getDeclarationNodes());
                }
            }
            else {
                FindReferencesVisitor frv = 
                        new FindReferencesVisitor(declaration);
                frv.visit(cu);
                nodes = frv.getNodes();
            }
            for (Node node: nodes) {
                CeylonSearchMatch match = 
                        CeylonSearchMatch.create(node, cu, pu.getUnitFile());
                if (includeImports || !match.isInImport()) {
                    TreeNode matchNode = new TreeNode(match);
                    matchNode.setParent(unitNode);
                    allMatchesList.add(matchNode);
                    unitList.add(matchNode);
                }
            }
            if (!unitList.isEmpty()) {
                allUnitsList.add(unitNode);
                unitNode.setChildren(unitList.toArray(new TreeNode[0]));
                Package p = u.getPackage();
                TreeNode packageNode = packageNodes.get(p);
                if (packageNode==null) {
                    packageNode = new TreeNode(p);
                    TreeNode moduleNode = moduleNodes.get(p.getModule());
                    if (moduleNode==null) {
                        moduleNode = new TreeNode(p.getModule());
                        moduleNode.setParent(root);
                        moduleNodes.put(p.getModule(), moduleNode);
                        moduleNode.setChildren(new TreeNode[] {packageNode});
                    }
                    else {
                        TreeNode[] oldChildren = moduleNode.getChildren();
                        TreeNode[] children = new TreeNode[oldChildren.length+1];
                        for (int i=0; i<oldChildren.length; i++) {
                            children[i] = oldChildren[i];
                        }
                        children[oldChildren.length] = packageNode;
                        moduleNode.setChildren(children);
                    }
                    packageNode.setParent(moduleNode);
                    packageNodes.put(p, packageNode);
                    packageNode.setChildren(new TreeNode[] {unitNode});
                }
                else {
                    TreeNode[] oldChildren = packageNode.getChildren();
                    TreeNode[] children = new TreeNode[oldChildren.length+1];
                    for (int i=0; i<oldChildren.length; i++) {
                        children[i] = oldChildren[i];
                    }
                    children[oldChildren.length] = unitNode;
                    packageNode.setChildren(children);
                }
                unitNode.setParent(packageNode);
            }
        }
        root.setChildren(moduleNodes.values().toArray(new TreeNode[0]));
//        root.setChildren(allUnitsList.toArray(new TreeNode[0]));
        treeViewer.setInput(root.getChildren());
        tableViewer.setInput(allMatchesList);
        selectFirst();
        setStatusText();
        updateButtonSelection();
        setIcon();
    }

    private void selectFirst() {
        Object firstElem;
        if (viewer instanceof TableViewer) {
            firstElem = ((TableViewer) viewer).getElementAt(0);
        }
        else {
            org.eclipse.swt.widgets.Tree tree = ((TreeViewer) viewer).getTree();
            if (tree.getItemCount()>0) {
                firstElem = tree.getItem(0).getData();
            }
            else {
                firstElem = null;
            }
            
        }
        if (firstElem!=null) {
            viewer.setSelection(new StructuredSelection(firstElem), true);
        }
    }

    @Override
    public boolean restoresLocation() {
        return false;
    }
    
    @Override
    public boolean restoresSize() {
        return true;
    }
    
    @Override
    public Rectangle getBounds() {
        return getShell().getBounds();
    }
    
    @Override
    public Rectangle computeTrim() {
        return getShell().computeTrim(0, 0, 0, 0);
    }
    
    @Override
    protected IDialogSettings getDialogSettings() {
        String sectionName = "com.redhat.ceylon.eclipse.ui.FindReferences";
        IDialogSettings dialogSettings = CeylonPlugin.getInstance()
                .getDialogSettings();
        IDialogSettings settings = dialogSettings.getSection(sectionName);
        if (settings == null)
            settings= dialogSettings.addNewSection(sectionName);
        return settings;
    }

}