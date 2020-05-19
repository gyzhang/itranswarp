package com.itranswarp.bean;

public class WikiImportBean extends AbstractRequestBean {
	public String gitbookPath;
	public long wikiId;
	public long publishAt;
	public String content = "New wiki page content";//默认wiki页面的内容
	
	@Override
	public void validate(boolean createMode) {
		
	}

}
