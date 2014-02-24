package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.util.FindUtils.getContainer;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultIndent;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Indents.getIndent;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.ui.texteditor.ITextEditor;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PositionalArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class MakeReceiverRefactoring extends AbstractRefactoring {
    
    private final class MoveVisitor extends Visitor {
        private final TypeDeclaration newOwner;
        private final IDocument doc;
        private final Tree.Declaration fun;
        private final Declaration dec;
        private final TextChange tfc;

        private MoveVisitor(TypeDeclaration newOwner,
                IDocument doc, Declaration dec, Tree.Declaration fun,
                TextChange tfc) {
            this.newOwner = newOwner;
            this.doc = doc;
            this.dec = dec;
            this.fun = fun;
            this.tfc = tfc;
        }

        private String getDefinition() {
            final StringBuilder def = new StringBuilder(MakeReceiverRefactoring.this.toString(fun));
            new Visitor() {
                int offset=0;
                public void visit(Tree.Declaration that) {
                    if (that.getDeclarationModel().equals(dec)) {
                        int len = node.getStopIndex()-node.getStartIndex()+1;
                        int start = node.getStartIndex()-fun.getStartIndex()+offset;
                        def.replace(start, start+len, "");
                        offset-=len;
                        boolean deleted=false;
                        for (int i=start-1; i>=0; i--) {
                            if (!Character.isWhitespace(def.charAt(i))) {
                                if (def.charAt(i)==',') {
                                    def.delete(i, start);
                                    deleted = true;
                                    offset-=start-i;
                                }
                                break;
                            }
                        }
                        if (!deleted) {
                            boolean found=false;
                            for (int i=start; i<def.length(); i++) {
                                if (!Character.isWhitespace(def.charAt(i))) {
                                    if (!found && def.charAt(i)==',') {
                                        found = true;
                                    }
                                    else {
                                        def.delete(start, i);
                                        deleted = true;
                                        offset-=i-start;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    super.visit(that);
                }
                public void visit(Tree.BaseMemberOrTypeExpression that) {
                    if (that.getDeclaration().equals(dec)) {
                        int len = that.getStopIndex()-that.getStartIndex()+1;
                        int start = that.getStartIndex()-fun.getStartIndex()+offset;
                        String outerRef = fun.getDeclarationModel() instanceof Class ? 
                                "outer" : "this";
                        def.replace(start, start+len, outerRef);
                        offset+=outerRef.length()-len;
                    }
                    super.visit(that);
                }
            }.visit(fun);
            if (!fun.getDeclarationModel().isShared()) {
                def.insert(0, "shared ");
            }
            return def.toString();
        }

        private void insert(Tree.Body body, Tree.Declaration that) {
            String delim = getDefaultLineDelimiter(document);
            String originalIndent = delim+getIndent(fun, document);
            String text;
            List<Tree.Statement> sts = body.getStatements();
            int loc;
            if (sts.isEmpty()) {
                String outerIndent = delim + getIndent(that,doc);
                String newIndent = outerIndent + getDefaultIndent();
                String def = getDefinition()
                        .replaceAll(originalIndent, newIndent);
                text = newIndent + def + outerIndent;
                loc = body.getStopIndex();
            }
            else {
                Tree.Statement st = sts.get(sts.size()-1);
                String newIndent = delim + getIndent(st, doc);
                String def = getDefinition()
                        .replaceAll(originalIndent, newIndent);
                text = newIndent + def;
                loc = st.getStopIndex()+1;
            }
            tfc.addEdit(new InsertEdit(loc, text));
        }

        @Override
        public void visit(Tree.ClassDefinition that) {
            super.visit(that);
            if (that.getDeclarationModel()
                    .equals(newOwner)) {
                insert(that.getClassBody(), that);
            }
        }

        @Override
        public void visit(Tree.InterfaceDefinition that) {
            super.visit(that);
            if (that.getDeclarationModel()
                    .equals(newOwner)) {
                insert(that.getInterfaceBody(), that);
            }
        }

        @Override
        public void visit(Tree.InvocationExpression that) {
            super.visit(that);
            Tree.Primary p = that.getPrimary();
            if (p instanceof Tree.BaseMemberOrTypeExpression) {
                if (((Tree.BaseMemberOrTypeExpression) p).getDeclaration()
                        .equals(fun.getDeclarationModel())) {
                    Tree.PositionalArgumentList pal = that.getPositionalArgumentList();
                    Tree.NamedArgumentList nal = that.getNamedArgumentList();
                    if (pal!=null) {
                        List<PositionalArgument> pas = pal.getPositionalArguments();
                        for (int i=0; i<pas.size(); i++) {
                            Tree.PositionalArgument arg = pas.get(i);
                            if (arg.getParameter().getModel().equals(dec)) {
                                tfc.addEdit(new InsertEdit(p.getStartIndex(), 
                                        MakeReceiverRefactoring.this.toString(arg) + "."));
                                int start = arg.getStartIndex();
                                Integer end = arg.getStopIndex()+1;
                                if (i>0) {
                                    int comma = pas.get(i-1).getStopIndex()+1;
                                    tfc.addEdit(new DeleteEdit(comma, end-comma));
                                }
                                else if (i<pas.size()-1) {
                                    int next = pas.get(i+1).getStartIndex();
                                    tfc.addEdit(new DeleteEdit(start, next-start));
                                }
                                else {
                                    tfc.addEdit(new DeleteEdit(start, end-start));
                                }
                            }
                        }
                    }
                    if (nal!=null) {
                        for (Tree.NamedArgument arg: nal.getNamedArguments()) {
                            if (arg.getParameter().getModel().equals(dec)) {
                                if (arg instanceof Tree.SpecifiedArgument) {
                                    Tree.Expression e = ((Tree.SpecifiedArgument) arg).getSpecifierExpression()
                                            .getExpression();
                                    tfc.addEdit(new InsertEdit(p.getStartIndex(), 
                                            MakeReceiverRefactoring.this.toString(e) + "."));
                                    tfc.addEdit(new DeleteEdit(arg.getStartIndex(), 
                                            arg.getStopIndex()-arg.getStartIndex()+1));
                                }
                                else {
                                    //TODO!!!!
                                }
                            }
                        }
                    }
                }
            }
        }
        
    }
    
    public MakeReceiverRefactoring(ITextEditor editor) {
        super(editor);
    }

    @Override
    boolean isEnabled() {
        if (node instanceof Tree.AttributeDeclaration && 
                project != null) {
            Value param = ((Tree.AttributeDeclaration) node).getDeclarationModel();
            if (param!=null && 
                    param.isParameter() && 
                    param.getInitializerParameter().getDeclaration().isToplevel()) {
                TypeDeclaration target = param.getTypeDeclaration();
                return target!=null &&
                        inSameProject(target) && 
                        (target instanceof Class || 
                         target instanceof Interface);
            }
        }
        return false;
    }
    
    public String getName() {
        return "Make Receiver";
    }

    public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        RefactoringStatus result = new RefactoringStatus();
        return result;
    }

    public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        return new RefactoringStatus();
    }

    public Change createChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        CompositeChange cc = new CompositeChange(getName());
        Value param = ((Tree.AttributeDeclaration) node).getDeclarationModel();
        TypeDeclaration target = param.getTypeDeclaration(); 
        Tree.Declaration fun = getContainer(param, rootNode);
//        Tree.ClassOrInterface newOwner = (Tree.ClassOrInterface) 
//                getReferencedNode(target, rootNode);
        
        for (PhasedUnit pu: getAllUnits()) {
            if (searchInFile(pu)) {
                TextFileChange pufc = newTextFileChange(pu);
                IDocument doc = pufc.getCurrentDocument(null);
                pufc.setEdit(new MultiTextEdit());
                if (fun.getUnit().equals(pu.getUnit())) {
                    deleteOld(pufc, fun);
                }
                new MoveVisitor(target, doc, param, fun, pufc)
                        .visit(pu.getCompilationUnit());
                if (pufc.getEdit().hasChildren()) {
                    cc.add(pufc);
                }
            }
        }
        if (searchInEditor()) {
            final TextChange tfc = newLocalChange();
            tfc.setEdit(new MultiTextEdit());
            deleteOld(tfc, fun);
            new MoveVisitor(target, document, param, fun, tfc)
                    .visit(rootNode);
            cc.add(tfc);
        }
        
        
        return cc;
    }

    private void deleteOld(final TextChange tfc, final Tree.Declaration fun) {
        tfc.addEdit(new DeleteEdit(fun.getStartIndex(), 
                fun.getStopIndex()-fun.getStartIndex()+1));
    }
    
}
