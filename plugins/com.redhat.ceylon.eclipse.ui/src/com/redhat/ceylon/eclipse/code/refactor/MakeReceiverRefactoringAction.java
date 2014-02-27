package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ui.IEditorPart;

public class MakeReceiverRefactoringAction extends AbstractRefactoringAction {
    public MakeReceiverRefactoringAction(IEditorPart editor) {
        super("MakeReceiver.", editor);
        setActionDefinitionId(PLUGIN_ID + ".action.makeReceiver");
    }
    
    @Override
    public AbstractRefactoring createRefactoring() {
        return new MakeReceiverRefactoring(getTextEditor());
    }
    
    @Override
    public RefactoringWizard createWizard(AbstractRefactoring refactoring) {
        return new MakeReceiverWizard((MakeReceiverRefactoring) refactoring);
    }
    
    @Override
    String message() {
        return "No parameter name selected";
    }

}