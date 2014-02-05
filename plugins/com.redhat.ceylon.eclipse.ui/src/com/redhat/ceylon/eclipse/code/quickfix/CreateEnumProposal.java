package com.redhat.ceylon.eclipse.code.quickfix;

import static com.redhat.ceylon.eclipse.code.editor.CeylonAutoEditStrategy.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.ATTRIBUTE;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.INTERFACE;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.getIdentifyingNode;
import static com.redhat.ceylon.eclipse.code.quickfix.CeylonQuickFixAssistant.findDeclaration;
import static com.redhat.ceylon.eclipse.code.quickfix.CeylonQuickFixAssistant.getIndent;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getFile;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getUnits;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.swt.graphics.Image;
import org.eclipse.text.edits.InsertEdit;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.Util;
import com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider;

class CreateEnumProposal extends ChangeCorrectionProposal {
    
    final int offset;
    final IFile file;
    final int length;
    
    CreateEnumProposal(String def, String desc, Image image, 
            int offset, IFile file, TextFileChange change) {
        super(desc, change, image);
        int loc = def.indexOf("{}")+1;
        length = 0;
        this.offset = offset + loc;
        this.file=file;
    }
    
    @Override
    public void apply(IDocument document) {
        super.apply(document);
        Util.gotoLocation(file, offset, length);
    }

    static IDocument getDocument(TextFileChange change) {
        try {
            return change.getCurrentDocument(null);
        }
        catch (CoreException e) {
            throw new RuntimeException(e);
        }
    }

    static void addCreateEnumProposal(Tree.CompilationUnit cu, Node node, 
            ProblemLocation problem, Collection<ICompletionProposal> proposals, 
            IProject project, TypeChecker tc, IFile file) {
            Node idn = getIdentifyingNode(node);
            if (idn==null) return;
    		String brokenName = idn.getText();
            if (brokenName.isEmpty()) return;
            Tree.Declaration dec = findDeclaration(cu, node);
            if (dec instanceof Tree.ClassDefinition) {
                Tree.ClassDefinition cd = (Tree.ClassDefinition) dec;
                if (cd.getCaseTypes()!=null) {
                    if (cd.getCaseTypes().getTypes().contains(node)) {
                        addCreateEnumProposal(proposals, project, 
                                "class " + brokenName + parameters(cd.getTypeParameterList()) +
                                    parameters(cd.getParameterList()) +
                                    " extends " + cd.getDeclarationModel().getName() + 
                                    parameters(cd.getTypeParameterList()) + 
                                    arguments(cd.getParameterList()) + " {}", 
                                "class '"+ brokenName + parameters(cd.getTypeParameterList()) +
                                parameters(cd.getParameterList()) + "'", 
                                CeylonLabelProvider.CLASS, cu, cd);
                    }
                    if (cd.getCaseTypes().getBaseMemberExpressions().contains(node)) {
                        addCreateEnumProposal(proposals, project, 
                                "object " + brokenName + 
                                    " extends " + cd.getDeclarationModel().getName() + 
                                    parameters(cd.getTypeParameterList()) + 
                                    arguments(cd.getParameterList()) + " {}", 
                                "object '"+ brokenName + "'", 
                                ATTRIBUTE, cu, cd);
                    }
                }
            }
            if (dec instanceof Tree.InterfaceDefinition) {
                Tree.InterfaceDefinition cd = (Tree.InterfaceDefinition) dec;
                if (cd.getCaseTypes()!=null) {
                    if (cd.getCaseTypes().getTypes().contains(node)) {
                        addCreateEnumProposal(proposals, project, 
                                "interface " + brokenName + parameters(cd.getTypeParameterList()) +
                                    " satisfies " + cd.getDeclarationModel().getName() + 
                                    parameters(cd.getTypeParameterList()) + " {}", 
                                "interface '"+ brokenName + parameters(cd.getTypeParameterList()) +  "'", 
                                INTERFACE, cu, cd);
                    }
                    if (cd.getCaseTypes().getBaseMemberExpressions().contains(node)) {
                        addCreateEnumProposal(proposals, project, 
                                "object " + brokenName + 
                                    " satisfies " + cd.getDeclarationModel().getName() + 
                                    parameters(cd.getTypeParameterList()) + " {}", 
                                "object '"+ brokenName + "'", 
                                ATTRIBUTE, cu, cd);
                    }
                }
            }
        }
        
    private static void addCreateEnumProposal(Collection<ICompletionProposal> proposals, 
    		String def, String desc, Image image, PhasedUnit unit, 
    		Tree.Statement statement) {
        IFile file = getFile(unit);
        TextFileChange change = new TextFileChange("Create Enumerated", file);
        IDocument doc = getDocument(change);
        String indent = getIndent(statement, doc);
        String s = indent + def + getDefaultLineDelimiter(doc);
        int offset = statement.getStopIndex()+2;
        if (offset>doc.getLength()) {
            offset = doc.getLength();
            s = getDefaultLineDelimiter(doc) + s;
        }
        change.setEdit(new InsertEdit(offset, s));
        proposals.add(new CreateEnumProposal(def, 
        		"Create enumerated " + desc, 
                image, offset, file, change));
    }

        private static void addCreateEnumProposal(Collection<ICompletionProposal> proposals,
                IProject project, String def, String desc, Image image, 
                Tree.CompilationUnit cu, Tree.TypeDeclaration cd) {
            for (PhasedUnit unit: getUnits(project)) {
                if (unit.getUnit().equals(cu.getUnit())) {
                    addCreateEnumProposal(proposals, def, desc, image, unit, cd);
                    break;
                }
            }
        }

        private static String parameters(Tree.ParameterList pl) {
            StringBuilder result = new StringBuilder();
            if (pl==null ||
                    pl.getParameters().isEmpty()) {
                result.append("()");
            }
            else {
                result.append("(");
                int len = pl.getParameters().size(), i=0;
                for (Tree.Parameter p: pl.getParameters()) {
                    if (p!=null) {
                        if (p instanceof Tree.ParameterDeclaration) {
                            Tree.TypedDeclaration td = 
                            		((Tree.ParameterDeclaration) p).getTypedDeclaration();
                            result.append(td.getType().getTypeModel().getProducedTypeName()) 
                                    .append(" ")
                                    .append(td.getIdentifier().getText());
                        }
                        else if (p instanceof Tree.InitializerParameter) {
                            result.append(p.getParameterModel().getType().getProducedTypeName()) 
                                .append(" ")
                                .append(((Tree.InitializerParameter) p).getIdentifier().getText());
                        }
                        //TODO: easy to add back in:
                        /*if (p instanceof Tree.FunctionalParameterDeclaration) {
                            Tree.FunctionalParameterDeclaration fp = (Tree.FunctionalParameterDeclaration) p;
                            for (Tree.ParameterList ipl: fp.getParameterLists()) {
                                parameters(ipl, label);
                            }
                        }*/
                    }
                    if (++i<len) result.append(", ");
                }
                result.append(")");
            }
            return result.toString();
        }
        
        private static String parameters(Tree.TypeParameterList tpl) {
            StringBuilder result = new StringBuilder();
            if (tpl!=null &&
                    !tpl.getTypeParameterDeclarations().isEmpty()) {
                result.append("<");
                int len = tpl.getTypeParameterDeclarations().size(), i=0;
                for (Tree.TypeParameterDeclaration p: tpl.getTypeParameterDeclarations()) {
                    result.append(p.getIdentifier().getText());
                    if (++i<len) result.append(", ");
                }
                result.append(">");
            }
            return result.toString();
        }
        
        private static String arguments(Tree.ParameterList pl) {
            StringBuilder result = new StringBuilder();
            if (pl==null ||
                    pl.getParameters().isEmpty()) {
                result.append("()");
            }
            else {
                result.append("(");
                int len = pl.getParameters().size(), i=0;
                for (Tree.Parameter p: pl.getParameters()) {
                    if (p!=null) {
                        Tree.Identifier id;
                        if (p instanceof Tree.InitializerParameter) {
                            id = ((Tree.InitializerParameter) p).getIdentifier();
                        }
                        else if (p instanceof Tree.ParameterDeclaration) {
                            id = ((Tree.ParameterDeclaration) p).getTypedDeclaration().getIdentifier();
                        }
                        else {
                            continue;
                        }
                        result.append(id.getText());
                        //TODO: easy to add back in:
                        /*if (p instanceof Tree.FunctionalParameterDeclaration) {
                            Tree.FunctionalParameterDeclaration fp = (Tree.FunctionalParameterDeclaration) p;
                            for (Tree.ParameterList ipl: fp.getParameterLists()) {
                                parameters(ipl, label);
                            }
                        }*/
                    }
                    if (++i<len) result.append(", ");
                }
                result.append(")");
            }
            return result.toString();
        }
        
}