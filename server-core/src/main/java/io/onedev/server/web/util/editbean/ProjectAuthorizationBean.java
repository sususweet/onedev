package io.onedev.server.web.util.editbean;

import java.io.Serializable;

import javax.validation.constraints.NotEmpty;

import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.ProjectChoice;
import io.onedev.server.annotation.RoleChoice;

@Editable
public class ProjectAuthorizationBean implements Serializable {

	private static final long serialVersionUID = 1L;

	private String projectPath;
	
	private String roleName;

	@Editable(order=100, name="Projects")
	@ProjectChoice
	@NotEmpty
	public String getProjectPath() {
		return projectPath;
	}

	public void setProjectPath(String projectPath) {
		this.projectPath = projectPath;
	}

	@Editable(order=200, name="Role")
	@RoleChoice
	@NotEmpty
	public String getRoleName() {
		return roleName;
	}

	public void setRoleName(String roleName) {
		this.roleName = roleName;
	}
	
}
