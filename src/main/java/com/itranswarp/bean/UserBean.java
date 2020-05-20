package com.itranswarp.bean;

public class UserBean extends AbstractRequestBean {

	public String email;
	public String name;
	public String password;
	public String imageUrl;

	@Override
	public void validate(boolean createMode) {
		this.name = checkName(this.name);
	}

}
