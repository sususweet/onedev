package io.onedev.server.buildspec.step.commandinterpreter;

import io.onedev.k8shelper.CommandFacade;
import io.onedev.server.annotation.Code;
import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.Interpolative;
import io.onedev.server.model.support.administration.jobexecutor.JobExecutor;

import javax.validation.constraints.NotEmpty;
import java.util.HashMap;

@Editable(order=100, name="Default (Shell on Linux, Batch on Windows)")
public class DefaultInterpreter extends Interpreter {

	private static final long serialVersionUID = 1L;

	@Editable(order=110, description="Specify shell commands (on Linux/Unix) or batch commands (on Windows) to execute "
			+ "under the <a href='https://docs.onedev.io/concepts#job-workspace' target='_blank'>job workspace</a>")
	@Interpolative
	@Code(language=Code.SHELL, variableProvider="suggestVariables")
	@NotEmpty
	@Override
	public String getCommands() {
		return super.getCommands();
	}

	@Override
	public void setCommands(String commands) {
		super.setCommands(commands);
	}

	@Override
	public CommandFacade getExecutable(JobExecutor jobExecutor, String jobToken, String image, 
									   String runAs, String builtInRegistryAccessToken, boolean useTTY) {
		return new CommandFacade(image, runAs, builtInRegistryAccessToken, getCommands(), 
				new HashMap<>(), useTTY);
	}
	
}
