package com.redhat.ceylon.eclipse.code.quickfix;

import static com.redhat.ceylon.eclipse.code.editor.CeylonAutoEditStrategy.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.code.propose.CeylonContentProposer.getRefinedProducedReference;
import static com.redhat.ceylon.eclipse.code.propose.CeylonContentProposer.getRefinementTextFor;
import static com.redhat.ceylon.eclipse.code.quickfix.Util.getSelectedNode;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.DeclarationWithProximity;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedTypedReference;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.editor.Util;
import com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider;
import com.redhat.ceylon.eclipse.code.propose.CeylonContentProposer;
import com.redhat.ceylon.eclipse.code.propose.RequiredTypeVisitor;
import com.redhat.ceylon.eclipse.code.wizard.NewUnitWizard;

class CreateSubtypeInNewUnitProposal implements ICompletionProposal, 
		ICompletionProposalExtension6 {

    private CeylonEditor editor;
    private ProducedType type;
    
    public CreateSubtypeInNewUnitProposal(CeylonEditor editor, 
    		ProducedType type) {
        this.editor = editor;
        this.type = type;
    }
    
    @Override
    public Point getSelection(IDocument doc) {
    	return null;
    }

    @Override
    public Image getImage() {
    	return CeylonLabelProvider.MOVE;
    }

    @Override
    public String getDisplayString() {
    	return "Create subtype of '" + 
    	        type.getProducedTypeName() + 
    	        "' in new unit";
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
        TypeDeclaration td = type.getDeclaration();
        Unit unit = editor.getParseController().getRootNode()
                .getUnit();
        CreateSubtype cs = subtypeDeclaration(type, 
        		unit.getPackage(), unit, false, doc);
        NewUnitWizard.open(cs.getImports()+cs.getDefinition(), 
                Util.getFile(editor.getEditorInput()), 
        		"My" + td.getName().replace("&", "").replace("<", "").replace(">", ""), 
        		"Create Subtype", "Create a new Ceylon compilation unit containing the new class.");
    }

    public static class CreateSubtype {
		private String definition;
		private String imports;
		private Set<ProducedType> importedTypes;
		CreateSubtype(String definition, String imports,
				Set<ProducedType> importedTypes) {
			this.definition = definition;
			this.imports = imports;
			this.importedTypes = importedTypes;
		}
		public String getDefinition() {
			return definition;
		}
		public String getImports() {
			return imports;
		}
		public Set<ProducedType> getImportedTypes() {
			return importedTypes;
		}
	}
	
    public static CreateSubtype subtypeDeclaration(ProducedType type, 
    		Package pkg, Unit unit, boolean object, IDocument doc) {
        TypeDeclaration td = type.getDeclaration();
        StringBuilder def = new StringBuilder();
        if (object) {
            def.append("object $className");
        }
        else {
            def.append("class $className");
            boolean first = true;
            for (ProducedType ta: type.getTypeArgumentList()) {
                if (ta!=null && ta.getDeclaration() instanceof TypeParameter) {
                    if (first) {
                        def.append("<");
                        first=false;
                    }
                    else {
                        def.append(", ");
                    }
                    def.append(ta.getDeclaration().getName());
                }
            }
            if (!first) def.append(">");
            boolean foundClass = false;
            if (td instanceof IntersectionType) {
                for (ProducedType pt: td.getSatisfiedTypes()) {
                    if (pt.getDeclaration() instanceof Class) {
                        foundClass = true;
                        appendParameters(pt, (Class) pt.getDeclaration(), unit, def);
                        break;
                    }
                }
            }
            if (td instanceof Class) {
                foundClass = true;
            	appendParameters(type, (Class) td, unit, def);
            }
            if (!foundClass) {
            	def.append("()");
            }
        }
        Set<ProducedType> allTypes = new HashSet<ProducedType>();
        if (td instanceof IntersectionType) {
        	allTypes.addAll(td.getSatisfiedTypes());
            for (ProducedType pt: td.getSatisfiedTypes()) {
                if (pt.getDeclaration() instanceof Class) {
                    appendClass(pt, (Class) pt.getDeclaration(), def);
                }
            }
            boolean first=true;
            for (ProducedType pt: td.getSatisfiedTypes()) {
                if (!(pt.getDeclaration() instanceof Class)) {
                    appendInterface(pt, pt.getDeclaration(), def, first);
                    first=false;
                }
            }
        }
        else if (td instanceof Class) {
        	allTypes.add(type);
        	appendClass(type, (Class) td, def);
        }
        else {
        	allTypes.add(type);
        	appendInterface(type, td, def, true);
        }
        def.append(" {").append(getDefaultLineDelimiter(doc));
        for (DeclarationWithProximity dwp: td.getMatchingMemberDeclarations(null, "", 0).values()) {
        	Declaration d = dwp.getDeclaration();
        	if (d.isFormal() /*&& td.isInheritedFromSupertype(d)*/) {
        		if (d instanceof ClassOrInterface) {
        			allTypes.add(((ClassOrInterface) d).getType());
        		}
        		else if (d instanceof TypedDeclaration) {
        			allTypes.add(((TypedDeclaration) d).getType());
        		}
        		if (d instanceof Functional) {
        			for (ParameterList pl: ((Functional) d).getParameterLists()) {
        				for (Parameter p: pl.getParameters()) {
        					allTypes.add(p.getType());
        				}
        			}
        		}
        		ProducedReference pr = getRefinedProducedReference(type, d);
            	if (pr instanceof ProducedTypedReference) {
            		def.append("    ")
            			.append(getRefinementTextFor(d, pr, unit, false, "", false))
            			.append(getDefaultLineDelimiter(doc));
            	}
        	}
        }
        def.append("}");
        Set<Declaration> importedDecs = new HashSet<Declaration>();
        for (ProducedType pt: allTypes) {
        	addTypeDeclarations(importedDecs, pt);
        }
        Set<Package> importedPackages = new TreeSet<Package>(new Comparator<Package>() {
        	public int compare(Package p1, Package p2) {
        		return p1.getNameAsString().compareTo(p2.getNameAsString());
        	}
		});
        for (Declaration d: importedDecs) {
        	if (d.isToplevel()) {
        		importedPackages.add(d.getUnit().getPackage());
        	}
        }
        StringBuilder imports = new StringBuilder();
        for (Package p: importedPackages) {
        	if (!p.getNameAsString().isEmpty() && !p.equals(pkg) &&
        			!p.getNameAsString().equals(Module.LANGUAGE_MODULE_NAME)) {
        		imports.append("import ")
        		    .append(p.getNameAsString())
        		    .append(" { ");
        		for (Declaration d: importedDecs) {
        			if (d.getUnit().getPackage().equals(p)) {
        				imports.append(d.getName()).append(", ");
        			}
        		}
    			imports.setLength(imports.length()-2);
    			imports.append(" }")
    			        .append(getDefaultLineDelimiter(doc));
        	}
        }
        return new CreateSubtype(def.toString(), imports.toString(), allTypes);
    }

	private static void addTypeDeclarations(Set<Declaration> importedDecs,
			ProducedType pt) {
		Declaration d = pt.getDeclaration();
		if (d instanceof UnionType) {
			for (ProducedType t: ((UnionType) d).getCaseTypes()) {
				addTypeDeclarations(importedDecs, t);        			
			}
		}
		else if (d instanceof IntersectionType) {
			for (ProducedType t: ((IntersectionType) d).getSatisfiedTypes()) {
				addTypeDeclarations(importedDecs, t);        			
			}
		}
		else {
			importedDecs.add(d);
			for (ProducedType a: pt.getTypeArgumentList()) {
			    if (a!=null) {
			        addTypeDeclarations(importedDecs, a.getType());
			    }
			}
		}
	}

    public static void appendParameters(ProducedType type, Class c,
            Unit unit, StringBuilder def) {
        CeylonContentProposer.appendParameters(c, type, unit, def);
//        if (c.getParameterList()==null ||
//                c.getParameterList().getParameters().isEmpty()) {
//        	def.append("()");
//        }
//        else {
//        	def.append("(");
//        	for (Parameter p: c.getParameterList().getParameters()) {
//        		ProducedTypedReference ptr = type.getTypedParameter(p);
//        		if (ptr.getType()!=null && ptr.getFullType().isWellDefined()) {
//        			def.append(ptr.getFullType().getProducedTypeName());
//        		}
//        		else {
//        			def.append("Anything");
//        		}
//        		def.append(" ").append(p.getName()).append(", ");
//        	}
//        	def.setLength(def.length()-2);
//        	def.append(")");
//        }
    }

    private static void appendInterface(ProducedType type, TypeDeclaration td,
            StringBuilder def, boolean first) {
        def.append(first?" satisfies ":"&").append(td.getName());
        if (!td.getTypeParameters().isEmpty()) {
        	def.append("<");
        	for (ProducedType ta: type.getTypeArgumentList()) {
                if (ta!=null) {
                    def.append(ta.getProducedTypeName()).append(", ");
                }
        	}
        	def.setLength(def.length()-2);
        	def.append(">");
        }
    }

    private static void appendClass(ProducedType type, Class c, StringBuilder def) {
        def.append(" extends ").append(c.getName());
        if (!c.getTypeParameters().isEmpty()) {
        	def.append("<");
        	for (ProducedType ta: type.getTypeArgumentList()) {
        	    if (ta!=null) {
        	        def.append(ta.getProducedTypeName()).append(", ");
        	    }
        	}
        	def.setLength(def.length()-2);
        	def.append(">");
        }
        if (c.getParameterList()==null ||
                c.getParameterList().getParameters().isEmpty()) {
        	def.append("()");
        }
        else {
        	def.append("(");
        	for (Parameter p: c.getParameterList().getParameters()) {
        		def.append(p.getName()).append(", ");
        	}
        	def.setLength(def.length()-2);
        	def.append(")");
        }
    }

    public static ProducedType getType(CeylonEditor editor) {
        Tree.CompilationUnit cu = editor.getParseController().getRootNode();
        if (cu==null) return null;
        return getType(editor.getParseController().getRootNode(), 
                getSelectedNode(editor));
    }

    public static ProducedType getType(Tree.CompilationUnit cu, Node node) {
        if (node instanceof Tree.BaseType) {
        	return ((Tree.BaseType) node).getTypeModel();
        }
        /*else if (node instanceof Tree.BaseTypeExpression) {
            return ((Tree.BaseTypeExpression) node).getTypeModel();
        }
        else if (node instanceof Tree.ExtendedTypeExpression) {
            return ((Tree.ExtendedTypeExpression) node).getTypeModel();
        }*/
        else if (node instanceof Tree.ClassOrInterface) {
            ClassOrInterface ci = ((Tree.ClassOrInterface) node).getDeclarationModel();
            return ci==null ? null : ci.getType();
        }
        else if (node instanceof Tree.SpecifierOrInitializerExpression ||
                node instanceof Tree.Return || 
                node instanceof Tree.PositionalArgumentList) {
            RequiredTypeVisitor rtv = new RequiredTypeVisitor(node, null);
            rtv.visit(cu);
            return rtv.getType();
        }
        else {
            return null;
        }
    }
    
    public static boolean proposeSubtype(ProducedType type) {
    	TypeDeclaration dec = type.getDeclaration();
    	if (dec==null) {
    		return false;
    	}
    	else {
    		Unit unit = dec.getUnit();
    		return (dec instanceof ClassOrInterface ||
    					dec instanceof IntersectionType) &&
    				!dec.isFinal() &&
    				dec!=unit.getAnythingDeclaration() &&
    				dec!=unit.getObjectDeclaration() &&
    				dec!=unit.getBasicDeclaration() &&
    				dec!=unit.getIdentifiableDeclaration();
    	}
    }

    public static void add(Collection<ICompletionProposal> proposals, CeylonEditor editor) {
    	ProducedType type = getType(editor);
    	if (type!=null && proposeSubtype(type)) {
    		proposals.add(new CreateSubtypeInNewUnitProposal(editor, type));
    	}
    }

	@Override
	public StyledString getStyledDisplayString() {
		return ChangeCorrectionProposal.style(getDisplayString());
	}

}