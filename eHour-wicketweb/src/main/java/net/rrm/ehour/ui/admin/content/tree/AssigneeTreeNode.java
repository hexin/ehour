package net.rrm.ehour.ui.admin.content.tree;

import java.util.HashSet;
import java.util.Set;

import javax.swing.tree.DefaultMutableTreeNode;

import net.rrm.ehour.domain.DomainObject;

/**
 * Created on Feb 6, 2010 10:34:58 PM
 *
 * @author thies (www.te-con.nl)
 *
 */
public class AssigneeTreeNode<T extends DomainObject<?, ?>> extends DefaultMutableTreeNode
{
	private static final long serialVersionUID = -4757873193229912865L;

	private NodeType nodeType;
	
	public AssigneeTreeNode(T userObject, NodeType nodeType)
	{
		super(userObject);
		
		this.nodeType = nodeType;
	}

	/**
	 * Get selected objects in other tree. 
	 * Override to provide a meaningful list
	 * @return
	 */
	public Set<?> getSelectedNodeObjects()
	{
		return new HashSet<DomainObject<?, ?>>();
	}
	
	/**
	 * Get this node type
	 * @return
	 */
	public final NodeType getNodeType()
	{
		return nodeType;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public String toString()
	{
		return ((T)getUserObject()).getFullName(); 
	}
}