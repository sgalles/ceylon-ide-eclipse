package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ui.IEditorPart;

public class ChangeVersionRefactoringAction extends AbstractRefactoringAction {
    public ChangeVersionRefactoringAction(IEditorPart editor) {
        super("ChangeVersion.", editor);
        setActionDefinitionId(PLUGIN_ID + ".action.changeVersion");
    }
    
    @Override
    public Refactoring createRefactoring() {
        return new ChangeVersionRefactoring(getTextEditor());
    }
    
    @Override
    public RefactoringWizard createWizard(Refactoring refactoring) {
        return new ChangeVersionWizard((AbstractRefactoring) refactoring);
    }
    
    @Override
    public String message() {
        return "No module version selected";
    }
    
//    public String currentName() {
//        return ((RenameRefactoring) refactoring).getDeclaration().getName();
//    }
//    
}
