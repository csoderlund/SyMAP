package symap3D;

import java.awt.event.ActionListener;
import java.util.TreeSet;

import symap.projectmanager.common.Project;
import symap.projectmanager.common.ProjectPanelCommon;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ProjectPanel3D extends ProjectPanelCommon 
{
	public ProjectPanel3D(Mapper3D mapper, Project project, ActionListener listener,
			TreeSet<Integer> grpIdxWithSynteny) {
		super(mapper, project, listener,grpIdxWithSynteny);		
	}
};
