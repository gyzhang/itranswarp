package com.itranswarp.bean;

public class MarkdownImageBean {
	int line;// 行号，供后续替换使用
	String url;// 图片地址
	String tip;// 图片提示信息
	String type;// 图片类型: jpeg｜bmp｜gif｜png
	String location;// 图片位置：本地local｜网络web
	String imageMark;// 更新过的图片标签
	long attachmentId;//图片导入系统后存储附件的id

	public int getLine() {
		return line;
	}

	public void setLine(int line) {
		this.line = line;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getTip() {
		return tip;
	}

	public void setTip(String tip) {
		this.tip = tip;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getImageMark() {
		return imageMark;
	}

	public void setImageMark(String imageMark) {
		this.imageMark = imageMark;
	}

	public long getAttachmentId() {
		return attachmentId;
	}

	public void setAttachmentId(long attachmentId) {
		this.attachmentId = attachmentId;
	}

}
