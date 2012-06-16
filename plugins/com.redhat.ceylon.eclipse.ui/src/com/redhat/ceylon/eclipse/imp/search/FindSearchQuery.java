package com.redhat.ceylon.eclipse.imp.search;

import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.eclipse.imp.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.imp.core.CeylonReferenceResolver;
import com.redhat.ceylon.eclipse.imp.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.imp.editor.Util;
import com.redhat.ceylon.eclipse.imp.parser.CeylonParseController;

abstract class FindSearchQuery implements ISearchQuery {
	
	private final Declaration referencedDeclaration;
	//private final IProject project;
	private AbstractTextSearchResult result = new CeylonSearchResult(this);
	private int count = 0;
	private IWorkbenchPage page;

	FindSearchQuery(Declaration referencedDeclaration, IProject project) {
		this.referencedDeclaration = referencedDeclaration;
		//this.project = project;
		this.page = Util.getActivePage();
	}

	@Override
	public IStatus run(IProgressMonitor monitor) throws OperationCanceledException {
	    //List<PhasedUnit> units = CeylonBuilder.getUnits(project);
	    //if (units==null) units = CeylonBuilder.getUnits();
	    
	    List<PhasedUnit> units = CeylonBuilder.getUnits();
        for (PhasedUnit pu: units) {
	        CompilationUnit cu = getRootNode(pu);
            Set<Node> nodes = getNodes(cu);
	        //TODO: should really add these as we find them:
            for (Node node: nodes) {
    			FindContainerVisitor fcv = new FindContainerVisitor(node);
    			cu.visit(fcv);
                if (node.getToken()==null) {
                    //a synthetic node inserted in the tree
                }
                else {
                    node = CeylonReferenceResolver.getIdentifyingNode(node);
        			result.addMatch(new CeylonSearchMatch(fcv.getDeclaration(), 
        			        CeylonBuilder.getFile(pu), 
        					node.getStartIndex(), 
        					node.getStopIndex()-node.getStartIndex()+1,
        					node.getToken()));
                }
    		}
    		count+=nodes.size();
        }
		return Status.OK_STATUS;
	}

    Tree.CompilationUnit getRootNode(PhasedUnit pu) {
        for (IEditorPart editor: page.getDirtyEditors()) {
            if (editor instanceof CeylonEditor) {
                CeylonParseController cpc = ((CeylonEditor)editor).getParseController();
                if ( /*editor.isDirty() &&*/
                        pu.getUnit().equals(cpc.getRootNode().getUnit()) ) {
                    return cpc.getRootNode();
                }
            }
        }
        return pu.getCompilationUnit();
    }
    
    protected abstract Set<Node> getNodes(Tree.CompilationUnit cu);
    
    protected abstract String labelString();

	@Override
	public ISearchResult getSearchResult() {
		return result;
	}

	@Override
	public String getLabel() {
		return "Displaying " + count + " " + labelString() + 
		        " '" + referencedDeclaration.getName() + "'";
	}

	@Override
	public boolean canRunInBackground() {
		return true;
	}

	@Override
	public boolean canRerun() {
		return false;
	}
}