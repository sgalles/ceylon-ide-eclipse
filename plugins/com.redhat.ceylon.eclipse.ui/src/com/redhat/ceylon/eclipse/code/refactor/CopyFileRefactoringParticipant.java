package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.compiler.typechecker.tree.Util.formatPath;
import static com.redhat.ceylon.eclipse.code.quickfix.ImportProposals.findImportNode;
import static com.redhat.ceylon.eclipse.code.quickfix.ImportProposals.importEdit;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getProjectTypeChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.CopyParticipant;
import org.eclipse.ltk.core.refactoring.participants.CopyProcessor;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.BaseMemberOrTypeExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.BaseType;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ImportMemberOrType;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ModuleDescriptor;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PackageDescriptor;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class CopyFileRefactoringParticipant extends CopyParticipant {

    private IFile file;

    @Override
    protected boolean initialize(Object element) {
        file= (IFile) element;
        return getProcessor() instanceof CopyProcessor && 
                getProjectTypeChecker(file.getProject())!=null &&
                file.getFileExtension().equals("ceylon");
    }
    
    @Override
    public String getName() {
        return "Copy file participant for Ceylon source";
    }

    @Override
    public RefactoringStatus checkConditions(IProgressMonitor pm,
            CheckConditionsContext context) throws OperationCanceledException {
        return new RefactoringStatus();
    }

    public Change createChange(IProgressMonitor pm) throws CoreException {
        IFolder dest = (IFolder) getArguments().getDestination();
        final String newName = dest.getProjectRelativePath()
                .removeFirstSegments(1).toPortableString()
                .replace('/', '.');
        IFile newFile = dest.getFile(file.getName());
        String relFilePath = file.getProjectRelativePath()
                .removeFirstSegments(1).toPortableString();
        String relPath = file.getProjectRelativePath()
                .removeFirstSegments(1).removeLastSegments(1)
                .toPortableString();
        final String oldName = relPath.replace('/', '.');
        final IProject project = file.getProject();

        TypeChecker tc = getProjectTypeChecker(project);
        if (tc==null) return null;
		PhasedUnit phasedUnit = tc.getPhasedUnitFromRelativePath(relFilePath);
        final List<ReplaceEdit> edits = new ArrayList<ReplaceEdit>();                
        final List<Declaration> declarations = phasedUnit.getDeclarations();
        final Map<Declaration,String> imports = new HashMap<Declaration,String>();
        phasedUnit.getCompilationUnit().visit(new Visitor() {
            @Override
            public void visit(ImportMemberOrType that) {
                super.visit(that);
                visitIt(that.getIdentifier(), that.getDeclarationModel());
            }
            @Override
            public void visit(BaseMemberOrTypeExpression that) {
                super.visit(that);
                visitIt(that.getIdentifier(), that.getDeclaration());
            }
            @Override
            public void visit(BaseType that) {
                super.visit(that);
                visitIt(that.getIdentifier(), that.getDeclarationModel());
            }
            @Override
            public void visit(ModuleDescriptor that) {
                super.visit(that);
                visitIt(that.getImportPath());
            }
            @Override
            public void visit(PackageDescriptor that) {
                super.visit(that);
                visitIt(that.getImportPath());
            }
            private void visitIt(Tree.ImportPath importPath) {
                if (formatPath(importPath.getIdentifiers()).equals(oldName)) {
                    edits.add(new ReplaceEdit(importPath.getStartIndex(), 
                            oldName.length(), newName));
                }
            }
            private void visitIt(Tree.Identifier id, Declaration dec) {
                if (dec!=null && !declarations.contains(dec)) {
                    String pn = dec.getUnit().getPackage().getNameAsString();
                    if (pn.equals(oldName) && !pn.isEmpty() && 
                            !pn.equals(Module.LANGUAGE_MODULE_NAME)) {
                        imports.put(dec, id.getText());
                    }
                }
            }
        });

        try {
            TextFileChange change = new TextFileChange(file.getName(), newFile);
            Tree.CompilationUnit cu = phasedUnit.getCompilationUnit();
            change.setEdit(new MultiTextEdit());
            for (ReplaceEdit edit: edits) {
                change.addEdit(edit);
            }
            if (!imports.isEmpty()) {
                List<InsertEdit> list = importEdit(cu, 
                        imports.keySet(), imports.values(), 
                        null, change.getCurrentDocument(null));
                for (TextEdit edit: list) {
                    change.addEdit(edit);
                }
            }
            Tree.Import toDelete = findImportNode(cu, newName);
            if (toDelete!=null) {
                change.addEdit(new DeleteEdit(toDelete.getStartIndex(), 
                        toDelete.getStopIndex()-toDelete.getStartIndex()+1));
            }
            if (change.getEdit().hasChildren()) {
                return change;
            }
        }
        catch (Exception e) { 
            e.printStackTrace(); 
        }

        return null;
    }

}
