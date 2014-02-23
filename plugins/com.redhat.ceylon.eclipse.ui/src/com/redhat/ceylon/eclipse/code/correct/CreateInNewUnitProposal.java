package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getCurrentEditor;
import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.performChange;
import static com.redhat.ceylon.eclipse.code.imports.CleanImportsHandler.imports;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.gotoLocation;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.eclipse.code.move.CreateUnitChange;
import com.redhat.ceylon.eclipse.code.wizard.SelectNewUnitWizard;

class CreateInNewUnitProposal implements ICompletionProposal,
        ICompletionProposalExtension6 {
    
    private final IFile file;
    private final DefinitionGenerator dg;

    CreateInNewUnitProposal(IFile file, DefinitionGenerator dg) {
        this.file = file;
        this.dg = dg;
    }

    @Override
    public Point getSelection(IDocument doc) {
        return null;
    }

    @Override
    public Image getImage() {
        return dg.image;
    }

    @Override
    public String getDisplayString() {
        return "Create toplevel " + dg.desc + " in new unit";
    }

    @Override
    public IContextInformation getContextInformation() {
        return null;
    }

    @Override
    public String getAdditionalProposalInfo() {
        return null;
    }

    @Override
    public void apply(IDocument doc) {
        SelectNewUnitWizard w = new SelectNewUnitWizard("Create in New Unit", 
                "Create a new Ceylon compilation unit with the missing declaration.",
                dg.brokenName);
        if (w.open(file)) {
            CreateUnitChange change = new CreateUnitChange(w.getFile(), 
                    w.includePreamble(), getText(doc), w.getProject());
            try {
                performChange(getCurrentEditor(), doc, change, 
                        "Move to New Unit");
                gotoLocation(w.getFile().getFullPath(), 0);
            }
            catch (CoreException e) {
                e.printStackTrace();
            }
        }
    }

    private String getText(IDocument doc) {
        String delim = getDefaultLineDelimiter(doc);
        String definition = dg.generate("", delim);
        List<Declaration> imports = new ArrayList<Declaration>();
        resolveImports(imports, dg.returnType);
        if (dg.parameters!=null) {
            resolveImports(imports, dg.parameters.values());
        }
        String imps = imports(imports, doc);
        if (!imps.isEmpty()) {
            definition = imps + delim + delim + definition;
        }
        return definition;
    }

    static void addCreateToplevelProposal(Collection<ICompletionProposal> proposals, 
            DefinitionGenerator dg, IFile file) {        
        proposals.add(new CreateInNewUnitProposal(file, dg));
    }
    
    private static void resolveImports(List<Declaration> imports, 
            Collection<ProducedType> producedTypes) {
        if (producedTypes!=null) {
            for (ProducedType pt : producedTypes) {
                resolveImports(imports, pt);
            }
        }
    }

    private static void resolveImports(List<Declaration> imports, ProducedType pt) {
        if (pt != null) {
            if (pt.getDeclaration() instanceof UnionType) {
                resolveImports(imports, pt.getCaseTypes());
            }
            else if (pt.getDeclaration() instanceof IntersectionType) {
                resolveImports(imports, pt.getSatisfiedTypes());
            }
            else if (pt.getDeclaration() instanceof TypeParameter) {
                TypeParameter typeParam = (TypeParameter) pt.getDeclaration();
                if (typeParam.isConstrained()) {
                    resolveImports(imports, typeParam.getCaseTypes());
                    resolveImports(imports, typeParam.getSatisfiedTypes());
                }
                if (typeParam.isDefaulted()) {
                    resolveImports(imports, typeParam.getDefaultTypeArgument());
                }
            } else {
                resolveImports(imports, pt.getTypeArgumentList());
                Package p = pt.getDeclaration().getUnit().getPackage();
                if (!p.getQualifiedNameString().isEmpty() && 
                    !p.getQualifiedNameString().equals(Module.LANGUAGE_MODULE_NAME)) {
                    if (!imports.contains(pt.getDeclaration())) {
                        imports.add(pt.getDeclaration());
                    }
                }
            }
        }
    }

    @Override
    public StyledString getStyledDisplayString() {
        return CorrectionUtil.styleProposal(getDisplayString());
    }
}