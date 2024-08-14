package io.onedev.server.web.page.simple.security;

import io.onedev.server.OneDev;
import io.onedev.server.entitymanager.SettingManager;
import io.onedev.server.entitymanager.UserManager;
import io.onedev.server.model.User;
import io.onedev.server.model.support.administration.BrandingSetting;
import io.onedev.server.model.support.administration.sso.SsoConnector;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.security.realm.PasswordAuthenticatingRealm;
import io.onedev.server.util.Translation;
import io.onedev.server.web.component.link.ViewStateAwarePageLink;
import io.onedev.server.web.component.user.twofactorauthentication.TwoFactorAuthenticationSetupPanel;
import io.onedev.server.web.page.simple.SimpleCssResourceReference;
import io.onedev.server.web.page.simple.SimplePage;
import org.apache.shiro.authc.*;
import org.apache.shiro.mgt.RememberMeManager;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.feedback.FencedFeedbackPanel;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.image.ExternalImage;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import static io.onedev.server.web.page.admin.ssosetting.SsoProcessPage.MOUNT_PATH;
import static io.onedev.server.web.page.admin.ssosetting.SsoProcessPage.STAGE_INITIATE;

@SuppressWarnings("serial")
public class LoginPage extends SimplePage {

	private String userName;
	
	private String password;
	
	private String passcode;
	
	private String recoveryCode;

	private boolean rememberMe;
	
	private String errorMessage;
	
	private String subTitle = Translation.get("Login_Sub_Title");
	
	public LoginPage(PageParameters params) {
		super(params);
		
		if (SecurityUtils.getAuthUser() != null)
			throw new RestartResponseException(getApplication().getHomePage());
	}
	
	public LoginPage(String errorMessage) {
		super(new PageParameters());
		this.errorMessage = errorMessage;
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		Fragment fragment = new Fragment("content", "passwordCheckFrag", this);
		
		Form<?> form = new Form<Void>("form") {

			@Override
			protected void onSubmit() {
				super.onSubmit();
				try {
					var token = new UsernamePasswordToken(userName, password, rememberMe);
					var user = (User) OneDev.getInstance(PasswordAuthenticatingRealm.class).getAuthenticationInfo(token);
					if (user.isEnforce2FA()) {
						if (user.getTwoFactorAuthentication() != null) {
							subTitle = Translation.get("Two_Factor_Auth_Enabled");
							newPasscodeVerifyFrag(user.getId());
						} else {
							subTitle = Translation.get("Setup_Two_Factor_Auth");
							newTwoFactorAuthenticationSetup(user.getId());
						}
					} else {
						afterLogin(user);
					}
				} catch (IncorrectCredentialsException|UnknownAccountException e) {
					error(Translation.get("Invalid_Credentials"));
				} catch (AuthenticationException ae) {
					error(ae.getMessage());
				}
			}
			
		};
		
		form.add(new FencedFeedbackPanel("feedback", form));
		
		if (errorMessage != null) 
			form.error(errorMessage);
		
		form.add(new TextField<>("userName", new IModel<String>() {

			@Override
			public void detach() {
			}

			@Override
			public String getObject() {
				return userName;
			}

			@Override
			public void setObject(String object) {
				userName = object;
			}

		}).setLabel(Model.of(Translation.get("User_Name"))).setRequired(true));
		
		form.add(new PasswordTextField("password", new IModel<String>() {

			@Override
			public void detach() {
			}

			@Override
			public String getObject() {
				return password;
			}

			@Override
			public void setObject(String object) {
				password = object;
			}
			
		}).setLabel(Model.of(Translation.get("Password"))).setRequired(true));
		
		form.add(new CheckBox("rememberMe", new IModel<Boolean>() {

			@Override
			public void detach() {
			}

			@Override
			public Boolean getObject() {
				return rememberMe;
			}

			@Override
			public void setObject(Boolean object) {
				rememberMe = object;
			}
			
		}));
		
		form.add(new ViewStateAwarePageLink<Void>("forgetPassword", PasswordResetPage.class) {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(OneDev.getInstance(SettingManager.class).getMailService() != null);
			}
			
		});
		
		fragment.add(form);
		
		SettingManager settingManager = OneDev.getInstance(SettingManager.class);
		
		boolean enableSelfRegister = settingManager.getSecuritySetting().isEnableSelfRegister();
		fragment.add(new ViewStateAwarePageLink<Void>("registerUser", SignUpPage.class).setVisible(enableSelfRegister));

		String serverUrl = settingManager.getSystemSetting().getServerUrl();
		
		RepeatingView ssoButtonsView = new RepeatingView("ssoButtons");
		for (SsoConnector connector: settingManager.getSsoConnectors()) {
			ExternalLink ssoButton = new ExternalLink(ssoButtonsView.newChildId(), 
					Model.of(serverUrl + "/" + MOUNT_PATH + "/" + STAGE_INITIATE + "/" + connector.getName()));
			ssoButton.add(new ExternalImage("image", connector.getButtonImageUrl()));
			ssoButton.add(new Label("label", "Login with " + connector.getName()));
			ssoButtonsView.add(ssoButton);
		}
		fragment.add(ssoButtonsView.setVisible(!settingManager.getSsoConnectors().isEmpty()));
		
		add(fragment);
		
		var brandingSetting = getSettingManager().getBrandingSetting();	
		if (brandingSetting.isOEM()) 
			add(new ExternalLink("vendor", brandingSetting.getUrl(), brandingSetting.getName()));
		else 
			add(new ExternalLink("vendor", BrandingSetting.DEFAULT_URL, BrandingSetting.DEFAULT_NAME));
	}
	
	private UserManager getUserManager() {
		return OneDev.getInstance(UserManager.class);
	}

	private SettingManager getSettingManager() {
		return OneDev.getInstance(SettingManager.class);
	}
	
	private void afterLogin(User user) {
		RememberMeManager rememberMeManager = OneDev.getInstance(RememberMeManager.class);
		if (rememberMe) {
			AuthenticationToken token = new UsernamePasswordToken(userName, password, rememberMe);
			rememberMeManager.onSuccessfulLogin(SecurityUtils.getSubject(), token, user);
		} else {
			SecurityUtils.getSubject().runAs(user.getPrincipals());
		}
		
		continueToOriginalDestination();
		throw new RestartResponseException(getApplication().getHomePage());
	}
	
	private void newTwoFactorAuthenticationSetup(Long userId) {
		replace(new TwoFactorAuthenticationSetupPanel("content") {
			
			@Override
			protected void onConfigured(AjaxRequestTarget target) {
				afterLogin(getUser());
			}
			
			@Override
			protected User getUser() {
				return getUserManager().load(userId);
			}
			
		});
	}

	private void newPasscodeVerifyFrag(Long userId) {
		Fragment fragment = new Fragment("content", "passcodeVerifyFrag", this);
		Form<?> form = new Form<Void>("form") {

			@Override
			protected void onSubmit() {
				super.onSubmit();
				User user = getUserManager().load(userId);
				if (user.getTwoFactorAuthentication().getTOTPCode().equals(passcode)) 
					afterLogin(user);
				else 
					error(Translation.get("Passcode_Verification_Failed"));
			}
			
		};
		form.add(new FencedFeedbackPanel("feedback", form));
		form.add(new TextField<String>("passcode", new IModel<String>() {

			@Override
			public void detach() {
			}

			@Override
			public String getObject() {
				return passcode;
			}

			@Override
			public void setObject(String object) {
				LoginPage.this.passcode = object;
			}
			
		}).setLabel(Model.of("Passcode")).setRequired(true));
		
		fragment.add(form);
		fragment.add(new Link<Void>("verifyRecoveryCode") {

			@Override
			public void onClick() {
				subTitle = Translation.get("Input_Recovery_Code");
				newRecoveryCodeVerifyFrag(userId);
			}
			
		});
		
		replace(fragment);
	}

	private void newRecoveryCodeVerifyFrag(Long userId) {
		Fragment fragment = new Fragment("content", "recoveryCodeVerifyFrag", this);
		Form<?> form = new Form<Void>("form") {

			@Override
			protected void onSubmit() {
				super.onSubmit();
				User user = getUserManager().load(userId);
				if (user.getTwoFactorAuthentication().getScratchCodes().remove(recoveryCode)) {
					getUserManager().update(user, null);
					afterLogin(user);
				} else {
					error(Translation.get("Recovery_Code_Verification_Failed"));
				}
			}
			
		};
		form.add(new FencedFeedbackPanel("feedback", form));
		form.add(new TextField<String>("recoveryCode", new IModel<String>() {

			@Override
			public void detach() {
			}

			@Override
			public String getObject() {
				return recoveryCode;
			}

			@Override
			public void setObject(String object) {
				LoginPage.this.recoveryCode = object;
			}
			
		}).setLabel(Model.of("Recovery code")).setRequired(true));
		
		fragment.add(form);
		replace(fragment);
	}
	
	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(CssHeaderItem.forReference(new SimpleCssResourceReference()));
	}

	@Override
	protected String getTitle() {
		return Translation.get("Sign_In_Indicator") + OneDev.getInstance(SettingManager.class).getBrandingSetting().getName();
	}

	@Override
	protected String getSubTitle() {
		return subTitle;
	}
	
}
