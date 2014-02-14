package com.redhat.ceylon.eclipse.code.preferences;

import static com.redhat.ceylon.compiler.typechecker.TypeChecker.LANGUAGE_MODULE_VERSION;
import static org.eclipse.jface.layout.GridDataFactory.swtDefaults;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.FolderSelectionDialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;

import com.redhat.ceylon.common.config.ConfigParser;
import com.redhat.ceylon.common.config.Repositories;
import com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.core.builder.CeylonProjectConfig;

public class CeylonRepoConfigBlock {

    public interface ValidationCallback {
        void validationResultChange(boolean isValid, String message);
    }

    private IProject project;
    private ValidationCallback validationCallback;

    private List<String> projectLocalRepos;
    private List<String> globalLookupRepos;
    private List<String> projectRemoteRepos;
    private List<String> otherRemoteRepos;

    private Text systemRepoText;
    private Text outputRepoText;
    private Table lookupRepoTable;
    private Button systemRepoBrowseButton;
    private Button outputRepoBrowseButton;
    private Button addRepoButton;
    private Button addExternalRepoButton;
    private Button addAetherRepoButton;
    private Button addRemoteRepoButton;
    private Button removeRepoButton;
    private Button upButton;
    private Button downButton;

    public CeylonRepoConfigBlock(ValidationCallback validationCallback) {
        this.validationCallback = validationCallback;
    }
    
    public IProject getProject() {
        return project;
    }

    public String getSystemRepo() {
        return systemRepoText.getText();
    }

    public String getOutputRepo() {
        return outputRepoText.getText();
    }

    public List<String> getProjectLocalRepos() {
        return projectLocalRepos;
    }
    
    public List<String> getProjectRemoteRepos() {
        return projectRemoteRepos;
    }

    public void performDefaults() {
        systemRepoText.setText("${ceylon.repo}");
        outputRepoText.setText(Repositories.withConfig(ConfigParser.loadDefaultConfig(null))
                .getOutputRepository().getUrl());

        projectLocalRepos = new ArrayList<String>();
        projectRemoteRepos = new ArrayList<String>();
        lookupRepoTable.removeAll();
        addLookupRepos(globalLookupRepos, true);
        addLookupRepos(otherRemoteRepos, true);
        
        validate();
    }
    
    public void initState(IProject project, boolean isCeylonNatureEnabled) {
        this.project = project;
        
        if (isCeylonNatureEnabled) {
            projectLocalRepos = CeylonProjectConfig.get(project).getProjectLocalRepos();
            projectRemoteRepos = CeylonProjectConfig.get(project).getProjectRemoteRepos();
            globalLookupRepos = CeylonProjectConfig.get(project).getGlobalLookupRepos();
            otherRemoteRepos = CeylonProjectConfig.get(project).getOtherRemoteRepos();
        }
        
        systemRepoText.setText(CeylonBuilder.getCeylonSystemRepo(project));
        systemRepoText.setEnabled(isCeylonNatureEnabled);
        systemRepoBrowseButton.setEnabled(isCeylonNatureEnabled);
        
        outputRepoText.setText(CeylonProjectConfig.get(project).getOutputRepo());
        outputRepoText.setEnabled(isCeylonNatureEnabled);
        outputRepoBrowseButton.setEnabled(isCeylonNatureEnabled);
        
        lookupRepoTable.setEnabled(isCeylonNatureEnabled);
        lookupRepoTable.removeAll();
        addLookupRepos(projectLocalRepos, false);
        addLookupRepos(globalLookupRepos, true);
        addLookupRepos(projectRemoteRepos, false);
        addLookupRepos(otherRemoteRepos, true);
        
        addRepoButton.setEnabled(isCeylonNatureEnabled);
        addExternalRepoButton.setEnabled(isCeylonNatureEnabled);
        addAetherRepoButton.setEnabled(isCeylonNatureEnabled);
        addRemoteRepoButton.setEnabled(isCeylonNatureEnabled);
    }

    public void initContents(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(2, false));
        composite.setLayoutData(swtDefaults().grab(true, true).align(SWT.FILL, SWT.FILL).create());

        initSystemRepoInput(composite);
        initSystemRepoBrowseButton(composite);
        initOutputRepoInput(composite);
        initOutputRepoBrowseButton(composite);
        initLookupRepoTable(composite);

        Composite lookupRepoButtons = new Composite(composite, SWT.NONE);
        lookupRepoButtons.setLayout(new GridLayout(1, false));
        lookupRepoButtons.setLayoutData(swtDefaults().align(SWT.FILL, SWT.TOP).span(1, 4).create());

        initAddRepoButton(lookupRepoButtons);
        initAddExternalRepoButton(lookupRepoButtons);
        initAddAetherRepoButton(lookupRepoButtons);
        initAddRemoteRepoButton(lookupRepoButtons);
        initRemoveRepoButton(lookupRepoButtons);
        initUpDownButtons(lookupRepoButtons);
        
        performDefaults();
    }

    private void initSystemRepoInput(Composite composite) {
        Label systemRepoLabel = new Label(composite, SWT.LEFT | SWT.WRAP);
        systemRepoLabel.setText("System repository (contains language module)");
        systemRepoLabel.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).span(2, 1).grab(true, false).create());

        systemRepoText = new Text(composite, SWT.SINGLE | SWT.BORDER);
        systemRepoText.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).create());
        systemRepoText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                validate();
            }
        });    
    }

    private void initSystemRepoBrowseButton(final Composite composite) {
        systemRepoBrowseButton = new Button(composite, SWT.PUSH);
        systemRepoBrowseButton.setText("Browse...");
        systemRepoBrowseButton.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).create());
        systemRepoBrowseButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String result = new DirectoryDialog(composite.getShell(), SWT.SHEET).open();
                if (result != null) {
                    systemRepoText.setText(result);
                }
            }
        });
    }

    private void initOutputRepoInput(Composite composite) {
        Label outputRepoLabel = new Label(composite, SWT.LEFT | SWT.WRAP);
        outputRepoLabel.setText("Output repository (contains compiled module archives)");
        outputRepoLabel.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).span(2, 1).grab(true, false).indent(0, 10).create());

        outputRepoText = new Text(composite, SWT.SINGLE | SWT.BORDER);
        outputRepoText.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).create());
        outputRepoText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                validate();
            }
        });    
    }

    private void initOutputRepoBrowseButton(final Composite composite) {
        outputRepoBrowseButton = new Button(composite, SWT.PUSH);
        outputRepoBrowseButton.setText("Browse...");
        outputRepoBrowseButton.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).create());
        outputRepoBrowseButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IResource result = openSelectRelativeFolderDialog(composite, "Output Repository",
                        "Select a workspace folder for compiled module archives");
                if (result != null) {
                    String outputRepoUrl = "." + File.separator + result.getFullPath().removeFirstSegments(1);
                    outputRepoText.setText(outputRepoUrl);
                }
            }
        });
    }

    private void initLookupRepoTable(Composite composite) {
        Label lookupRepoLabel = new Label(composite, SWT.LEFT | SWT.WRAP);
        lookupRepoLabel.setText("Lookup repositories on build path:");
        lookupRepoLabel.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).span(2, 1).grab(true, false).indent(0, 10).create());

        lookupRepoTable = new Table(composite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        lookupRepoTable.setLayoutData(swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).hint(250, 220).create());
        lookupRepoTable.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateButtonState();
            }
        });
    }

    private void initAddRepoButton(final Composite buttons) {
        addRepoButton = new Button(buttons, SWT.PUSH);
        addRepoButton.setText("Add Repository...");
        addRepoButton.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).create());
        addRepoButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IResource result = openSelectRelativeFolderDialog(buttons, "Add Repository",
                        "Select a module repository in the workspace");
                if( result != null ) {
                    String repo = "." + File.separator + result.getFullPath().removeFirstSegments(1);
                    addProjectRepo(repo, 0, true);
                }
            }
        });
    }

    private void initAddExternalRepoButton(final Composite buttons) {
        addExternalRepoButton = new Button(buttons, SWT.PUSH);
        addExternalRepoButton.setText("Add External Repository...");
        addExternalRepoButton.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).create());
        addExternalRepoButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String result = new DirectoryDialog(buttons.getShell(), SWT.SHEET).open();
                if (result != null) {
                    addProjectRepo(result, 0, true);
                }
            }
        });
    }

    private void initAddAetherRepoButton(final Composite buttons) {
        addAetherRepoButton = new Button(buttons, SWT.PUSH);
        addAetherRepoButton.setText("Add Maven Repository...");
        addAetherRepoButton.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).create());
        addAetherRepoButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                AetherRepositoryDialog dlg = new AetherRepositoryDialog(buttons.getShell());
                int result = dlg.open();
                if (result == InputDialog.OK) {
                    int index = projectLocalRepos.size() + globalLookupRepos.size() + projectRemoteRepos.size();
                    String value = dlg.getValue();
                    if (value.isEmpty()) {
                        addProjectRepo("aether", index, false);
                    } else {
                        addProjectRepo("aether:" + dlg.getValue(), index, false);
                    }
                }
            }
        });
    }
    
    private void initAddRemoteRepoButton(final Composite buttons) {
        addRemoteRepoButton = new Button(buttons, SWT.PUSH);
        addRemoteRepoButton.setText("Add Remote Repository...");
        addRemoteRepoButton.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).create());
        addRemoteRepoButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IInputValidator inputValidator = new IInputValidator() {
                    @Override
                    public String isValid(String val) {
                        try {
                            // FIXME: we might want to validate it more than that: for example to check that it's http/https
                            URI uri = new URI(val);
                            if (!uri.isAbsolute()) {
                                return "Unsupported URI: missing scheme";
                            }
                            return null;
                        } catch (URISyntaxException e) {
                            return "Invalid URI: " + e.getReason();
                        }
                    }
                };
                InputDialog input = new InputDialog(buttons.getShell(), "Add Remote Repository", 
                        "Enter URI of remote module repository", "http://", inputValidator);
                int result = input.open();
                if (result == InputDialog.OK) {
                    int index = projectLocalRepos.size() + globalLookupRepos.size() + projectRemoteRepos.size();
                    addProjectRepo(input.getValue(), index, false);
                }
            }
        });
    }

    private void initRemoveRepoButton(Composite buttons) {
        removeRepoButton = new Button(buttons, SWT.PUSH);
        removeRepoButton.setText("Remove Repository");
        removeRepoButton.setEnabled(false);
        removeRepoButton.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).create());
        removeRepoButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int[] selection = lookupRepoTable.getSelectionIndices();
                Arrays.sort(selection);
                for (int i = selection.length - 1; i >= 0; i--) {
                    int index = selection[i];
                    if (!isFixedRepoIndex(index)) {
                        String repo = lookupRepoTable.getItem(index).getText();
                        lookupRepoTable.remove(index);
                        projectLocalRepos.remove(repo);
                        projectRemoteRepos.remove(repo);
                    }
                }
                lookupRepoTable.deselectAll();
                updateButtonState();
            }
        });
    }
    
    private void initUpDownButtons(Composite buttons) {
        upButton = new Button(buttons, SWT.PUSH);
        upButton.setText("Up");
        upButton.setEnabled(false);
        upButton.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).indent(0, 10).create());
        upButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int index = lookupRepoTable.getSelectionIndex();
                if( index != -1 ) {
                    String repo = lookupRepoTable.getItem(index).getText();
                    lookupRepoTable.remove(index);
                    if (index > 0 && index <= projectLocalRepos.size()) {
                        projectLocalRepos.remove(index);
                        addProjectRepo(repo, index - 1, true);
                    }
                    if (index == projectLocalRepos.size() + globalLookupRepos.size()) {
                        projectRemoteRepos.remove(repo);
                        addProjectRepo(repo, projectLocalRepos.size(), true);
                    }
                    if (index > projectLocalRepos.size() + globalLookupRepos.size()) {
                        projectRemoteRepos.remove(repo);
                        addProjectRepo(repo, index - 1, false);
                    }                
                }
            }
        });
        
        downButton = new Button(buttons, SWT.PUSH);
        downButton.setText("Down");
        downButton.setEnabled(false);
        downButton.setLayoutData(swtDefaults().align(SWT.FILL, SWT.CENTER).create());
        downButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int index = lookupRepoTable.getSelectionIndex();
                if( index != -1 ) {
                    String repo = lookupRepoTable.getItem(index).getText();
                    lookupRepoTable.remove(index);
                    if (index < projectLocalRepos.size() - 1 && !projectLocalRepos.isEmpty()) {
                        projectLocalRepos.remove(repo);
                        addProjectRepo(repo, index + 1, true);
                    }
                    if( index == projectLocalRepos.size() - 1 && !projectLocalRepos.isEmpty()) {
                        projectLocalRepos.remove(repo);
                        addProjectRepo(repo, projectLocalRepos.size() + globalLookupRepos.size(), false);
                    }
                    if( index >= projectLocalRepos.size() + globalLookupRepos.size() 
                            && index < projectLocalRepos.size() + globalLookupRepos.size() + projectRemoteRepos.size() - 1 ) {
                        projectRemoteRepos.remove(repo);
                        addProjectRepo(repo, index + 1, false);
                    }
                }
            }
        });
    }
    
    private void updateButtonState() {
        updateRemoveRepoButtonState();
        updateUpDownButtonState();
    }

    private void updateRemoveRepoButtonState() {
        int[] selectionIndices = lookupRepoTable.getSelectionIndices();
        for (int index : selectionIndices) {
            if (!isFixedRepoIndex(index)) {
                removeRepoButton.setEnabled(true);
                return;
            }
        }
        removeRepoButton.setEnabled(false);
    }

    private void updateUpDownButtonState() {
        boolean isUpEnabled = false;
        boolean isDownEnabled = false;

        int[] selectionIndices = lookupRepoTable.getSelectionIndices();
        if (selectionIndices.length == 1) {
            int index = selectionIndices[0];
            if (index > 0 && !isFixedRepoIndex(index)) {
                isUpEnabled = true;
            }
            int maxIndex = projectLocalRepos.size() + globalLookupRepos.size() + projectRemoteRepos.size() - 1;
            if (index < maxIndex && !isFixedRepoIndex(index)) {
                isDownEnabled = true;
            }
        }

        upButton.setEnabled(isUpEnabled);
        downButton.setEnabled(isDownEnabled);
    }

    private void validate() {
        if (!isSystemRepoValid()) {
            validationCallback.validationResultChange(false, "Please select a system module repository containing the language module");
        } else if (!isOutputRepoValid()) {
            validationCallback.validationResultChange(false, "Please select a output module repository inside project");
        } else {
            validationCallback.validationResultChange(true, null);
        }
    }

    private boolean isFixedRepoIndex(int index) {
        if (!globalLookupRepos.isEmpty() 
                && index >= projectLocalRepos.size()
                && index < projectLocalRepos.size() + globalLookupRepos.size()) {
            return true;
        }
        if (!otherRemoteRepos.isEmpty() 
                && index >= projectLocalRepos.size() + globalLookupRepos.size() + projectRemoteRepos.size()
                && index < projectLocalRepos.size() + globalLookupRepos.size() + projectRemoteRepos.size() + otherRemoteRepos.size()) {
            return true;
        }
        return false;
    }

    private boolean isSystemRepoValid() {
        String systemRepoUrl = systemRepoText.getText();
        if (systemRepoUrl != null && !systemRepoUrl.isEmpty()) {
            systemRepoUrl = CeylonBuilder.interpolateVariablesInRepositoryPath(systemRepoUrl);

            String ceylonLanguageSubdir = File.separator+"ceylon"+File.separator+"language"+File.separator + LANGUAGE_MODULE_VERSION;
            String ceylonLanguageFileName = File.separator+"ceylon.language-" + LANGUAGE_MODULE_VERSION + ".car";

            File ceylonLanguageFile = new File(systemRepoUrl + ceylonLanguageSubdir + ceylonLanguageFileName);
            if (ceylonLanguageFile.exists() && ceylonLanguageFile.isFile()) {
                return true;
            }
        }
        return false;
    }

    private boolean isOutputRepoValid() {
        String outputRepoUrl = outputRepoText.getText();
        if (outputRepoUrl.startsWith("./") || outputRepoUrl.startsWith(".\\")) {
            return true;
        }
        return false;
    }

    private IResource openSelectRelativeFolderDialog(Control control, String title, 
            String description) {
        IWorkspaceRoot root = project.getWorkspace().getRoot();

        Class<?>[] acceptedClasses= new Class[] { IProject.class, IFolder.class };
        ISelectionStatusValidator validator= new TypedElementSelectionValidator(acceptedClasses, false);
        IProject[] allProjects= root.getProjects();
        ArrayList<IProject> rejectedElements= new ArrayList<IProject>(allProjects.length);
        for (int i= 0; i < allProjects.length; i++) {
            if (!allProjects[i].equals(project)) {
                rejectedElements.add(allProjects[i]);
            }
        }
        ViewerFilter filter= new TypedViewerFilter(acceptedClasses, rejectedElements.toArray());

        ILabelProvider lp= new WorkbenchLabelProvider();
        ITreeContentProvider cp= new WorkbenchContentProvider();

        IResource container= null;
        try {
            container = getOutputFolder();
            if (container.exists() && container.isHidden()) {
                container.setHidden(false); // TODO: whooaah awful hack!
            }
        } catch(IllegalArgumentException iae) {
            // noop (IllegalArgumentException: Path must include project and resource name)
        } catch (CoreException ce) {
            ce.printStackTrace();
        }

        try {
            FolderSelectionDialog dialog= new FolderSelectionDialog(control.getShell(), lp, cp);
            dialog.setTitle(title);
            dialog.setValidator(validator);
            dialog.setMessage(description);
            dialog.addFilter(filter);
            dialog.setInput(root);
            dialog.setInitialSelection(container);
            dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));

            if (dialog.open() == Window.OK) {
                return (IResource)dialog.getFirstResult();
            }
            return null;
        }
        finally {
            if (container != null && container.exists()) {
                try {
                    container.setHidden(true);
                } 
                catch (Exception ce) {
                    ce.printStackTrace();
                }
            }
        }
    }

    private void addProjectRepo(String repo, int index, boolean isLocalRepo) {
        if (isLocalRepo && projectLocalRepos.contains(repo)) {
            return;
        }
        if (!isLocalRepo && projectRemoteRepos.contains(repo)) {
            return;
        }
        
        if (isLocalRepo) {
            projectLocalRepos.add(index, repo);
        } else {
            int remoteIndex = index - projectLocalRepos.size() - globalLookupRepos.size();
            projectRemoteRepos.add(remoteIndex, repo);
        }
        
        TableItem tableItem = new TableItem(lookupRepoTable, SWT.NONE, index);
        tableItem.setText(repo);
        tableItem.setImage(CeylonLabelProvider.REPO);
        lookupRepoTable.setSelection(index);

        validate();
        updateButtonState();
    }

    private void addLookupRepos(List<String> repos, boolean isGlobalRepo) {
        if (repos != null) {
            for (String repo : repos) {
                TableItem tableItem = new TableItem(lookupRepoTable, SWT.NONE);
                tableItem.setText(repo);
                tableItem.setImage(CeylonLabelProvider.REPO);
                if (isGlobalRepo) {
                    tableItem.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_TITLE_INACTIVE_FOREGROUND));
                }
            }
        }
    }

    private IFolder getOutputFolder() {
        String outputRepoUrl = outputRepoText.getText();
        if (outputRepoUrl.startsWith("./") || outputRepoUrl.startsWith(".\\")) {
            outputRepoUrl = outputRepoUrl.substring(2);
        }
        return project.getFolder(outputRepoUrl);
    }    

}