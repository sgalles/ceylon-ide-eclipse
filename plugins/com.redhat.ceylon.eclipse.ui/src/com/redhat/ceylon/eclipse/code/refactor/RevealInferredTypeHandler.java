package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.code.editor.Util.getCurrentEditor;
import static com.redhat.ceylon.eclipse.code.quickfix.ImportProposals.applyImports;
import static com.redhat.ceylon.eclipse.code.quickfix.ImportProposals.importType;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.ltk.core.refactoring.RefactoringCore.getUndoManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.LocalModifier;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;

public class RevealInferredTypeHandler extends AbstractHandler {

    @Override
    public boolean isEnabled() {
        IEditorPart ce = getCurrentEditor();
        if (!(ce instanceof CeylonEditor)) return false;
        CeylonEditor editor = (CeylonEditor) ce;
        List<Tree.LocalModifier> localModifiers = new ArrayList<Tree.LocalModifier>();
        List<Tree.ValueIterator> valueIterators = new ArrayList<Tree.ValueIterator>();

        findCandidatesForRevelation(editor, localModifiers, valueIterators);

        return !localModifiers.isEmpty() || !valueIterators.isEmpty();
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        CeylonEditor editor = (CeylonEditor) getCurrentEditor();
        Tree.CompilationUnit rootNode = editor.getParseController().getRootNode();

        Set<Declaration> imports = new HashSet<Declaration>();
        List<Tree.LocalModifier> localModifiers = new ArrayList<Tree.LocalModifier>();
        List<Tree.ValueIterator> valueIterators = new ArrayList<Tree.ValueIterator>();

        findCandidatesForRevelation(editor, localModifiers, valueIterators);

        if( !localModifiers.isEmpty() || !valueIterators.isEmpty() ) {
            TextChange tfc = new TextFileChange("Reveal Inferred Types", 
            		((IFileEditorInput) editor.getEditorInput()).getFile());
            tfc.setEdit(new MultiTextEdit());
            tfc.initializeValidationData(null);

            for (Tree.LocalModifier localModifier : localModifiers) {
                if( localModifier.getStartIndex() != null && 
                		localModifier.getTypeModel() != null ) {
                    ProducedType pt = localModifier.getTypeModel();
                    tfc.addEdit(new ReplaceEdit(
                            localModifier.getStartIndex(), 
                            localModifier.getText().length(), 
                            pt.getProducedTypeName()));
                    importType(imports, pt, rootNode);
                }
            }

            for (Tree.ValueIterator valueIterator : valueIterators) {
                Tree.Variable variable = valueIterator.getVariable();
                if( variable != null 
                        && variable.getStartIndex() != null 
                        && variable.getType() != null 
                        && variable.getType().getTypeModel() != null ) {
                    ProducedType pt = variable.getType().getTypeModel();
                    tfc.addEdit(new InsertEdit(
                            variable.getStartIndex(), 
                            pt.getProducedTypeName() + " "));
                    importType(imports,  variable.getType().getTypeModel(), rootNode);
                }
            }
            
            try {
                IDocument doc = tfc.getCurrentDocument(null);
    			applyImports(tfc, imports, rootNode, doc);

                PerformChangeOperation changeOperation = new PerformChangeOperation(tfc);
                changeOperation.setUndoManager(getUndoManager(), "Reveal Inferred Types");
                getWorkspace().run(changeOperation, new NullProgressMonitor());
            }
            catch (CoreException ce) {
                throw new ExecutionException("Error reveal inferred types", ce);
            }
        }

        return null;
    }

    private void findCandidatesForRevelation(CeylonEditor editor, 
    		final List<Tree.LocalModifier> localModifiers, 
    		final List<Tree.ValueIterator> valueIterators) {
        if (editor != null && 
                editor.getParseController() != null && 
                editor.getParseController().getRootNode() != null && 
                editor.getSelectionProvider() != null && 
                editor.getSelectionProvider().getSelection() != null) {

            final ITextSelection selection = (ITextSelection) editor.getSelectionProvider().getSelection();
            final int selectionStart = selection.getOffset();
            final int selectionStop = selection.getOffset() + selection.getLength();

            editor.getParseController().getRootNode().visit(new Visitor() {

                @Override
                public void visit(Tree.TypedDeclaration typedDeclaration) {
                    if( isInSelection(typedDeclaration) ) {
                        Tree.Type type = typedDeclaration.getType();
                        if( type instanceof Tree.LocalModifier && type.getToken() != null ) {
                            localModifiers.add((LocalModifier) type);                                
                        }
                    }
                    super.visit(typedDeclaration);
                }

                @Override
                public void visit(Tree.ValueIterator valueIterator) {
                    if (isInSelection(valueIterator)) {
                        Tree.Variable variable = valueIterator.getVariable();
                        Tree.Type type = variable.getType();
                        if (type instanceof Tree.ValueModifier) {
                            valueIterators.add(valueIterator);
                        }
                    }
                    super.visit(valueIterator);
                }

                private boolean isInSelection(Node node) {
                    Integer startIndex = node.getStartIndex();
                    Integer stopIndex = node.getStopIndex();
                    if (startIndex != null && stopIndex != null) {
                        if (selection.getLength() == 0 /* if selection is empty, process whole file */ ||
                           (startIndex >= selectionStart && stopIndex <= selectionStop) ) {
                            return true;
                        }
                    }
                    return false;
                }

            });
        }
    }

}