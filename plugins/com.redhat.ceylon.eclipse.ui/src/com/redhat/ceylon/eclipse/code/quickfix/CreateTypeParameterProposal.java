package com.redhat.ceylon.eclipse.code.quickfix;

import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.ADD;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.getIdentifyingNode;
import static com.redhat.ceylon.eclipse.code.quickfix.AddConstraintSatisfiesProposal.createMissingBoundsText;
import static com.redhat.ceylon.eclipse.code.quickfix.CeylonQuickFixAssistant.applyImports;
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

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Generic;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.editor.Util;
import com.redhat.ceylon.eclipse.util.FindBodyContainerVisitor;

class CreateTypeParameterProposal extends ChangeCorrectionProposal {
    
    final int offset;
    final IFile file;
    final int length;
    
    CreateTypeParameterProposal(String def, String desc, 
    		Image image, String name, int offset, IFile file, 
    		TextFileChange change) {
        super(desc, change, image);
        this.offset = offset+1;
        this.length = name.length();
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
    
    private static void addCreateTypeParameterProposal(Collection<ICompletionProposal> proposals, 
            String def, String name, Image image, Declaration dec, PhasedUnit unit,
            Tree.Declaration decNode, int offset, String constraints) {
        IFile file = getFile(unit);
        TextFileChange change = new TextFileChange("Add Parameter", file);
        change.setEdit(new MultiTextEdit());
        IDocument doc = getDocument(change);
        HashSet<Declaration> decs = new HashSet<Declaration>();
		CompilationUnit cu = unit.getCompilationUnit();
		int il = applyImports(change, decs, cu, doc);
        change.addEdit(new InsertEdit(offset, def));
        if (constraints!=null) {
        	int loc = getConstraintLoc(decNode);
        	if (loc>=0) {
        		change.addEdit(new InsertEdit(loc, constraints));
        	}
        }
        proposals.add(new CreateTypeParameterProposal(def, 
                "Add type parameter '" + name + "'" + 
                		" to '" + dec.getName() + "'", 
                image, name, offset+il, file, change));
    }

    private static int getConstraintLoc(Tree.Declaration decNode) {
        if( decNode instanceof Tree.ClassDefinition ) {
            Tree.ClassDefinition classDefinition = (Tree.ClassDefinition) decNode;
            return classDefinition.getClassBody().getStartIndex();
        }
        else if( decNode instanceof Tree.InterfaceDefinition ) {
            Tree.InterfaceDefinition interfaceDefinition = (Tree.InterfaceDefinition) decNode;
            return interfaceDefinition.getInterfaceBody().getStartIndex();
        }
        else if( decNode instanceof Tree.MethodDefinition ) {
            Tree.MethodDefinition methodDefinition = (Tree.MethodDefinition) decNode;
            return methodDefinition.getBlock().getStartIndex();
        }
        else if( decNode instanceof Tree.ClassDeclaration ) {
            Tree.ClassDeclaration classDefinition = (Tree.ClassDeclaration) decNode;
            return classDefinition.getClassSpecifier().getStartIndex();
        }
        else if( decNode instanceof Tree.InterfaceDefinition ) {
            Tree.InterfaceDeclaration interfaceDefinition = (Tree.InterfaceDeclaration) decNode;
            return interfaceDefinition.getTypeSpecifier().getStartIndex();
        }
        else if( decNode instanceof Tree.MethodDeclaration ) {
            Tree.MethodDeclaration methodDefinition = (Tree.MethodDeclaration) decNode;
            return methodDefinition.getSpecifierExpression().getStartIndex();
        }
        else {
        	return -1;
        }
    }
    
    static void addCreateTypeParameterProposal(Collection<ICompletionProposal> proposals, 
    		IProject project, Tree.CompilationUnit cu, final Tree.BaseType node, 
    		String brokenName) {
    	FindBodyContainerVisitor fcv = new FindBodyContainerVisitor(node);
        fcv.visit(cu);
        Tree.Declaration decl = fcv.getDeclaration();
        Declaration d = decl==null ? null : decl.getDeclarationModel();
		if (d == null || d.isActual() ||
                !(d instanceof Method || d instanceof ClassOrInterface)) {
            return;
        }
        
        Tree.TypeParameterList paramList = getTypeParameters(decl);
        String paramDef;
        int offset;
        //TODO: add bounds as default type arg?
        if (paramList != null) {
            paramDef = ", " + brokenName;
            offset = paramList.getStopIndex();
        }
        else {
        	paramDef = "<" + brokenName + ">";
        	offset = getIdentifyingNode(decl).getStopIndex()+1;
        }
        
        class FindTypeParameterConstraintVisitor extends Visitor {
        	List<ProducedType> result;
        	@Override
        	public void visit(Tree.SimpleType that) {
        	    super.visit(that);
        	    List<TypeParameter> tps = that.getDeclarationModel().getTypeParameters();
        	    Tree.TypeArgumentList tal = that.getTypeArgumentList();
        	    if (tal!=null) {
        	    	List<Tree.Type> tas = tal.getTypes();
        	    	for (int i=0; i<tas.size(); i++) {
        	    		if (tas.get(i)==node) {
        	    			result = tps.get(i).getSatisfiedTypes();
        	    		}
        	    	}
        	    }
        	}
        	@Override
        	public void visit(Tree.StaticMemberOrTypeExpression that) {
        	    super.visit(that);
        	    Declaration d = that.getDeclaration();
        	    if (d instanceof Generic) {
        	    	List<TypeParameter> tps = ((Generic) d).getTypeParameters();
        	    	Tree.TypeArguments tal = that.getTypeArguments();
        	    	if (tal instanceof Tree.TypeArgumentList) {
        	    		List<Tree.Type> tas = ((Tree.TypeArgumentList) tal).getTypes();
        	    		for (int i=0; i<tas.size(); i++) {
        	    			if (tas.get(i)==node) {
        	    				result = tps.get(i).getSatisfiedTypes();
        	    			}
        	    		}
        	    	}
        	    }
        	}
        }
        FindTypeParameterConstraintVisitor ftpcv = 
        		new FindTypeParameterConstraintVisitor();
        ftpcv.visit(cu);
        String constraints;
        if (ftpcv.result==null) {
        	constraints = null;
        }
        else {
        	String bounds = createMissingBoundsText(ftpcv.result);
        	if (bounds.isEmpty()) {
        		constraints = null;
        	}
        	else {
        		constraints = "given " + brokenName + 
        				" satisfies " + bounds + " ";
        	}
        }
        
        for (PhasedUnit unit : getUnits(project)) {
        	if (unit.getUnit().equals(cu.getUnit())) {
        		addCreateTypeParameterProposal(proposals, 
        				paramDef, brokenName, ADD, d, unit, 
        				decl, offset, constraints);
        		break;
        	}
        }

    }
    
    private static Tree.TypeParameterList getTypeParameters(Tree.Declaration decl) {
    	if (decl instanceof Tree.ClassOrInterface) {
    		return ((Tree.ClassOrInterface) decl).getTypeParameterList();
    	}
    	else if (decl instanceof Tree.AnyMethod) {
    		return ((Tree.AnyMethod) decl).getTypeParameterList();
    	}
    	return null;
    }
    
        
}