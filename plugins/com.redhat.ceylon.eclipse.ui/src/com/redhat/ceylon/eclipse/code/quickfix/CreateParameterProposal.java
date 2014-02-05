package com.redhat.ceylon.eclipse.code.quickfix;

import static com.redhat.ceylon.eclipse.code.editor.CeylonAutoEditStrategy.getDefaultIndent;
import static com.redhat.ceylon.eclipse.code.editor.CeylonAutoEditStrategy.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.ADD;
import static com.redhat.ceylon.eclipse.code.quickfix.CeylonQuickFixAssistant.applyImports;
import static com.redhat.ceylon.eclipse.code.quickfix.CeylonQuickFixAssistant.getBody;
import static com.redhat.ceylon.eclipse.code.quickfix.CeylonQuickFixAssistant.getIndent;
import static com.redhat.ceylon.eclipse.code.quickfix.CeylonQuickFixAssistant.getRootNode;
import static com.redhat.ceylon.eclipse.code.quickfix.CeylonQuickFixAssistant.importType;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getFile;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getUnits;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.swt.graphics.Image;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.eclipse.code.editor.Util;
import com.redhat.ceylon.eclipse.util.FindBodyContainerVisitor;
import com.redhat.ceylon.eclipse.util.FindDeclarationNodeVisitor;

class CreateParameterProposal extends ChangeCorrectionProposal {
    
    final int offset;
    final IFile file;
    final int length;
    
    CreateParameterProposal(String def, String desc, 
    		Image image, int offset, IFile file, 
    		TextFileChange change) {
        super(desc, change, image);
        int loc = def.indexOf("= nothing");
        if (loc<0) {
            loc = def.indexOf("= ");
            if (loc<0) {
                loc = def.indexOf("{}")+1;
                length=0;
            }
            else {
                loc += 2;
                length = def.length()-loc;
            }
        }
        else {
            loc += 2;
            length=7;
        }
        this.offset=offset + loc;
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
    
    private static void addCreateParameterProposal(Collection<ICompletionProposal> proposals, 
            String def, String desc, Image image, Declaration dec, PhasedUnit unit,
            Tree.Declaration decNode, Tree.ParameterList paramList, 
            ProducedType returnType) {
        IFile file = getFile(unit);
        TextFileChange change = new TextFileChange("Add Parameter", file);
        change.setEdit(new MultiTextEdit());
        IDocument doc = getDocument(change);
        int offset = paramList.getStopIndex();
        HashSet<Declaration> decs = new HashSet<Declaration>();
		CompilationUnit cu = unit.getCompilationUnit();
		importType(decs, returnType, cu);
		int il = applyImports(change, decs, cu, doc);
        change.addEdit(new InsertEdit(offset, def));
        proposals.add(new CreateParameterProposal(def, 
                "Add " + desc + " to '" + dec.getName() + "'", 
                image, offset+il, file, change));
    }

    private static void addCreateParameterAndAttributeProposal(Collection<ICompletionProposal> proposals, 
            String pdef, String adef, String desc, Image image, Declaration dec, PhasedUnit unit,
            Tree.Declaration decNode, Tree.ParameterList paramList, Tree.Body body, 
            ProducedType returnType) {
        IFile file = getFile(unit);
        TextFileChange change = new TextFileChange("Add Attribute", file);
        change.setEdit(new MultiTextEdit());
        int offset = paramList.getStopIndex();
        IDocument doc = getDocument(change);
        String indent;
        String indentAfter;
        int offset2;
        List<Tree.Statement> statements = body.getStatements();
        if (statements.isEmpty()) {
            indentAfter = getDefaultLineDelimiter(doc) + getIndent(decNode, doc);
            indent = indentAfter + getDefaultIndent();
            offset2 = body.getStartIndex()+1;
        }
        else {
            Tree.Statement statement = statements.get(statements.size()-1);
            indent = getDefaultLineDelimiter(doc) + getIndent(statement, doc);
            offset2 = statement.getStopIndex()+1;
            indentAfter = "";
        }
        HashSet<Declaration> decs = new HashSet<Declaration>();
		Tree.CompilationUnit cu = unit.getCompilationUnit();
		importType(decs, returnType, cu);
		int il = applyImports(change, decs, cu, doc);
        change.addEdit(new InsertEdit(offset, pdef));
        change.addEdit(new InsertEdit(offset2, indent+adef+indentAfter));
        proposals.add(new CreateParameterProposal(pdef, 
                "Add " + desc + " to '" + dec.getName() + "'", 
                image, offset+il, file, change));
    }

    static void addCreateParameterProposal(Collection<ICompletionProposal> proposals, 
    		IProject project, DefinitionGenerator dg) {
    	FindBodyContainerVisitor fcv = new FindBodyContainerVisitor(dg.node);
        fcv.visit(dg.rootNode);
        Tree.Declaration decl = fcv.getDeclaration();
        if (decl == null || 
                decl.getDeclarationModel() == null || 
                decl.getDeclarationModel().isActual()) {
            return;
        }
        
        Tree.ParameterList paramList = getParameters(decl);
        if (paramList != null) {
        	String def = dg.generate("", "");
        	//TODO: really ugly and fragile way to strip off the trailing ;
    		String paramDef = (paramList.getParameters().isEmpty() ? "" : ", ") + 
    				def.substring(0, def.length() - 1);
    		String paramDesc = "parameter '" + dg.brokenName + "'";
    		for (PhasedUnit unit : getUnits(project)) {
    			if (unit.getUnit().equals(dg.rootNode.getUnit())) {
    				addCreateParameterProposal(proposals, paramDef, paramDesc, ADD, 
    						decl.getDeclarationModel(), unit, decl, paramList, dg.returnType);
    				break;
    			}
    		}
        }
    }

    static void addCreateParameterProposals(Tree.CompilationUnit cu, Node node, 
            ProblemLocation problem, Collection<ICompletionProposal> proposals, 
            IProject project, TypeChecker tc, IFile file) {
        FindInvocationVisitor fav = new FindInvocationVisitor(node);
        fav.visit(cu);
        if (fav.result==null) return;
        Tree.Primary prim = fav.result.getPrimary();
        if (prim instanceof Tree.MemberOrTypeExpression) {
            ProducedReference pr = ((Tree.MemberOrTypeExpression) prim).getTarget();
            if (pr!=null) {
                Declaration d = pr.getDeclaration();
                ProducedType t=null;
                String n=null;
                if (node instanceof Tree.Term) {
                    t = ((Tree.Term) node).getTypeModel();
                    n = t.getDeclaration().getName();
                    if (n!=null) {
                        n = Character.toLowerCase(n.charAt(0)) + n.substring(1)
                                .replace("?", "").replace("[]", "");
                        if ("string".equals(n)) {
                            n = "text";
                        }
                    }
                }
                else if (node instanceof Tree.SpecifiedArgument) {
                	Tree.SpecifiedArgument sa = (Tree.SpecifiedArgument) node;
                	Tree.SpecifierExpression se = sa.getSpecifierExpression();
                    if (se!=null && se.getExpression()!=null) {
                        t = se.getExpression().getTypeModel();
                    }
                    n = sa.getIdentifier().getText();
                }
                else if (node instanceof Tree.TypedArgument) {
                	Tree.TypedArgument ta = (Tree.TypedArgument) node;
                    t = ta.getType().getTypeModel();
                    n = ta.getIdentifier().getText();
                }
                if (t!=null && n!=null) {
                    t = node.getUnit().denotableType(t);
                    String dv = defaultValue(prim.getUnit(), t);
                    String tn = t.getProducedTypeName();
                    String def = tn + " " + n + " = " + dv;
                    String desc = "parameter '" + n +"'";
                    addCreateParameterProposals(proposals, project, def, desc, d, t);
                    String pdef = n + " = " + dv;
                    String adef = tn + " " + n + ";";
                    String padesc = "attribute '" + n +"'";
                    addCreateParameterAndAttributeProposals(proposals, project, 
                            pdef, adef, padesc, d, t);
                }
            }
        }
    }

    static String defaultValue(Unit unit, ProducedType t) {
        if (t==null) {
            return "nothing";
        }
        String tn = t.getProducedTypeQualifiedName();
        if (tn.equals("ceylon.language::Boolean")) {
            return "false";
        }
        else if (tn.equals("ceylon.language::Integer")) {
            return "0";
        }
        else if (tn.equals("ceylon.language::Float")) {
            return "0.0";
        }
        else if (unit.isOptionalType(t)) {
            return "null";
        }
        else if (tn.equals("ceylon.language::String")) {
            return "\"\"";
        }
        else {
            return "nothing";
        }
    }
    
    private static Tree.ParameterList getParameters(Tree.Declaration decNode) {
        if (decNode instanceof Tree.AnyClass) {
            return ((Tree.AnyClass) decNode).getParameterList();
        }
        else if (decNode instanceof Tree.AnyMethod){
            List<Tree.ParameterList> pls = ((Tree.AnyMethod) decNode).getParameterLists();
            return pls.isEmpty() ? null : pls.get(0);
        }
        return null;
    }

    private static void addCreateParameterProposals(Collection<ICompletionProposal> proposals,
            IProject project, String def, String desc, Declaration typeDec, ProducedType t) {
        if (typeDec!=null && typeDec instanceof Functional) {
            for (PhasedUnit unit: getUnits(project)) {
                if (typeDec.getUnit().equals(unit.getUnit())) {
                    FindDeclarationNodeVisitor fdv = new FindDeclarationNodeVisitor(typeDec);
                    getRootNode(unit).visit(fdv);
                    Tree.Declaration decNode = fdv.getDeclarationNode();
                    Tree.ParameterList paramList = getParameters(decNode);
                    if (paramList!=null) {
                        if (!paramList.getParameters().isEmpty()) {
                            def = ", " + def;
                        }
                        addCreateParameterProposal(proposals, def, desc, 
                        		ADD, typeDec, unit, decNode, paramList, t);
                        break;
                    }
                }
            }
        }
    }

    private static void addCreateParameterAndAttributeProposals(Collection<ICompletionProposal> proposals,
            IProject project, String pdef, String adef, String desc, Declaration typeDec, ProducedType t) {
        if (typeDec!=null && typeDec instanceof ClassOrInterface) {
            for (PhasedUnit unit: getUnits(project)) {
                if (typeDec.getUnit().equals(unit.getUnit())) {
                    FindDeclarationNodeVisitor fdv = new FindDeclarationNodeVisitor(typeDec);
                    getRootNode(unit).visit(fdv);
                    Tree.Declaration decNode = fdv.getDeclarationNode();
                    Tree.ParameterList paramList = getParameters(decNode);
                    Tree.Body body = getBody(decNode);
                    if (body!=null && paramList!=null) {
                        if (!paramList.getParameters().isEmpty()) {
                            pdef = ", " + pdef;
                        }
                        addCreateParameterAndAttributeProposal(proposals, pdef, 
                                adef, desc, ADD, typeDec, unit, decNode, 
                                paramList, body, t);
                    }
                }
            }
        }
    }
        
}