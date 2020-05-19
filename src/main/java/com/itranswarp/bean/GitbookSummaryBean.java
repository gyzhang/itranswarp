package com.itranswarp.bean;

/**
 * 用来记录Summary文件中的一行（一个页面对应一个文件，不支持页面内锚点，后续也许会扩展），需要记录前序兄弟节点和父节点
 * 每4个空格下降1级，如一级内容（如第1章）没有空格，二级内容（如1.1小节）4个空格，三级（如1.1.1小节）内容8个空格
 * 
 * @author kevin
 *
 */
public class GitbookSummaryBean {
	private String content;// 内容：“8个空格* [1.2.1 在路上](第01章 万事开头难/1.2.1onTheWay.md)”，3级内容
	private String title;// 显示用的标题：“1.2.1 在路上”
	private String markdownFile;// 文件地址：“第01章 万事开头难/1.2.1onTheWay.md”
	private int level;// 当前页面所处的级别：3级
	private int displayOrder;// 同层页面显示序号：0
	private long id;// 当前页面的编码，导入wiki page后，就是数据库内的编码
	private GitbookSummaryBean parent;// 当前这个目录文件的父文件，就是挂靠目录树用的

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getMarkdownFile() {
		return markdownFile;
	}

	public void setMarkdownFile(String markdownFile) {
		this.markdownFile = markdownFile;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public void setDisplayOrder(int displayOrder) {
		this.displayOrder = displayOrder;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public GitbookSummaryBean getParent() {
		return parent;
	}

	public void setParent(GitbookSummaryBean parent) {
		this.parent = parent;
	}
}
